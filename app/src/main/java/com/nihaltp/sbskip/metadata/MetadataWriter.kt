package com.nihaltp.sbskip.metadata

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared utility for writing metadata tags to media files via FFmpeg.
 * Supports both file paths and SAF content URIs.
 */
object MetadataWriter {
    /**
     * Writes SBSKIP_* tags to a file identified by a content URI (SAF/MediaStore).
     * Uses a temp-file-then-replace strategy to avoid corrupting the original.
     *
     * @param context Application context
     * @param contentResolver Content resolver for SAF operations
     * @param fileUri The content URI of the file to tag
     * @param metadata The metadata to write (extracted from YouTubeMetadataExtractor)
     * @param extension File extension (e.g., "mp4", "m4a")
     * @param existingSbskipSegments Existing SBSKIP_SEGMENTS tag to preserve (optional)
     * @param existingRemovedCategories Existing SBSKIP_REMOVED_CATEGORIES tag to preserve (optional)
     * @return true if write was successful
     */
    suspend fun writeSbskipTagsToUri(
        context: Context,
        contentResolver: ContentResolver,
        fileUri: Uri,
        metadata: YouTubeMetadataExtractor.ExtractedMetadata,
        extension: String,
        existingSbskipSegments: String? = null,
        existingRemovedCategories: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            // Only write if we have a YouTube ID
            val youtubeId = metadata.youtubeId ?: return@withContext false

            // Build FFmpeg metadata arguments
            val metadataArgs = mutableListOf<String>()

            fun addMetadata(
                key: String,
                value: String?,
            ) {
                value?.let { metadataArgs.add("-metadata $key=\"${escapeForFfmpeg(it)}\"") }
            }

            addMetadata("SBSKIP_YOUTUBE_VIDEO_ID", youtubeId)
            addMetadata("SBSKIP_YOUTUBE_VIDEO_URL", metadata.youtubeUrl)
            addMetadata("SBSKIP_YOUTUBE_TITLE", metadata.youtubeTitle)
            addMetadata("SBSKIP_AUTHOR_NAME", metadata.authorName)
            addMetadata("SBSKIP_AUTHOR_URL", metadata.authorUrl)
            addMetadata("SBSKIP_THUMBNAIL_URL", metadata.thumbnailUrl ?: Constants.buildYouTubeThumbnailUrl(youtubeId))
            addMetadata("SBSKIP_VERSION", BuildConfig.VERSION_NAME)
            addMetadata("SBSKIP_PROCESSED", "true")
            addMetadata("SBSKIP_PROCESSED_AT", System.currentTimeMillis().toString())
            // Preserve existing segments/categories if available
            addMetadata("SBSKIP_SEGMENTS", existingSbskipSegments)
            addMetadata("SBSKIP_REMOVED_CATEGORIES", existingRemovedCategories)

            val metadataArgsStr = metadataArgs.joinToString(" ")
            val metadataArgsPart = if (metadataArgsStr.isNotEmpty()) " $metadataArgsStr" else ""

            val safReadParam = FFmpegKitConfig.getSafParameterForRead(context, fileUri)
            val extensionLower = extension.lowercase()
            val isMovFamily = extensionLower in setOf("mp4", "m4a", "m4v", "mov")
            val movflagsArg = if (isMovFamily) "-movflags +use_metadata_tags" else ""

            // Create a temp file in the same directory/bucket
            val tempFileName = "${fileUri.lastPathSegment?.substringBeforeLast('.') ?: "temp"}_sbskip_tmp.$extensionLower"
            val tempUri =
                try {
                    val contentValues =
                        android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, tempFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(extensionLower))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, getRelativePath(fileUri, contentResolver))
                            }
                        }
                    contentResolver.insert(getContentUri(extensionLower), contentValues)
                } catch (e: Exception) {
                    AppLogger.error("MetadataWriter", e, "Failed to create temp file for write-back")
                    return@withContext false
                }

            tempUri?.let { tempUri ->
                try {
                    val safWriteParam = FFmpegKitConfig.getSafParameterForWrite(context, tempUri)
                    val command = "-y -i \"$safReadParam\"$metadataArgsPart $movflagsArg -codec copy \"$safWriteParam\""
                    val session = FFmpegKit.execute(command)
                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        // Replace original with temp file
                        contentResolver.delete(fileUri, null, null)
                        val updateValues =
                            android.content.ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileUri.lastPathSegment)
                            }
                        contentResolver.update(tempUri, updateValues, null, null)
                        AppLogger.metadata("MetadataWriter: Successfully wrote SBSKIP tags back to $fileUri")
                        return@withContext true
                    } else {
                        AppLogger.metadata("MetadataWriter: FFmpeg write-back failed: ${session.allLogsAsString}")
                        contentResolver.delete(tempUri, null, null)
                        return@withContext false
                    }
                } catch (e: Exception) {
                    AppLogger.error("MetadataWriter", e, "Error during SBSKIP tag write-back")
                    contentResolver.delete(tempUri, null, null)
                    return@withContext false
                }
            }

            false
        }

    /**
     * Writes SBSKIP_* tags to a local file path.
     * Uses a temp-file-then-replace strategy.
     *
     * @param inputFile The input file to tag
     * @param metadata The metadata to write
     * @param extension File extension
     * @param existingSbskipSegments Existing SBSKIP_SEGMENTS tag to preserve
     * @param existingRemovedCategories Existing SBSKIP_REMOVED_CATEGORIES tag to preserve
     * @return The tagged file (either the new temp file or original on failure)
     */
    suspend fun writeSbskipTagsToFile(
        inputFile: java.io.File,
        metadata: YouTubeMetadataExtractor.ExtractedMetadata,
        extension: String,
        existingSbskipSegments: String? = null,
        existingRemovedCategories: String? = null,
    ): java.io.File =
        withContext(Dispatchers.IO) {
            val youtubeId = metadata.youtubeId ?: return@withContext inputFile

            val metadataArgs = mutableListOf<String>()

            fun addMetadata(
                key: String,
                value: String?,
            ) {
                value?.let { metadataArgs.add("-metadata $key=\"${escapeForFfmpeg(it)}\"") }
            }

            addMetadata("SBSKIP_YOUTUBE_VIDEO_ID", youtubeId)
            addMetadata("SBSKIP_YOUTUBE_VIDEO_URL", metadata.youtubeUrl)
            addMetadata("SBSKIP_YOUTUBE_TITLE", metadata.youtubeTitle)
            addMetadata("SBSKIP_AUTHOR_NAME", metadata.authorName)
            addMetadata("SBSKIP_AUTHOR_URL", metadata.authorUrl)
            addMetadata("SBSKIP_THUMBNAIL_URL", metadata.thumbnailUrl ?: Constants.buildYouTubeThumbnailUrl(youtubeId))
            addMetadata("SBSKIP_VERSION", BuildConfig.VERSION_NAME)
            addMetadata("SBSKIP_PROCESSED", "true")
            addMetadata("SBSKIP_PROCESSED_AT", System.currentTimeMillis().toString())
            addMetadata("SBSKIP_SEGMENTS", existingSbskipSegments)
            addMetadata("SBSKIP_REMOVED_CATEGORIES", existingRemovedCategories)

            val metadataArgsStr = metadataArgs.joinToString(" ")
            val metadataArgsPart = if (metadataArgsStr.isNotEmpty()) " $metadataArgsStr" else ""

            val extensionLower = extension.lowercase()
            val isMovFamily = extensionLower in setOf("mp4", "m4a", "m4v", "mov")
            val movflagsArg = if (isMovFamily) "-movflags +use_metadata_tags" else ""

            val taggedOutput = java.io.File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_tagged.$extensionLower")

            val command = "-y -i \"${inputFile.absolutePath}\"$metadataArgsPart $movflagsArg -codec copy \"${taggedOutput.absolutePath}\""
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode) && taggedOutput.exists()) {
                inputFile.delete()
                AppLogger.metadata("MetadataWriter: Successfully wrote SBSKIP tags to ${taggedOutput.absolutePath}")
                return@withContext taggedOutput
            } else {
                AppLogger.metadata("MetadataWriter: FFmpeg write-back failed: ${session.allLogsAsString}")
                taggedOutput.delete()
                return@withContext inputFile
            }
        }

    private fun getMimeType(extension: String): String {
        return when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "opus" -> "audio/opus"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
    }

    private fun getContentUri(extension: String): Uri {
        return if (extension in setOf("mp3", "m4a", "opus", "aac")) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getRelativePath(
        uri: Uri,
        resolver: ContentResolver,
    ): String? {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun escapeForFfmpeg(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
