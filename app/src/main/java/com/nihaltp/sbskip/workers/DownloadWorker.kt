package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import com.nihaltp.sbskip.workers.helpers.DurationValidator
import com.nihaltp.sbskip.workers.helpers.MediaProcessingManager
import com.nihaltp.sbskip.workers.helpers.MediaTagger
import com.nihaltp.sbskip.workers.helpers.MetadataFetcher
import com.nihaltp.sbskip.workers.helpers.OutputSaver
import com.nihaltp.sbskip.workers.helpers.ProcessingContext
import com.nihaltp.sbskip.workers.helpers.ProcessingPlanBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val queueRepository: QueueRepository,
        private val settingsRepository: SettingsRepository,
        private val notificationManager: DownloadNotificationManager,
        private val workScheduler: DownloadWorkScheduler,
        private val metadataFetcher: MetadataFetcher,
        private val durationValidator: DurationValidator,
        private val processingPlanBuilder: ProcessingPlanBuilder,
        private val mediaProcessingManager: MediaProcessingManager,
        private val mediaTagger: MediaTagger,
        private val outputSaver: OutputSaver,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val queueItemId = inputData.getLong(KEY_QUEUE_ITEM_ID, -1L)
            if (queueItemId < 0) {
                AppLogger.error("Worker", IllegalArgumentException("Missing queue item ID"), "Cleaner worker aborted")
                return Result.failure(workDataOf(KEY_ERROR to "Missing queue item ID"))
            }

            val item =
                queueRepository.findItemById(queueItemId)
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

            var tempOutputFile: File? = null

            return try {
                AppLogger.worker("Cleaner pipeline started for queueItemId=$queueItemId url=${item.url}")

                var oembedTitle: String? = null
                var oembedAuthorName: String? = null
                var oembedAuthorUrl: String? = null
                var finalThumbnailUrl: String? = null
                var sbSkipSegments = ""

                // 1. Mark as FETCHING_SEGMENTS
                queueRepository.markFetchingSegments(queueItemId)

                // Fetch settings
                val settings = settingsRepository.settings.first()

                // Query & validate local file metadata
                notificationManager.showActive(notificationId, taskTitle, 10, applicationContext.getString(R.string.status_fetching_info))

                val categories =
                    if (item.url.isNotBlank() && item.url.contains("categories=")) {
                        val startIndex = item.url.indexOf("categories=")
                        val categoriesSubStr = item.url.substring(startIndex)
                        val value = categoriesSubStr.substringBefore("&").substringAfter("=")
                        value.split(",")
                            .mapNotNull { name ->
                                runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
                            }
                            .toSet()
                    } else {
                        settings.sponsorBlockSettings.categories
                    }

                var videoId: String? = null
                var youtubeDuration: Long? = null
                var segments: List<com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment>? = null

                if (item.url.isNotBlank() && !item.url.startsWith("sbskip://")) {
                    videoId = YouTubeUrlParser.extractVideoId(item.url)
                        ?: throw IllegalArgumentException(applicationContext.getString(R.string.enter_valid_url))

                    youtubeDuration = metadataFetcher.fetchVideoDuration(videoId)
                }

                val bypassCheck = item.url.contains("bypassDurationCheck=true")
                val localMetadata = durationValidator.validate(item.localFileUri, youtubeDuration, bypassCheck)
                val fileDuration = localMetadata.durationSeconds ?: 0L

                if (videoId != null) {
                    // Fetch YouTube oEmbed metadata if possible
                    try {
                        val metadata = metadataFetcher.fetchYouTubeMetadata(item.url)
                        oembedTitle = metadata.title
                        oembedAuthorName = metadata.authorName
                        oembedAuthorUrl = metadata.authorUrl
                        finalThumbnailUrl = metadata.thumbnailUrl
                    } catch (e: Exception) {
                        AppLogger.error("DownloadWorker", e, "Failed to fetch oEmbed metadata for ${item.url}")
                    }

                    // Derive thumbnail dynamically and update database metadata
                    val thumbUrl = finalThumbnailUrl ?: "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
                    finalThumbnailUrl = thumbUrl
                    taskTitle = item.title.ifBlank { oembedTitle ?: localMetadata.title }
                    queueRepository.updateMetadata(queueItemId, taskTitle, thumbUrl, fileDuration)

                    // Fetch SponsorBlock skip segments
                    val fetchedSegments = metadataFetcher.fetchSponsorSegments(videoId, categories)
                    segments = fetchedSegments
                    AppLogger.worker("Fetched ${fetchedSegments.size} SponsorBlock segments for videoID=$videoId")

                    sbSkipSegments = fetchedSegments.joinToString(";") { "${it.startSeconds}-${it.endSeconds}" }
                } else {
                    taskTitle = item.title.ifBlank { localMetadata.title }
                    queueRepository.updateMetadata(queueItemId, taskTitle, null, fileDuration)
                    AppLogger.worker("No YouTube URL provided; skipping SponsorBlock segmentation checks.")
                }

                // 2. Mark as PROCESSING
                queueRepository.markProcessing(queueItemId)
                notificationManager.showActive(
                    notificationId,
                    taskTitle,
                    20,
                    applicationContext.getString(R.string.notification_copying_cache),
                )

                // Build processing plan
                val plan = processingPlanBuilder.createPlan(item, localMetadata.extension, fileDuration, segments)

                // Build Processing Context
                val context =
                    ProcessingContext(
                        queueItem = item,
                        localMetadata = localMetadata,
                        videoId = videoId,
                        oembedTitle = oembedTitle,
                        authorName = oembedAuthorName,
                        authorUrl = oembedAuthorUrl,
                        thumbnailUrl = finalThumbnailUrl,
                        sbSkipSegments = sbSkipSegments,
                        categories = categories,
                        segments = segments,
                        plan = plan,
                    )

                // Process media with FFmpeg
                tempOutputFile =
                    mediaProcessingManager.process(
                        queueItemId = queueItemId,
                        localFileUri = item.localFileUri,
                        localExtension = localMetadata.extension,
                        plan = plan,
                    ) { percent ->
                        // Progress mapped between 25% and 90%
                        val mappedProgress = 25 + (percent * 65 / 100)
                        notificationManager.showActive(
                            id = notificationId,
                            title = taskTitle,
                            progress = mappedProgress,
                            message = applicationContext.getString(R.string.notification_trimming_segments, percent),
                        )
                    }

                // Apply metadata tags & thumbnail
                tempOutputFile =
                    mediaTagger.applyMetadata(
                        inputFile = tempOutputFile,
                        processingContext = context,
                    )

                // 3. Save / overwrite media file
                notificationManager.showActive(
                    notificationId,
                    taskTitle,
                    95,
                    applicationContext.getString(R.string.notification_saving_media),
                )

                val savedUriString =
                    outputSaver.save(
                        tempOutputFile = tempOutputFile,
                        processingContext = context,
                    )

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
                val message =
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: applicationContext.getString(R.string.download_failed_generic)

                queueRepository.markFailed(queueItemId, message)
                notificationManager.showFailure(notificationId, taskTitle, message)

                if (checkIfSponsorBlockIsDown(throwable)) {
                    AppLogger.worker("SponsorBlock API detected down. Enqueuing status checker...")
                    workScheduler.scheduleSponsorBlockStatusCheck()
                }

                Result.failure(workDataOf(KEY_ERROR to message))
            } finally {
                // Clean up temporary output file
                tempOutputFile?.let { if (it.exists()) it.delete() }
            }
        }

        private suspend fun checkIfSponsorBlockIsDown(throwable: Throwable): Boolean {
            return try {
                val settings = settingsRepository.settings.first()
                val statusUrl = settings.sponsorBlockStatusUrl.trim()
                if (statusUrl.isNotBlank()) {
                    val isNetworkError =
                        throwable is java.io.IOException && (
                            throwable is java.net.UnknownHostException ||
                                throwable is java.net.SocketTimeoutException ||
                                throwable.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                throwable.message?.contains("timeout", ignoreCase = true) == true ||
                                throwable.message?.contains("timed out", ignoreCase = true) == true
                        )
                    if (isNetworkError) {
                        try {
                            metadataFetcher.checkApiStatus() != "operational"
                        } catch (e: Exception) {
                            // If we cannot even reach the status page, assume the network/API is down
                            true
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        companion object {
            const val KEY_QUEUE_ITEM_ID = "queue_item_id"
            const val KEY_ERROR = "error"
            const val KEY_OUTPUT_PATH = "output_path"
            const val KEY_MESSAGE = "message"
        }
    }
