package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SbskipFileMetadata(
    val youtubeId: String?,
    val youtubeUrl: String?,
    val youtubeTitle: String?,
    val authorName: String?,
    val fileSize: Long?,
)

@Serializable
private data class FfprobeFormat(val tags: Map<String, String>?)

@Serializable
private data class FfprobeResult(val format: FfprobeFormat?)

@Serializable
private data class CommentMetadata(
    val youtubeId: String?,
    val youtubeUrl: String?,
    val youtubeTitle: String?,
    val authorName: String?,
)

class FfmpegMetadataReader
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        fun readMetadata(
            contentResolver: ContentResolver,
            fileUri: Uri,
            context: android.content.Context,
        ): SbskipFileMetadata {
            val fileName = fileUri.lastPathSegment ?: "unknown"

            // Determine the file size before probing, so we can fail early when the cache
            // doesn't have enough free space instead of exhausting it mid-copy.
            val fileSize = fileSizeFromProvider(contentResolver, fileUri)

            // Probe in place via SAF file descriptor instead of copying the file into the cache,
            // so large files don't exhaust cache storage.
            val safParam = FFmpegKitConfig.getSafParameterForRead(context, fileUri)
            val command = "-v quiet -print_format json -show_format \"$safParam\""
            AppLogger.metadata("ImportFolder: Probing $fileName with ffprobe")
            val session = FFprobeKit.execute(command)
            return parseFfprobeOutput(session, fileName, fileSize)
        }

        private fun parseFfprobeOutput(
            session: com.arthenica.ffmpegkit.Session,
            fileName: String,
            fileSize: Long?,
        ): SbskipFileMetadata {
            val output = session.output
            if (output == null || output.isBlank()) {
                AppLogger.metadata("ImportFolder: ffprobe returned empty output for $fileName")
                return SbskipFileMetadata(null, null, null, null, fileSize)
            }

            try {
                val result = json.decodeFromString<FfprobeResult>(output)
                val tags = result.format?.tags ?: emptyMap()

                // First try direct YOUTUBE_VIDEO_ID tag
                var youtubeId = tags["YOUTUBE_VIDEO_ID"]?.takeIf { it.isNotBlank() }
                var youtubeUrl = tags["YOUTUBE_VIDEO_URL"]?.takeIf { it.isNotBlank() }
                var youtubeTitle = tags["title"]?.takeIf { it.isNotBlank() }
                var authorName = tags["author"]?.takeIf { it.isNotBlank() }

                // If not found, try parsing the comment JSON field (where MediaTagger writes it)
                if (youtubeId == null || authorName == null) {
                    val comment = tags["comment"]?.takeIf { it.isNotBlank() }
                    if (comment != null) {
                        val unescaped = comment.replace("\\\"", "\"")
                        try {
                            val commentData =
                                json.decodeFromString<CommentMetadata>(unescaped)
                            youtubeId = commentData.youtubeId?.takeIf { it.isNotBlank() } ?: youtubeId
                            youtubeUrl = commentData.youtubeUrl?.takeIf { it.isNotBlank() } ?: youtubeUrl
                            youtubeTitle = commentData.youtubeTitle?.takeIf { it.isNotBlank() } ?: youtubeTitle
                            authorName = commentData.authorName?.takeIf { it.isNotBlank() } ?: authorName
                            if (youtubeId == null) {
                                AppLogger.metadata(
                                    "ImportFolder: No youtubeId in comment JSON for $fileName",
                                )
                            } else {
                                AppLogger.metadata(
                                    "ImportFolder: Found YOUTUBE_VIDEO_ID=$youtubeId in $fileName (via comment)",
                                )
                            }
                        } catch (e: Exception) {
                            AppLogger.metadata(
                                "ImportFolder: Failed to parse comment JSON for $fileName: ${e.message}",
                            )
                        }
                    }
                }

                val cleanYoutubeId = youtubeId?.let { YouTubeUrlParser.cleanVideoId(it) }

                if (cleanYoutubeId == null) {
                    AppLogger.metadata(
                        "ImportFolder: No valid YOUTUBE_VIDEO_ID found in $fileName (raw: $youtubeId, tags: $tags)",
                    )
                } else {
                    AppLogger.metadata("ImportFolder: Found YOUTUBE_VIDEO_ID=$cleanYoutubeId in $fileName")
                }
                return SbskipFileMetadata(
                    youtubeId = cleanYoutubeId,
                    youtubeUrl = youtubeUrl,
                    youtubeTitle = youtubeTitle,
                    authorName = authorName,
                    fileSize = fileSize,
                )
            } catch (e: Exception) {
                AppLogger.metadata("ImportFolder: Failed to parse ffprobe output for $fileName: ${e.message}")
                return SbskipFileMetadata(null, null, null, null, fileSize)
            }
        }

        private fun fileSizeFromProvider(
            contentResolver: ContentResolver,
            fileUri: Uri,
        ): Long? {
            return try {
                contentResolver.query(
                    fileUri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            cursor.getLong(sizeIndex).takeIf { it >= 0 }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
