package com.nihaltp.sbskip.workers

import android.content.Context
import android.media.MediaMetadataRetriever
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
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.processing.SegmentProcessor
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
        private val sponsorBlockService: SponsorBlockService,
        private val mediaProcessor: MediaProcessor,
        private val downloadStorage: DownloadStorage,
        private val notificationManager: DownloadNotificationManager,
        private val workScheduler: DownloadWorkScheduler,
    ) : CoroutineWorker(appContext, params) {
        private val httpClient = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }

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

            var tempInputFile: File? = null
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

                // Query local file metadata
                notificationManager.showActive(notificationId, taskTitle, 10, applicationContext.getString(R.string.status_fetching_info))
                val localMetadata =
                    downloadStorage.queryMetadata(item.localFileUri)
                        ?: throw IOException("Failed to read local media file metadata")

                val fileDuration = localMetadata.durationSeconds ?: 0L
                val keepRanges = mutableListOf<Pair<Double, Double>>()

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

                if (item.url.isNotBlank() && !item.url.startsWith("sbskip://")) {
                    // Parse Video ID from YouTube URL
                    val videoId =
                        YouTubeUrlParser.extractVideoId(item.url)
                            ?: throw IllegalArgumentException(applicationContext.getString(R.string.enter_valid_url))

                    // Check if the picked file duration matches the YouTube video duration
                    val bypassCheck = item.url.contains("bypassDurationCheck=true")
                    val youtubeDuration = com.nihaltp.sbskip.util.YouTubeDurationFetcher.fetchDuration(videoId)

                    if (bypassCheck) {
                        AppLogger.worker("Duration mismatch verification bypassed via explicit user request.")
                    } else if (youtubeDuration != null) {
                        val difference = kotlin.math.abs(fileDuration - youtubeDuration)
                        val hasMismatch =
                            if (settings.bypassSmallDurationDifference) {
                                difference > settings.maxDurationDifferenceSeconds
                            } else {
                                difference > 0
                            }
                        if (hasMismatch) {
                            throw IllegalStateException(
                                "Picked file duration ($fileDuration s) does not match YouTube video duration ($youtubeDuration s)",
                            )
                        }
                    } else {
                        AppLogger.worker("YouTube video duration could not be fetched for videoId=$videoId; skipping validation.")
                    }

                    // Fetch YouTube oEmbed metadata if possible
                    try {
                        val metadata = fetchYouTubeOEmbed(item.url)
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
                    val segments = sponsorBlockService.fetchSegments(videoId, categories)
                    AppLogger.worker("Fetched ${segments.size} SponsorBlock segments for videoID=$videoId")

                    sbSkipSegments = segments.joinToString(";") { "${it.startSeconds}-${it.endSeconds}" }

                    val computedKeepRanges = SegmentProcessor.computeKeepRanges(segments, fileDuration.toDouble())
                    keepRanges.addAll(computedKeepRanges)
                    AppLogger.worker("Computed ${keepRanges.size} keep ranges from segments")
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

                // Setup cache paths
                val cacheDir = applicationContext.cacheDir
                tempInputFile = File(cacheDir, "clean_in_$queueItemId.${localMetadata.extension}")
                val outputExtension = if (item.convertVideoToAudio) "m4a" else localMetadata.extension
                tempOutputFile = File(cacheDir, "clean_out_$queueItemId.$outputExtension")

                // Copy imported SAF Content URI file locally to cache for native random-access FFmpeg copy seeking
                downloadStorage.copyUriToTempFile(item.localFileUri, tempInputFile)

                // Process media with FFmpeg
                mediaProcessor.processMedia(tempInputFile, tempOutputFile, keepRanges, item.convertVideoToAudio) { percent ->
                    // Progress mapped between 25% and 90%
                    val mappedProgress = 25 + (percent * 65 / 100)
                    notificationManager.showActive(
                        id = notificationId,
                        title = taskTitle,
                        progress = mappedProgress,
                        message = applicationContext.getString(R.string.notification_trimming_segments, percent),
                    )
                }

                if (item.url.isNotBlank()) {
                    val videoId = YouTubeUrlParser.extractVideoId(item.url)
                    if (videoId != null) {
                        tempOutputFile =
                            tagProcessedMedia(
                                inputFile = tempOutputFile,
                                videoId = videoId,
                                youtubeUrl = item.url,
                                youtubeTitle = oembedTitle ?: taskTitle,
                                authorName = oembedAuthorName,
                                authorUrl = oembedAuthorUrl,
                                thumbnailUrl = finalThumbnailUrl,
                                categories = categories.map { it.name },
                                sbSkipSegments = sbSkipSegments,
                            )
                    }
                }

                // 3. Save / overwrite media file
                notificationManager.showActive(
                    notificationId,
                    taskTitle,
                    95,
                    applicationContext.getString(R.string.notification_saving_media),
                )
                val savedUriString =
                    if (item.convertVideoToAudio) {
                        // Save to public storage as audio
                        val outputSuffix = if (item.url.contains("noSuffix=true")) "" else settings.autoCleanSuffix
                        val baseTitle =
                            if (taskTitle.endsWith(".${localMetadata.extension}", ignoreCase = true)) {
                                taskTitle.substring(0, taskTitle.length - localMetadata.extension.length - 1)
                            } else {
                                taskTitle
                            }
                        val forceOverwrite = settings.overwriteBehavior || item.url.contains("overwrite=true")
                        val savedUri =
                            downloadStorage.saveToPublicStorage(
                                tempFile = tempOutputFile,
                                title = "$baseTitle$outputSuffix",
                                extension = outputExtension,
                                mediaType = com.nihaltp.sbskip.model.MediaType.AUDIO,
                                customFolderUri = item.audioOutputDirUri,
                                overwrite = forceOverwrite,
                            )

                        // If deleteOriginalVideo is true, delete the original video file
                        if (item.deleteOriginalVideo) {
                            val deleted = downloadStorage.deleteUri(item.localFileUri)
                            AppLogger.worker("Deleted original video file: ${item.localFileUri} status=$deleted")
                        }

                        savedUri
                    } else if (settings.overwriteBehavior || item.url.contains("overwrite=true")) {
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
                        val outputSuffix = if (item.url.contains("noSuffix=true")) "" else settings.autoCleanSuffix
                        val baseTitle =
                            if (taskTitle.endsWith(".${localMetadata.extension}", ignoreCase = true)) {
                                taskTitle.substring(0, taskTitle.length - localMetadata.extension.length - 1)
                            } else {
                                taskTitle
                            }
                        downloadStorage.saveToPublicStorage(
                            tempFile = tempOutputFile,
                            title = "$baseTitle$outputSuffix",
                            extension = localMetadata.extension,
                            mediaType = item.mediaType,
                            customFolderUri =
                                if (item.mediaType == com.nihaltp.sbskip.model.MediaType.AUDIO) {
                                    item.audioOutputDirUri
                                } else {
                                    null
                                },
                            overwrite = false,
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
                // Clean up temporary files
                tempInputFile?.let { if (it.exists()) it.delete() }
                tempOutputFile?.let { if (it.exists()) it.delete() }
            }
        }

        private fun audioHasCoverImage(file: File): Boolean {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val picture = retriever.embeddedPicture
                picture != null
            } catch (e: Exception) {
                AppLogger.error("DownloadWorker", e, "Failed to check embedded picture for ${file.name}")
                false
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }
        }

        private suspend fun downloadThumbnail(url: String, cacheDir: File): File? =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            AppLogger.worker("Failed to download thumbnail: HTTP ${response.code}")
                            return@withContext null
                        }
                        val body = response.body ?: return@withContext null
                        val tempFile = File.createTempFile("thumb_", ".jpg", cacheDir)
                        tempFile.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }
                        tempFile
                    }
                } catch (e: Exception) {
                    AppLogger.error("DownloadWorker", e, "Failed to download thumbnail from $url")
                    null
                }
            }

        private suspend fun tagProcessedMedia(
            inputFile: File,
            videoId: String,
            youtubeUrl: String,
            youtubeTitle: String?,
            authorName: String?,
            authorUrl: String?,
            thumbnailUrl: String?,
            categories: List<String>,
            sbSkipSegments: String,
        ): File {
            if (!inputFile.exists()) return inputFile

            val extension = inputFile.extension.lowercase()
            if (extension !in setOf("mp4", "m4a", "mp3", "m4v", "mov")) {
                return inputFile
            }

            val metadataJson =
                buildProcessedMetadataJson(
                    videoId = videoId,
                    youtubeUrl = youtubeUrl,
                    youtubeTitle = youtubeTitle ?: "",
                    authorName = authorName,
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    categories = categories,
                    sbSkipSegments = sbSkipSegments,
                )
            val taggedOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_tagged.${inputFile.extension}")

            val isAudio = extension in setOf("m4a", "mp3")
            var thumbFile: File? = null
            if (isAudio && !audioHasCoverImage(inputFile)) {
                val thumbUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
                if (thumbUrl != null) {
                    AppLogger.worker("Audio cover image is missing; downloading thumbnail: $thumbUrl")
                    val cacheDir = inputFile.parentFile ?: applicationContext.cacheDir
                    thumbFile = downloadThumbnail(thumbUrl, cacheDir)
                }
            }

            val (existingTitle, existingAuthor) = getExistingMetadata(inputFile)
            val metadataArgs = mutableListOf<String>()

            if (existingTitle.isNullOrBlank() && !youtubeTitle.isNullOrBlank()) {
                metadataArgs.add("-metadata title=\"${escapeForFfmpeg(youtubeTitle)}\"")
            }

            if (existingAuthor.isNullOrBlank() && !authorName.isNullOrBlank()) {
                if (isAudio) {
                    metadataArgs.add("-metadata artist=\"${escapeForFfmpeg(authorName)}\"")
                } else {
                    metadataArgs.add("-metadata author=\"${escapeForFfmpeg(authorName)}\"")
                }
            }

            if (!authorUrl.isNullOrBlank()) {
                metadataArgs.add("-metadata author_url=\"${escapeForFfmpeg(authorUrl)}\"")
            }

            if (!thumbnailUrl.isNullOrBlank()) {
                metadataArgs.add("-metadata thumbnail_url=\"${escapeForFfmpeg(thumbnailUrl)}\"")
            }

            metadataArgs.add("-metadata SB_SKIP_SEGMENTS=\"${escapeForFfmpeg(sbSkipSegments)}\"")
            metadataArgs.add("-metadata SB_VERSION=\"${escapeForFfmpeg(BuildConfig.VERSION_NAME)}\"")
            metadataArgs.add("-metadata SB_PROCESSED=\"true\"")
            metadataArgs.add("-metadata YOUTUBE_VIDEO_URL=\"${escapeForFfmpeg(youtubeUrl)}\"")
            metadataArgs.add("-metadata YOUTUBE_VIDEO_ID=\"${escapeForFfmpeg(videoId)}\"")

            val metadataArgsStr = metadataArgs.joinToString(" ")
            val metadataArgsPart = if (metadataArgsStr.isNotEmpty()) " $metadataArgsStr" else ""

            val command = if (thumbFile != null && thumbFile.exists()) {
                if (extension == "mp3") {
                    "-y -i \"${inputFile.absolutePath}\" -i \"${thumbFile.absolutePath}\" -map 0 -map 1 -c copy -id3v2_version 3$metadataArgsPart -metadata comment=\"${escapeForFfmpeg(metadataJson)}\" -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\" \"${taggedOutput.absolutePath}\""
                } else { // m4a
                    "-y -i \"${inputFile.absolutePath}\" -i \"${thumbFile.absolutePath}\" -map 0 -map 1 -c copy -disposition:v:0 attached_pic$metadataArgsPart -metadata comment=\"${escapeForFfmpeg(metadataJson)}\" \"${taggedOutput.absolutePath}\""
                }
            } else {
                "-y -i \"${inputFile.absolutePath}\"$metadataArgsPart -metadata comment=\"${escapeForFfmpeg(
                    metadataJson,
                )}\" -codec copy \"${taggedOutput.absolutePath}\""
            }

            val session = try {
                FFmpegKit.execute(command)
            } finally {
                thumbFile?.let { if (it.exists()) it.delete() }
            }
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
            authorName: String?,
            authorUrl: String?,
            thumbnailUrl: String?,
            categories: List<String>,
            sbSkipSegments: String,
        ): String {
            val escapedTitle = youtubeTitle.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedUrl = youtubeUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedVideoId = videoId.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedAuthorName = authorName?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val escapedAuthorUrl = authorUrl?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val escapedThumbnailUrl = thumbnailUrl?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val removedCategories = categories.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
            return "{" +
                "\"processed\":true," +
                "\"youtubeId\":\"$escapedVideoId\"," +
                "\"youtubeUrl\":\"$escapedUrl\"," +
                "\"youtubeTitle\":\"$escapedTitle\"," +
                "\"authorName\":\"$escapedAuthorName\"," +
                "\"authorUrl\":\"$escapedAuthorUrl\"," +
                "\"thumbnailUrl\":\"$escapedThumbnailUrl\"," +
                "\"processedAt\":\"${System.currentTimeMillis()}\"," +
                "\"sbskipVersion\":\"${BuildConfig.VERSION_NAME}\"," +
                "\"removedCategories\":[$removedCategories]," +
                "\"sbSkipSegments\":\"${sbSkipSegments.replace("\\", "\\\\").replace("\"", "\\\"")}\"" +
                "}"
        }

        private fun escapeForFfmpeg(value: String): String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
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
                            sponsorBlockService.checkApiStatus() != "operational"
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

        private fun getExistingMetadata(file: File): Pair<String?, String?> {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                val authorName = artist?.takeIf { it.isNotBlank() } ?: author?.takeIf { it.isNotBlank() }
                Pair(title?.takeIf { it.isNotBlank() }, authorName)
            } catch (e: Exception) {
                AppLogger.error("DownloadWorker", e, "Failed to read metadata for ${file.name}")
                Pair(null, null)
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }
        }

        private suspend fun fetchYouTubeOEmbed(videoUrl: String): YouTubeMetadata =
            withContext(Dispatchers.IO) {
                val oEmbedUrl =
                    videoUrl.toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.scheme("https")
                        ?.host("www.youtube.com")
                        ?.encodedPath("/oembed")
                        ?.addQueryParameter("url", videoUrl)
                        ?.addQueryParameter("format", "json")
                        ?.build()
                        ?: throw IOException("Unable to parse video URL")

                val request = Request.Builder().url(oEmbedUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("oEmbed request failed: ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = json.decodeFromString(YouTubeOEmbedResponse.serializer(), body)
                    YouTubeMetadata(
                        title = parsed.title,
                        authorName = parsed.authorName,
                        authorUrl = parsed.authorUrl,
                        thumbnailUrl = parsed.thumbnailUrl,
                    )
                }
            }

        @Serializable
        private data class YouTubeOEmbedResponse(
            val title: String? = null,
            @SerialName("author_name") val authorName: String? = null,
            @SerialName("author_url") val authorUrl: String? = null,
            @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        )

        private data class YouTubeMetadata(
            val title: String?,
            val authorName: String?,
            val authorUrl: String?,
            val thumbnailUrl: String?,
        )

        companion object {
            const val KEY_QUEUE_ITEM_ID = "queue_item_id"
            const val KEY_ERROR = "error"
            const val KEY_OUTPUT_PATH = "output_path"
            const val KEY_MESSAGE = "message"
        }
    }
