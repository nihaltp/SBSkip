package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFprobeKit
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.util.parser.SbskipFfprobeJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class SbskipMetadataReader
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
    ) {
        fun readSbskipMetadata(
            contentResolver: ContentResolver,
            fileUri: Uri,
            cacheDir: File,
        ): MediaFileMetadata {
            val fileName = fileUri.lastPathSegment ?: "unknown"

            val fileSize = fileSizeFromProvider(contentResolver, fileUri)
            val freeSpace = cacheDir.getFreeSpace()
            if (fileSize != null && fileSize > freeSpace) {
                AppLogger.metadata(
                    "SbskipMetadataReader: Not enough cache space to probe $fileName " +
                        "(file=$fileSize bytes, free=$freeSpace bytes)",
                )
                return MediaFileMetadata(
                    title = fileName,
                    extension = "unknown",
                    durationSeconds = null,
                    fileSize = fileSize,
                )
            }

            val tempFile =
                File(cacheDir, "sbskip_probe_${System.currentTimeMillis()}_${sanitizeFileName(fileName)}")

            return try {
                val inputStream = contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    AppLogger.metadata("SbskipMetadataReader: Failed to open input stream for $fileName")
                    return MediaFileMetadata(
                        title = fileName,
                        extension = "unknown",
                        durationSeconds = null,
                        fileSize = fileSize,
                    )
                }

                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                parseSbskipFile(tempFile, fileName, fileSize)
            } catch (e: Exception) {
                AppLogger.metadata("SbskipMetadataReader: Error reading metadata for $fileName: ${e.message}")
                MediaFileMetadata(
                    title = fileName,
                    extension = "unknown",
                    durationSeconds = null,
                    fileSize = fileSize,
                )
            } finally {
                tempFile.delete()
            }
        }

        private fun parseSbskipFile(
            tempFile: File,
            fileName: String,
            fileSize: Long?,
        ): MediaFileMetadata {
            val command = "-v quiet -print_format json -show_format \"${tempFile.absolutePath}\""
            AppLogger.metadata("SbskipMetadataReader: Probing $fileName with ffprobe")
            val session = FFprobeKit.execute(command)
            val output = session.output

            if (output == null || output.isBlank()) {
                AppLogger.metadata("SbskipMetadataReader: ffprobe returned empty output for $fileName")
                return MediaFileMetadata(
                    title = fileName,
                    extension = tempFile.extension.takeIf { it.isNotBlank() } ?: "unknown",
                    durationSeconds = null,
                    fileSize = fileSize,
                )
            }

            try {
                val result = SbskipFfprobeJson.json.decodeFromString(output)
                val tags = result.format?.tags ?: emptyMap()

                // Read SBSKIP_* tags directly (Picard-style named tags)
                val youtubeId = tags["SBSKIP_YOUTUBE_VIDEO_ID"]?.takeIf { it.isNotBlank() }
                val youtubeUrl = tags["SBSKIP_YOUTUBE_VIDEO_URL"]?.takeIf { it.isNotBlank() }
                val youtubeTitle = tags["SBSKIP_YOUTUBE_TITLE"]?.takeIf { it.isNotBlank() }
                val authorName = tags["SBSKIP_AUTHOR_NAME"]?.takeIf { it.isNotBlank() }
                val authorUrl = tags["SBSKIP_AUTHOR_URL"]?.takeIf { it.isNotBlank() }
                val thumbnailUrl = tags["SBSKIP_THUMBNAIL_URL"]?.takeIf { it.isNotBlank() }
                val sbskipSegments = tags["SBSKIP_SEGMENTS"]?.takeIf { it.isNotBlank() }
                val sbskipVersion = tags["SBSKIP_VERSION"]?.takeIf { it.isNotBlank() }
                val sbskipProcessed = tags["SBSKIP_PROCESSED"]?.takeIf { it.isNotBlank() }?.toBooleanOrNull()
                val sbskipProcessedAt = tags["SBSKIP_PROCESSED_AT"]?.takeIf { it.isNotBlank() }?.toLongOrNull()

                // Also read standard metadata fields
                val title = tags["title"]?.takeIf { it.isNotBlank() } ?: fileName

                val extension = tempFile.extension.takeIf { it.isNotBlank() } ?: "unknown"

                // Try to get duration from format
                val durationStr = result.format?.duration
                val durationSeconds = durationStr?.toDoubleOrNull()?.let { (it / 1000).toLong() }

                if (youtubeId != null) {
                    AppLogger.metadata(
                        "SbskipMetadataReader: Found SBSKIP_YOUTUBE_VIDEO_ID=$youtubeId in $fileName",
                    )
                }

                return MediaFileMetadata(
                    title = title,
                    extension = extension,
                    durationSeconds = durationSeconds,
                    youtubeId = youtubeId,
                    youtubeUrl = youtubeUrl,
                    youtubeTitle = youtubeTitle,
                    authorName = authorName,
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    sbskipSegments = sbskipSegments,
                    sbskipVersion = sbskipVersion,
                    sbskipProcessed = sbskipProcessed,
                    sbskipProcessedAt = sbskipProcessedAt,
                    fileSize = fileSize,
                )
            } catch (e: Exception) {
                AppLogger.metadata("SbskipMetadataReader: Failed to parse ffprobe output for $fileName: ${e.message}")
                return MediaFileMetadata(
                    title = fileName,
                    extension = tempFile.extension.takeIf { it.isNotBlank() } ?: "unknown",
                    durationSeconds = null,
                    fileSize = fileSize,
                )
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

        private fun sanitizeFileName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }
    }
