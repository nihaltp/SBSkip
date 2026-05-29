package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.BuildConfig
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

            // Check if the picked file duration matches the YouTube video duration
            val bypassCheck = item.url.contains("bypassDurationCheck=true")
            val youtubeDuration = com.nihaltp.sbskip.util.YouTubeDurationFetcher.fetchDuration(videoId)
            val fileDuration = localMetadata.durationSeconds ?: 0L

            if (bypassCheck) {
                AppLogger.worker("Duration mismatch verification bypassed via explicit user request.")
            } else if (youtubeDuration != null) {
                val difference = kotlin.math.abs(fileDuration - youtubeDuration)
                if (difference > 0) {
                    throw IllegalStateException("Picked file duration ($fileDuration s) does not match YouTube video duration ($youtubeDuration s)")
                }
            } else {
                AppLogger.worker("YouTube video duration could not be fetched for videoId=$videoId; skipping validation.")
            }

            // Derive thumbnail dynamically and update database metadata
            val thumbnailUrl = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
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
            notificationManager.showActive(notificationId, taskTitle, 20, applicationContext.getString(R.string.notification_copying_cache))

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
                    message = applicationContext.getString(R.string.notification_trimming_segments, percent),
                )
            }

            tempOutputFile = tagProcessedMedia(
                inputFile = tempOutputFile,
                videoId = videoId,
                youtubeUrl = item.url,
                youtubeTitle = taskTitle,
                categories = settings.sponsorBlockSettings.categories.map { it.name },
            )

            // 3. Save / overwrite media file
            notificationManager.showActive(notificationId, taskTitle, 95, applicationContext.getString(R.string.notification_saving_media))
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
            queueRepository.markCompleted(queueItemId, savedUriString)
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

    private fun tagProcessedMedia(
        inputFile: File,
        videoId: String,
        youtubeUrl: String,
        youtubeTitle: String,
        categories: List<String>,
    ): File {
        if (!inputFile.exists()) return inputFile

        val extension = inputFile.extension.lowercase()
        if (extension !in setOf("mp4", "m4a", "mp3", "m4v", "mov")) {
            return inputFile
        }

        val metadataJson = buildProcessedMetadataJson(
            videoId = videoId,
            youtubeUrl = youtubeUrl,
            youtubeTitle = youtubeTitle,
            categories = categories,
        )
        val taggedOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_tagged.${inputFile.extension}")
        val command = "-y -i \"${inputFile.absolutePath}\" -metadata comment=\"${escapeForFfmpeg(metadataJson)}\" -codec copy \"${taggedOutput.absolutePath}\""
        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        return if (ReturnCode.isSuccess(returnCode) && taggedOutput.exists()) {
            inputFile.delete()
            taggedOutput
        } else {
            AppLogger.worker("Metadata tagging skipped or failed for ${inputFile.name}: ${session.allLogsAsString}")
            taggedOutput.delete()
            inputFile
        }
    }

    private fun buildProcessedMetadataJson(
        videoId: String,
        youtubeUrl: String,
        youtubeTitle: String,
        categories: List<String>,
    ): String {
        val escapedTitle = youtubeTitle.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedUrl = youtubeUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedVideoId = videoId.replace("\\", "\\\\").replace("\"", "\\\"")
        val removedCategories = categories.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
        return "{" +
            "\"processed\":true," +
            "\"youtubeId\":\"$escapedVideoId\"," +
            "\"youtubeUrl\":\"$escapedUrl\"," +
            "\"youtubeTitle\":\"$escapedTitle\"," +
            "\"processedAt\":\"${System.currentTimeMillis()}\"," +
            "\"sbskipVersion\":\"${BuildConfig.VERSION_NAME}\"," +
            "\"removedCategories\":[$removedCategories]" +
            "}"
    }

    private fun escapeForFfmpeg(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    companion object {
        const val KEY_QUEUE_ITEM_ID = "queue_item_id"
        const val KEY_ERROR = "error"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_MESSAGE = "message"
    }
}
