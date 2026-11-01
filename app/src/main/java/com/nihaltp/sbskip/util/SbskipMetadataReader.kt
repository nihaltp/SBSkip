package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFprobeKit
import com.nihaltp.sbskip.model.MediaFileMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject

class SbskipMetadataReader
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        fun readSbskipMetadata(
            contentResolver: ContentResolver,
            fileUri: Uri,
            cacheDir: File,
        ): MediaFileMetadata? {
            val fileName = fileUri.lastPathSegment ?: "unknown"

            val fileSize = fileSizeFromProvider(contentResolver, fileUri)
            val freeSpace = cacheDir.getFreeSpace()
            if (fileSize != null && fileSize > freeSpace) {
                AppLogger.metadata(
                    "SbskipMetadataReader: Not enough cache space to probe $fileName " +
                        "(file=$fileSize bytes, free=$freeSpace bytes)",
                )
                return null
            }

            val tempFile =
                File(cacheDir, "sbskip_probe_${System.currentTimeMillis()}_${sanitizeFileName(fileName)}")

            return try {
                val inputStream = contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    AppLogger.metadata("SbskipMetadataReader: Failed to open input stream for $fileName")
                    return null
                }

                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                parseSbskipFile(tempFile, fileName, fileSize)
            } catch (e: Exception) {
                AppLogger.metadata("SbskipMetadataReader: Error reading metadata for $fileName: ${e.message}")
                null
            } finally {
                tempFile.delete()
            }
        }

        private fun parseSbskipFile(
            tempFile: File,
            fileName: String,
            fileSize: Long?,
        ): MediaFileMetadata? {
            val command = "-v quiet -print_format json -show_format \"${tempFile.absolutePath}\""
            AppLogger.metadata("SbskipMetadataReader: Probing $fileName with ffprobe")
            val session = FFprobeKit.execute(command)
            val output = session.output

            if (output == null || output.isBlank()) {
                AppLogger.metadata("SbskipMetadataReader: ffprobe returned empty output for $fileName")
                return null
            }

            try {
                val jsonElement = json.decodeFromString<JsonElement>(output)
                val formatObj = (jsonElement as JsonObject)["format"] as? JsonObject
                val tagsObj = formatObj?.get("tags") as? JsonObject

                // Helper to read a string tag from the tags object
                fun readTag(key: String): String? {
                    return tagsObj?.get(key)?.let { element ->
                        (element as? JsonPrimitive)?.content
                    }?.takeIf { it.isNotEmpty() }
                }

                // Read SBSKIP_* tags directly (Picard-style named tags)
                val youtubeId = readTag("SBSKIP_YOUTUBE_VIDEO_ID")
                val youtubeUrl = readTag("SBSKIP_YOUTUBE_VIDEO_URL")
                val youtubeTitle = readTag("SBSKIP_YOUTUBE_TITLE")
                val authorName = readTag("SBSKIP_AUTHOR_NAME")
                val authorUrl = readTag("SBSKIP_AUTHOR_URL")
                val thumbnailUrl = readTag("SBSKIP_THUMBNAIL_URL")
                val sbskipSegments = readTag("SBSKIP_SEGMENTS")
                val sbskipVersion = readTag("SBSKIP_VERSION")
                val sbskipProcessed = readTag("SBSKIP_PROCESSED")?.toBoolean()
                val sbskipProcessedAt = readTag("SBSKIP_PROCESSED_AT")?.toLong()

                // Also read standard metadata fields
                val title: String = readTag("title") ?: fileName

                val extension: String = tempFile.extension.takeIf { it.isNotEmpty() } ?: "unknown"

                // Try to get duration from format
                val durationStr: String? =
                    formatObj?.get("duration")?.let { element ->
                        (element as? JsonPrimitive)?.content
                    }
                val durationSeconds: Long? = durationStr?.toDoubleOrNull()?.toLong()

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
                return null
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
