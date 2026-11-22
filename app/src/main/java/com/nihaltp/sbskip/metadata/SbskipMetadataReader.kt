package com.nihaltp.sbskip.metadata

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.util.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

class SbskipMetadataReader
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        fun readSbskipMetadata(
            context: Context,
            contentResolver: ContentResolver,
            fileUri: Uri,
        ): MediaFileMetadata? {
            val safParameter = FFmpegKitConfig.getSafParameterForRead(context, fileUri)
            return readSbskipMetadata(contentResolver, fileUri, safParameter)
        }

        fun readSbskipMetadata(
            contentResolver: ContentResolver,
            fileUri: Uri,
            safParameter: String,
        ): MediaFileMetadata? {
            val fileName = displayFileNameFromProvider(contentResolver, fileUri) ?: fileUri.lastPathSegment ?: "unknown"

            val fileSize = fileSizeFromProvider(contentResolver, fileUri)

            val command = "-v quiet -print_format json -show_format \"$safParameter\""
            AppLogger.metadata("SbskipMetadataReader: Probing $fileName with ffprobe")
            val session = FFprobeKit.execute(command)
            return parseSbskipFileWithFallbackInfo(session, fileName, fileSize)?.metadata
        }

        /**
         * Reads metadata and writes back SBSKIP tags if they were extracted from fallback tags.
         * This avoids re-extraction on subsequent reads.
         */
        suspend fun readAndWriteBackSbskipTags(
            context: Context,
            contentResolver: ContentResolver,
            fileUri: Uri,
        ): MediaFileMetadata? {
            val safParameter = FFmpegKitConfig.getSafParameterForRead(context, fileUri)
            val fileName = displayFileNameFromProvider(contentResolver, fileUri) ?: fileUri.lastPathSegment ?: "unknown"
            val fileSize = fileSizeFromProvider(contentResolver, fileUri)

            val command = "-v quiet -print_format json -show_format \"$safParameter\""
            AppLogger.metadata("SbskipMetadataReader: Probing $fileName with ffprobe")
            val session = FFprobeKit.execute(command)
            val metadata = parseSbskipFileWithFallbackInfo(session, fileName, fileSize)

            if (metadata != null && metadata.fallbackUsed) {
                // Write back the extracted SBSKIP tags
                writeSbskipTagsIfNeeded(context, contentResolver, fileUri, metadata.metadata, metadata.extension)
                    .also { success ->
                        if (success) {
                            AppLogger.metadata("SbskipMetadataReader: Write-back successful for $fileName")
                        }
                    }
            }

            return metadata?.metadata
        }

        private data class ParseResult(
            val metadata: MediaFileMetadata,
            val extension: String,
            val fallbackUsed: Boolean,
        )

        private fun parseSbskipFileWithFallbackInfo(
            session: com.arthenica.ffmpegkit.Session,
            fileName: String,
            fileSize: Long?,
        ): ParseResult? {
            val output = session.output

            if (output == null || output.isBlank()) {
                AppLogger.metadata("SbskipMetadataReader: ffprobe returned empty output for $fileName")
                return null
            }

            try {
                val jsonElement = json.decodeFromString<JsonElement>(output)
                val formatObj = (jsonElement as JsonObject)["format"] as? JsonObject
                val tagsObj = formatObj?.get("tags") as? JsonObject

                // Convert JsonObject to Map<String, String> for shared utility
                val tags =
                    tagsObj?.entries?.associate {
                        it.key to
                            (it.value as? JsonPrimitive)?.content?.takeIf {
                                it.isNotEmpty()
                            }
                    }?.filterValues { it != null }?.mapValues { it.value!! } ?: emptyMap()

                // Helper to read a string tag from the tags object
                fun readTag(key: String): String? {
                    return tagsObj?.get(key)?.let { element ->
                        (element as? JsonPrimitive)?.content
                    }?.takeIf { it.isNotEmpty() }
                }

                // Read SBSKIP_* tags directly (Picard-style named tags)
                val sbskipSegments = readTag("SBSKIP_SEGMENTS")
                val sbskipVersion = readTag("SBSKIP_VERSION")
                val sbskipProcessed = readTag("SBSKIP_PROCESSED")?.toBoolean()
                val sbskipProcessedAt = readTag("SBSKIP_PROCESSED_AT")?.toLongOrNull()
                val sbskipRemovedCategories = readTag("SBSKIP_REMOVED_CATEGORIES")

                val fallbackTitle = readTag("title") ?: fileName.substringBeforeLast('.', fileName)
                val extracted = YouTubeMetadataExtractor.extract(tags, fallbackTitle)

                var fallbackUsed = false
                var finalYoutubeId: String? = null
                var finalYoutubeUrl: String? = null
                var finalYoutubeTitle: String? = null
                var finalAuthorName: String? = null
                var finalAuthorUrl: String? = null
                var finalThumbnailUrl: String? = null

                if (extracted != null) {
                    finalYoutubeId = extracted.youtubeId
                    finalYoutubeUrl = extracted.youtubeUrl
                    finalYoutubeTitle = extracted.youtubeTitle
                    finalAuthorName = extracted.authorName
                    finalAuthorUrl = extracted.authorUrl
                    finalThumbnailUrl = extracted.thumbnailUrl
                    fallbackUsed = extracted.youtubeId != tags["SBSKIP_YOUTUBE_VIDEO_ID"]
                }

                // Also read standard metadata fields
                val title: String = fallbackTitle

                val extension: String = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.lowercase() ?: "unknown"

                // Try to get duration from format
                val durationStr: String? =
                    formatObj?.get("duration")?.let { element ->
                        (element as? JsonPrimitive)?.content
                    }
                val durationSeconds: Long? = durationStr?.toDoubleOrNull()?.toLong()

                if (finalYoutubeId != null) {
                    AppLogger.metadata(
                        "SbskipMetadataReader: Found YouTube ID=$finalYoutubeId in $fileName" +
                            (if (!fallbackUsed) " (via SBSKIP tag)" else " (via fallback tag)"),
                    )
                }

                val mediaFileMetadata =
                    MediaFileMetadata(
                        title = title,
                        extension = extension,
                        durationSeconds = durationSeconds,
                        youtubeId = finalYoutubeId,
                        youtubeUrl = finalYoutubeUrl,
                        youtubeTitle = finalYoutubeTitle,
                        authorName = finalAuthorName,
                        authorUrl = finalAuthorUrl,
                        thumbnailUrl = finalThumbnailUrl,
                        sbskipSegments = sbskipSegments,
                        sbskipVersion = sbskipVersion,
                        sbskipProcessed = sbskipProcessed,
                        sbskipProcessedAt = sbskipProcessedAt,
                        sbskipRemovedCategories = sbskipRemovedCategories,
                        fileSize = fileSize,
                    )

                return ParseResult(mediaFileMetadata, extension, fallbackUsed)
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

        private fun displayFileNameFromProvider(
            contentResolver: ContentResolver,
            fileUri: Uri,
        ): String? {
            return try {
                contentResolver.query(
                    fileUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex >= 0) {
                            cursor.getString(displayNameIndex)?.takeIf { it.isNotBlank() }
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

        /**
         * Writes SBSKIP_* tags to the file if they were extracted from fallback tags.
         * This avoids re-extraction on subsequent reads.
         */
        suspend fun writeSbskipTagsIfNeeded(
            context: Context,
            contentResolver: ContentResolver,
            fileUri: Uri,
            metadata: MediaFileMetadata,
            extension: String,
        ): Boolean {
            // Only write if we extracted from fallback (i.e., no existing SBSKIP tags)
            val hasExistingSbskipTags = metadata.sbskipProcessed == true || metadata.sbskipVersion != null
            if (hasExistingSbskipTags) {
                AppLogger.metadata("SbskipMetadataReader: SBSKIP tags already exist, skipping write-back")
                return false
            }

            val youtubeId = metadata.youtubeId ?: return false

            val extractedMetadata =
                YouTubeMetadataExtractor.ExtractedMetadata(
                    youtubeId = youtubeId,
                    youtubeUrl = metadata.youtubeUrl,
                    youtubeTitle = metadata.youtubeTitle,
                    authorName = metadata.authorName,
                    authorUrl = metadata.authorUrl,
                    thumbnailUrl = metadata.thumbnailUrl,
                )

            return MetadataWriter.writeSbskipTagsToUri(
                context = context,
                contentResolver = contentResolver,
                fileUri = fileUri,
                metadata = extractedMetadata,
                extension = extension,
                existingSbskipSegments = metadata.sbskipSegments,
                existingRemovedCategories = metadata.sbskipRemovedCategories,
            )
        }
    }
