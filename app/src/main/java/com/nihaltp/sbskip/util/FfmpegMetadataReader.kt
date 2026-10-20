package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
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
    constructor(
        @ApplicationContext private val context: android.content.Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun readMetadata(
            contentResolver: ContentResolver,
            fileUri: Uri,
            cacheDir: File,
        ): SbskipFileMetadata {
            val fileName = fileUri.lastPathSegment ?: "unknown"
            val tempFile =
                File(cacheDir, "sbskip_probe_${System.currentTimeMillis()}_${sanitizeFileName(fileName)}")

            return try {
                val inputStream = contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    AppLogger.metadata("ImportFolder: Failed to open input stream for $fileName")
                    return SbskipFileMetadata(null, null, null, null, null)
                }

                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val command = "-v quiet -print_format json -show_format \"${tempFile.absolutePath}\""
                AppLogger.metadata("ImportFolder: Probing $fileName with ffprobe")
                val session = FFprobeKit.execute(command)

                val output = session.output
                if (output == null || output.isBlank()) {
                    AppLogger.metadata("ImportFolder: ffprobe returned empty output for $fileName")
                    return SbskipFileMetadata(null, null, null, null, tempFile.length())
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
                    // The comment field contains double-escaped JSON (e.g. \"youtubeId\"), so we unescape
                    // the backslash-quotes before parsing with kotlinx.serialization
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

                    // Sanitize the youtubeId read from the file tags: strip query parameters
                    // (e.g. "?overwrite=true" appended as an overwrite signal) and keep only a
                    // clean 11-character YouTube video ID. This stops corrupted IDs stored in the
                    // file metadata from being persisted to the database and used in thumbnail URLs.
                    val cleanYoutubeId = youtubeId?.let { YouTubeUrlParser.cleanVideoId(it) }

                    if (cleanYoutubeId == null) {
                        AppLogger.metadata(
                            "ImportFolder: No valid YOUTUBE_VIDEO_ID found in $fileName (raw: $youtubeId, tags: $tags)",
                        )
                    } else {
                        AppLogger.metadata("ImportFolder: Found YOUTUBE_VIDEO_ID=$cleanYoutubeId in $fileName")
                    }
                    SbskipFileMetadata(
                        youtubeId = cleanYoutubeId,
                        youtubeUrl = youtubeUrl,
                        youtubeTitle = youtubeTitle,
                        authorName = authorName,
                        fileSize = tempFile.length(),
                    )
                } catch (e: Exception) {
                    AppLogger.metadata("ImportFolder: Failed to parse ffprobe output for $fileName: ${e.message}")
                    SbskipFileMetadata(null, null, null, null, tempFile.length())
                }
            } catch (e: Exception) {
                AppLogger.metadata("ImportFolder: Error reading metadata for $fileName: ${e.message}")
                SbskipFileMetadata(null, null, null, null, null)
            } finally {
                tempFile.delete()
            }
        }

        private fun sanitizeFileName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }
    }
