package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.processing.SegmentProcessor
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val queueRepository: QueueRepository,
    private val settingsRepository: SettingsRepository,
    private val sponsorBlockService: SponsorBlockService,
    private val mediaProcessor: MediaProcessor,
    private val downloadStorage: DownloadStorage,
    private val notificationManager: DownloadNotificationManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val queueItemId = inputData.getLong(KEY_QUEUE_ITEM_ID, -1L)
        if (queueItemId < 0) {
            AppLogger.error("Worker", IllegalArgumentException("Missing queue item ID"), "Cleaner worker aborted")
            return Result.failure(workDataOf(KEY_ERROR to "Missing queue item ID"))
        }

        val item = queueRepository.findItemById(queueItemId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Queue item not found"))

        val notificationId = queueItemId
        var taskTitle = item.title.ifBlank { "Clean Media" }

        // Set up foreground notification
        setForeground(
            notificationManager.createForegroundInfo(
                id = notificationId,
                title = taskTitle,
                message = applicationContext.getString(R.string.status_fetching_info),
            ),
        )
        notificationManager.showActive(
            id = notificationId,
            title = taskTitle,
            progress = 0,
            message = applicationContext.getString(R.string.status_fetching_info),
        )

        var tempInputFile: File? = null
        var tempOutputFile: File? = null

        return try {
            AppLogger.worker("Cleaner pipeline started for queueItemId=$queueItemId url=${item.url}")

            // 1. Mark as FETCHING_SEGMENTS
            queueRepository.markFetchingSegments(queueItemId)

            // Parse Video ID from YouTube URL
            val videoId = YouTubeUrlParser.extractVideoId(item.url)
                ?: throw IllegalArgumentException(applicationContext.getString(R.string.enter_valid_url))

            // Fetch settings
            val settings = settingsRepository.settings.first()
            val categories = settings.sponsorBlockSettings.categories

            // Query local file metadata
            notificationManager.showActive(notificationId, taskTitle, 10, applicationContext.getString(R.string.status_fetching_info))
            val localMetadata = downloadStorage.queryMetadata(item.localFileUri)
                ?: throw IOException("Failed to read local media file metadata")

            // Derive thumbnail dynamically and update database metadata
            val thumbnailUrl = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
            val fileDuration = localMetadata.durationSeconds ?: 0L
            taskTitle = item.title.ifBlank { localMetadata.title }
            queueRepository.updateMetadata(queueItemId, taskTitle, thumbnailUrl, fileDuration)

            // Fetch SponsorBlock skip segments
            val segments = sponsorBlockService.fetchSegments(videoId, categories)
            AppLogger.worker("Fetched ${segments.size} SponsorBlock segments for videoID=$videoId")

            // If no segments found, we can still output a clean file (which is identical to source)
            val keepRanges = SegmentProcessor.computeKeepRanges(segments, fileDuration.toDouble())
            AppLogger.worker("Computed ${keepRanges.size} keep ranges from segments")

            // 2. Mark as PROCESSING
            queueRepository.markProcessing(queueItemId)
            notificationManager.showActive(notificationId, taskTitle, 20, "Copying media to cache...")

            // Setup cache paths
            val cacheDir = applicationContext.cacheDir
            tempInputFile = File(cacheDir, "clean_in_$queueItemId.${localMetadata.extension}")
            tempOutputFile = File(cacheDir, "clean_out_$queueItemId.${localMetadata.extension}")

            // Copy imported SAF Content URI file locally to cache for native random-access FFmpeg copy seeking
            downloadStorage.copyUriToTempFile(item.localFileUri, tempInputFile)

            // Process media with FFmpeg
            mediaProcessor.processMedia(tempInputFile, tempOutputFile, keepRanges) { percent ->
                // Progress mapped between 25% and 90%
                val mappedProgress = 25 + (percent * 65 / 100)
                notificationManager.showActive(
                    id = notificationId,
                    title = taskTitle,
                    progress = mappedProgress,
                    message = "Trimming SponsorBlock segments ($percent%)...",
                )
            }

            // 3. Save / overwrite media file
            notificationManager.showActive(notificationId, taskTitle, 95, "Saving cleaned media...")
            val savedUriString = if (settings.overwriteBehavior) {
                val resolver = applicationContext.contentResolver
                val targetUri = android.net.Uri.parse(item.localFileUri)
                resolver.openOutputStream(targetUri, "w")?.use { output ->
                    java.io.FileInputStream(tempOutputFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Failed to open output stream for source file to overwrite")
                AppLogger.worker("Successfully overwrote the original file where it was picked from: ${item.localFileUri}")
                item.localFileUri
            } else {
                val outputSuffix = settings.autoCleanSuffix
                downloadStorage.saveToPublicStorage(
                    tempFile = tempOutputFile,
                    title = "$taskTitle$outputSuffix",
                    extension = localMetadata.extension,
                    mediaType = item.mediaType,
                )
            }

            // 4. Mark as COMPLETED
            queueRepository.markCompleted(queueItemId)
            notificationManager.showCompletion(notificationId, taskTitle)

            Result.success(
                workDataOf(
                    KEY_OUTPUT_PATH to savedUriString,
                    KEY_MESSAGE to applicationContext.getString(R.string.download_completed),
                ),
            )
        } catch (throwable: Throwable) {
            AppLogger.error("Worker", throwable, "Cleaner pipeline failed for queueItemId=$queueItemId")
            val message = throwable.message?.takeIf { it.isNotBlank() }
                ?: applicationContext.getString(R.string.download_failed_generic)

            queueRepository.markFailed(queueItemId, message)
            notificationManager.showFailure(notificationId, taskTitle, message)

            Result.failure(workDataOf(KEY_ERROR to message))
        } finally {
            // Clean up temporary files
            tempInputFile?.let { if (it.exists()) it.delete() }
            tempOutputFile?.let { if (it.exists()) it.delete() }
        }
    }

    companion object {
        const val KEY_QUEUE_ITEM_ID = "queue_item_id"
        const val KEY_ERROR = "error"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_MESSAGE = "message"
    }
}
