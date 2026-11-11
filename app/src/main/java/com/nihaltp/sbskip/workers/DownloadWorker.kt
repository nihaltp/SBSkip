package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DownloadSource
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.ProcessingContext
import com.nihaltp.sbskip.model.SponsorBlockSegment
import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.NetworkErrorClassifier
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import com.nihaltp.sbskip.util.ytdlp.YtDlpCommandBuilder
import com.nihaltp.sbskip.util.ytdlp.YtDlpProcessRunner
import com.nihaltp.sbskip.util.ytdlp.YtDlpRuntime
import com.nihaltp.sbskip.workers.helpers.DurationValidator
import com.nihaltp.sbskip.workers.helpers.MediaProcessingManager
import com.nihaltp.sbskip.workers.helpers.MediaTagger
import com.nihaltp.sbskip.workers.helpers.MetadataFetcher
import com.nihaltp.sbskip.workers.helpers.OutputSaver
import com.nihaltp.sbskip.workers.helpers.ProcessingPlanBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

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
        private val ytDlpRuntime: YtDlpRuntime,
        private val ytDlpCommandBuilder: YtDlpCommandBuilder,
        private val ytDlpProcessRunner: YtDlpProcessRunner,
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
            try {
                setForeground(
                    notificationManager.createForegroundInfo(
                        id = notificationId,
                        title = taskTitle,
                        message = applicationContext.getString(R.string.status_fetching_info),
                    ),
                )
            } catch (e: Exception) {
                AppLogger.worker("Worker is in background, cannot set foreground service: ${e.message}")
            }
            notificationManager.showActive(
                id = notificationId,
                title = taskTitle,
                progress = 0,
                message = applicationContext.getString(R.string.status_fetching_info),
            )

            var tempOutputFile: File? = null
            var isYtDlp = false
            var downloadedFile: File? = null

            return try {
                AppLogger.worker("Cleaner pipeline started for queueItemId=$queueItemId url=${item.url}")

                var oembedTitle: String? = null
                var oembedAuthorName: String? = null
                var oembedAuthorUrl: String? = null
                var finalThumbnailUrl: String? = null
                var sbSkipSegments = ""
                var ytMetadataRef: YouTubeMetadata? = null

                // 1. Mark as FETCHING_SEGMENTS
                queueRepository.markFetchingSegments(queueItemId)

                // Fetch settings
                val settings = settingsRepository.settings.first()
                isYtDlp = item.downloadSource == DownloadSource.YTDLP

                // Query & validate local file metadata
                notificationManager.showActive(notificationId, taskTitle, 10, applicationContext.getString(R.string.status_fetching_info))

                // Use custom categories from the queue item if available, otherwise fall back to global settings
                val categories =
                    if (item.customSponsorBlockCategories != null && item.customSponsorBlockCategories.isNotEmpty()) {
                        item.customSponsorBlockCategories
                    } else {
                        settings.sponsorBlockSettings.categories
                    }

                var videoId: String? = null
                var youtubeDuration: Long? = null
                var segments: List<SponsorBlockSegment>? = null

                if (item.url.isNotBlank() && !item.url.startsWith("sbskip://")) {
                    videoId = YouTubeUrlParser.extractVideoId(item.url)
                        ?: throw IllegalArgumentException(applicationContext.getString(R.string.enter_valid_url))

                    youtubeDuration = metadataFetcher.fetchVideoDuration(videoId)
                }

                val bypassCheck = item.bypassDurationCheck
                var localMetadata: MediaFileMetadata
                var fileDuration: Long
                if (isYtDlp) {
                    // yt-dlp download: no local file to validate yet; duration comes from YouTube API
                    fileDuration = youtubeDuration ?: 0L
                    localMetadata =
                        MediaFileMetadata(
                            title = item.title,
                            extension =
                                when (item.mediaType) {
                                    MediaType.VIDEO -> "mp4"
                                    MediaType.AUDIO -> "m4a"
                                },
                            durationSeconds = youtubeDuration,
                        )
                } else {
                    localMetadata = durationValidator.validate(item.localFileUri, youtubeDuration, bypassCheck)
                    fileDuration = localMetadata.durationSeconds ?: 0L
                }

                if (videoId != null) {
                    // Fetch YouTube oEmbed metadata if possible
                    try {
                        val metadata = metadataFetcher.fetchYouTubeMetadata(item.url)
                        ytMetadataRef = metadata
                        oembedTitle = metadata.title
                        oembedAuthorName = metadata.authorName
                        oembedAuthorUrl = metadata.authorUrl
                        finalThumbnailUrl = metadata.thumbnailUrl
                    } catch (e: Exception) {
                        AppLogger.error("DownloadWorker", e, "Failed to fetch oEmbed metadata for ${item.url}")
                    }

                    // Derive thumbnail dynamically and update database metadata
                    val thumbUrl = finalThumbnailUrl ?: Constants.buildYouTubeThumbnailUrl(videoId)
                    finalThumbnailUrl = thumbUrl
                    taskTitle = item.title.ifBlank { oembedTitle ?: localMetadata.title }
                    queueRepository.updateMetadata(queueItemId, taskTitle, thumbUrl, fileDuration)

                    // Fetch SponsorBlock skip segments (skip for yt-dlp: native removal handles it)
                    if (!isYtDlp) {
                        val fetchedSegments = metadataFetcher.fetchSponsorSegments(videoId, categories)
                        segments = fetchedSegments
                        AppLogger.worker("Fetched ${fetchedSegments.size} SponsorBlock segments for videoID=$videoId")

                        sbSkipSegments = fetchedSegments.joinToString(";") { "${it.startSeconds}-${it.endSeconds}" }
                    } else {
                        segments = emptyList()
                        sbSkipSegments = ""
                        AppLogger.worker("Skipping SponsorBlock API fetch for yt-dlp: native removal enabled")
                    }
                } else {
                    taskTitle = item.title.ifBlank { localMetadata.title }
                    queueRepository.updateMetadata(queueItemId, taskTitle, null, fileDuration)
                    AppLogger.worker("No YouTube URL provided; skipping SponsorBlock segmentation checks.")
                }

                // For yt-dlp downloads, fetch the media from YouTube before processing.
                var inputFileUri = item.localFileUri
                var downloadedExtension: String? = null
                if (isYtDlp) {
                    queueRepository.markDownloading(queueItemId)
                    notificationManager.showActive(
                        notificationId,
                        taskTitle,
                        20,
                        applicationContext.getString(R.string.status_downloading),
                    )
                    downloadedFile =
                        downloadViaYtDlp(
                            item = item,
                            settings = settings,
                            sponsorBlockCategories = categories.map { it.name }.toSet(),
                            youtubeVideoId = videoId,
                            youtubeVideoUrl = Constants.buildYouTubeWatchUrl(videoId ?: ""),
                            youtubeTitle = oembedTitle,
                            authorName = oembedAuthorName,
                            authorUrl = oembedAuthorUrl,
                            thumbnailUrl = finalThumbnailUrl,
                            sbskipSegments = sbSkipSegments,
                            removedCategories = categories.map { it.name }.toSet(),
                            convertToAudio = item.convertVideoToAudio && item.mediaType == MediaType.VIDEO,
                            onProgress = { percent ->
                                val mappedProgress = (20 + percent.toInt() * 10 / 100).coerceIn(20, 30)
                                notificationManager.showActive(
                                    id = notificationId,
                                    title = taskTitle,
                                    progress = mappedProgress,
                                    message = applicationContext.getString(R.string.status_downloading),
                                )
                            },
                        )
                    inputFileUri = downloadedFile!!.absolutePath
                    downloadedExtension = downloadedFile.extension.ifBlank { null }
                    AppLogger.worker("yt-dlp download complete: ${downloadedFile.absolutePath}")

                    // Probe the downloaded file for duration if YouTube API didn't provide one
                    if (fileDuration <= 0L) {
                        val probedDuration = probeFileDuration(downloadedFile.absolutePath)
                        if (probedDuration > 0L) {
                            fileDuration = probedDuration
                            localMetadata = localMetadata.copy(durationSeconds = fileDuration)
                            AppLogger.worker("Probed duration from downloaded file: ${fileDuration}s")
                        }
                    }
                }
                if (isYtDlp && downloadedExtension != null) {
                    localMetadata = localMetadata.copy(extension = downloadedExtension)
                }

                // 2. Mark as PROCESSING
                queueRepository.markProcessing(queueItemId)
                notificationManager.showActive(
                    notificationId,
                    taskTitle,
                    30,
                    applicationContext.getString(R.string.notification_trimming_segments, 0),
                )

                // Build processing plan
                val plan = processingPlanBuilder.createPlan(item, localMetadata.extension, fileDuration, segments)

                // Build Processing Context
                // For yt-dlp there is no local original file, so never delete one.
                val queueItemForContext = if (isYtDlp) item.copy(deleteOriginalVideo = false) else item
                val context =
                    ProcessingContext(
                        queueItem = queueItemForContext,
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
                        musicMetadata = ytMetadataRef?.musicMetadata,
                        musicConfidence = ytMetadataRef?.musicConfidence ?: 0f,
                    )

                // Process media with FFmpeg (skip for yt-dlp: native sponsorblock removal handles trimming)
                tempOutputFile =
                    if (isYtDlp) {
                        downloadedFile!!
                    } else {
                        mediaProcessingManager.process(
                            queueItemId = queueItemId,
                            localFileUri = inputFileUri,
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
                    }

                // Apply metadata tags & thumbnail (skip for yt-dlp: yt-dlp handles metadata natively)
                tempOutputFile =
                    if (isYtDlp) {
                        tempOutputFile
                    } else {
                        mediaTagger.applyMetadata(
                            inputFile = tempOutputFile,
                            processingContext = context,
                        )
                    }

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

                // Calculate output duration
                val outputDurationSeconds =
                    if (plan.keepRanges.isNotEmpty()) {
                        plan.keepRanges.sumOf { (start, end) -> end - start }.toLong()
                    } else {
                        fileDuration
                    }

                // 4. Mark as COMPLETED
                queueRepository.markCompleted(queueItemId, savedUriString, outputDurationSeconds)
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

                if (NetworkErrorClassifier.isRetryableNetworkError(throwable) && runAttemptCount < MAX_NETWORK_RETRY_ATTEMPTS - 1) {
                    AppLogger.worker(
                        "Retrying network operation for queueItemId=$queueItemId " +
                            "attempt=${runAttemptCount + 1}/$MAX_NETWORK_RETRY_ATTEMPTS",
                    )
                    notificationManager.showActive(
                        notificationId,
                        taskTitle,
                        10,
                        applicationContext.getString(R.string.status_fetching_info),
                    )
                    return Result.retry()
                }

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
                // Clean up yt-dlp work directory to prevent cache buildup
                if (isYtDlp) {
                    val workDir = ytDlpRuntime.workDir(applicationContext.cacheDir, item.id)
                    if (workDir.exists()) {
                        workDir.deleteRecursively()
                    }
                }
                // Clean up downloaded source file for yt-dlp (worker downloads to work dir)
                if (isYtDlp && downloadedFile?.exists() == true) {
                    downloadedFile.delete()
                }
            }
        }

        /** Downloads media via yt-dlp into a per-item work dir and returns the output file. */
        private suspend fun downloadViaYtDlp(
            item: com.nihaltp.sbskip.model.DownloadQueueItem,
            settings: com.nihaltp.sbskip.model.AppSettings,
            onProgress: (Float) -> Unit,
            sponsorBlockCategories: Set<String>,
            youtubeVideoId: String?,
            youtubeVideoUrl: String?,
            youtubeTitle: String?,
            authorName: String?,
            authorUrl: String?,
            thumbnailUrl: String?,
            sbskipSegments: String,
            removedCategories: Set<String>,
            convertToAudio: Boolean,
        ): File {
            val paths = ytDlpRuntime.ensure()
            val workDir = ytDlpRuntime.workDir(applicationContext.cacheDir, item.id)
            workDir.mkdirs()

            val request =
                YtDlpCommandBuilder.Request(
                    mediaType = item.mediaType,
                    url = item.url,
                    workDir = workDir,
                    maxQuality = settings.nativeQuality,
                    ffmpegLocation = paths.ffmpegLauncher,
                    sponsorBlockCategories = sponsorBlockCategories,
                    convertToAudio = convertToAudio,
                    youtubeVideoId = youtubeVideoId,
                    youtubeVideoUrl = youtubeVideoUrl,
                    youtubeTitle = youtubeTitle,
                    authorName = authorName,
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    sbskipSegments = sbskipSegments,
                    removedCategories = removedCategories,
                )
            val command =
                listOf(paths.pythonLauncher.absolutePath, paths.zipapp.absolutePath) +
                    ytDlpCommandBuilder.build(request)
            val environment = ytDlpRuntime.environment(paths, workDir)

            val result =
                downloadMutex.withLock {
                    ytDlpProcessRunner.run(command, environment) { progress ->
                        onProgress(progress.percent ?: 0f)
                    }
                }

            if (result.exitCode != 0) {
                throw IOException(
                    applicationContext.getString(R.string.download_failed_yt_dlp) +
                        ": exit=${result.exitCode} stderr=${result.stderrTail}",
                )
            }

            val outputExt =
                when (item.mediaType) {
                    MediaType.VIDEO -> "mp4"
                    MediaType.AUDIO -> "m4a"
                }
            val downloadedFile =
                workDir.listFiles()?.firstOrNull { it.name.startsWith("out.") && it.isFile }
                    ?: File(workDir, "out.$outputExt")
            if (!downloadedFile.isFile) {
                throw IOException(
                    "yt-dlp completed but output file not found: ${downloadedFile.absolutePath}",
                )
            }
            return downloadedFile
        }

        /** Uses ffprobe via ffmpeg-kit to probe the duration of a media file. */
        private suspend fun probeFileDuration(filePath: String): Long {
            return try {
                // Skip probing for empty or very small files (likely test dummies)
                val file = File(filePath)
                if (!file.exists() || file.length() < 1024) {
                    return 0L
                }
                val command = "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"$filePath\""
                val session = FFmpegKit.execute(command)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val output = session.output.trim()
                    if (output.isNotBlank()) {
                        output.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
                    } else {
                        0L
                    }
                } else {
                    AppLogger.error(
                        "DownloadWorker",
                        RuntimeException("ffprobe failed"),
                        "ffprobe failed for $filePath: exit=${session.returnCode}, output=${session.output}",
                    )
                    0L
                }
            } catch (e: Exception) {
                AppLogger.error("DownloadWorker", e, "Failed to probe file duration for $filePath")
                0L
            }
        }

        private suspend fun checkIfSponsorBlockIsDown(throwable: Throwable): Boolean {
            return try {
                val settings = settingsRepository.settings.first()
                val statusUrl = settings.sponsorBlockStatusUrl.trim()
                if (statusUrl.isNotBlank()) {
                    val isNetworkError = NetworkErrorClassifier.isRetryableNetworkError(throwable)
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
            private const val MAX_NETWORK_RETRY_ATTEMPTS = 3

            /**
             * Serializes yt-dlp downloads so only one Python VM runs at a time.
             * Without this, WorkManager launches all queue-item downloads concurrently,
             * each spawning its own yt-dlp (Python) process. On low-RAM devices the
             * combined memory pressure triggers the Low Memory Killer (SIGKILL, exit=137),
             * failing downloads and making the UI go white.
             */
            private val downloadMutex = Mutex()
        }
    }
