package com.nihaltp.sbskip.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : DownloadStorage {

    override suspend fun deleteTemporaryFile(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                val deleted = file.delete()
                AppLogger.worker("Deleted temp file: $path status=$deleted")
            }
        } catch (e: Exception) {
            AppLogger.error("Storage", e, "Failed to delete temp file: $path")
        }
    }

    override suspend fun copyUriToTempFile(uriString: String, tempFile: File) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open input stream for URI: $uriString")
        AppLogger.worker("Successfully cached URI $uriString to temp file ${tempFile.absolutePath} size=${tempFile.length()}")
    }

    override suspend fun saveToPublicStorage(
        tempFile: File,
        title: String,
        extension: String,
        mediaType: MediaType,
    ): String = withContext(Dispatchers.IO) {
        val filename = "$title.$extension"
        val mimeType = if (mediaType == MediaType.VIDEO) "video/$extension" else "audio/$extension"

        val settings = settingsRepository.settings.first()
        val customFolder = if (mediaType == MediaType.VIDEO) {
            settings.videoFolder.trimEnd('/')
        } else {
            settings.audioFolder.trimEnd('/')
        }

        // 1. Direct SAF Custom Folder Tree URI Write (supports ALL custom folders, external directories, SD cards, etc.)
        val folderUriStr = if (mediaType == MediaType.VIDEO) settings.videoFolderUri else settings.audioFolderUri
        if (folderUriStr.isNotEmpty() && folderUriStr.startsWith("content://")) {
            try {
                val folderUri = Uri.parse(folderUriStr)
                val dirFile = DocumentFile.fromTreeUri(context, folderUri)
                if (dirFile != null && dirFile.exists() && dirFile.isDirectory) {
                    val existingFile = dirFile.findFile(filename)
                    if (existingFile != null && settings.overwriteBehavior) {
                        existingFile.delete()
                    }
                    val newFile = dirFile.createFile(mimeType, filename)
                        ?: throw IOException("Failed to create document file inside SAF directory")

                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open SAF output stream")

                    AppLogger.worker("Successfully saved clean file to custom SAF directory: $filename")
                    return@withContext newFile.uri.toString()
                }
            } catch (e: Exception) {
                AppLogger.error("Storage", e, "Failed to save via SAF to custom folder $folderUriStr, falling back to MediaStore")
            }
        }

        // 2. Failsafe Fallback: Standard MediaStore or Legacy storage
        val allowedAudioDirs = setOf("Music", "Podcasts", "Ringtones", "Alarms", "Notifications", "Audiobooks", "Recordings")
        val allowedVideoDirs = setOf("Movies", "Pictures", "DCIM")
        val audioDirsMap = allowedAudioDirs.associateBy { it.lowercase() }
        val videoDirsMap = allowedVideoDirs.associateBy { it.lowercase() }

        val firstSegment = customFolder.split('/').firstOrNull()?.trim() ?: ""
        val lowerSegment = firstSegment.lowercase()

        val finalFolder: String
        var useDownloadsUri = false

        if (mediaType == MediaType.VIDEO) {
            if (videoDirsMap.containsKey(lowerSegment)) {
                val normalizedFirst = videoDirsMap[lowerSegment]!!
                val restOfPath = customFolder.substringAfter('/', "")
                finalFolder = if (restOfPath.isNotEmpty()) "$normalizedFirst/$restOfPath" else normalizedFirst
            } else if (lowerSegment == "download" || lowerSegment == "downloads") {
                useDownloadsUri = true
                val restOfPath = customFolder.substringAfter('/', "")
                finalFolder = if (restOfPath.isNotEmpty()) "Download/$restOfPath" else "Download"
            } else {
                finalFolder = "Movies/SB Skip"
            }
        } else {
            if (audioDirsMap.containsKey(lowerSegment)) {
                val normalizedFirst = audioDirsMap[lowerSegment]!!
                val restOfPath = customFolder.substringAfter('/', "")
                finalFolder = if (restOfPath.isNotEmpty()) "$normalizedFirst/$restOfPath" else normalizedFirst
            } else if (lowerSegment == "download" || lowerSegment == "downloads") {
                useDownloadsUri = true
                val restOfPath = customFolder.substringAfter('/', "")
                finalFolder = if (restOfPath.isNotEmpty()) "Download/$restOfPath" else "Download"
            } else {
                finalFolder = "Music/SB Skip"
            }
        }

        val contentUri = if (useDownloadsUri) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else if (mediaType == MediaType.VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, finalFolder)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(contentUri, contentValues)
            ?: throw IOException("Failed to insert media entry into MediaStore")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Failed to open MediaStore output stream")
            AppLogger.worker("Saved clean file to MediaStore: $finalFolder/$filename")
            uri.toString()
        } catch (e: Exception) {
            // Cleanup inserted failed record
            resolver.delete(uri, null, null)
            throw e
        }
    }

    override suspend fun queryMetadata(uriString: String): MediaFileMetadata? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver

        var displayName = ""
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex).orEmpty()
                }
            }
        }

        if (displayName.isBlank()) {
            displayName = uri.lastPathSegment ?: "media"
        }

        // Separate name and extension
        val dotIndex = displayName.lastIndexOf('.')
        val (title, extension) = if (dotIndex != -1 && dotIndex < displayName.length - 1) {
            Pair(displayName.substring(0, dotIndex), displayName.substring(dotIndex + 1).lowercase())
        } else {
            Pair(displayName, "mp4") // Default to mp4 if extension not found
        }

        // Query duration using MediaMetadataRetriever
        var durationSeconds: Long? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs > 0) {
                durationSeconds = durationMs / 1000L
            }
        } catch (e: Exception) {
            AppLogger.error("Storage", e, "Failed to read media duration for URI: $uriString")
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        MediaFileMetadata(
            title = title,
            extension = extension,
            durationSeconds = durationSeconds,
        )
    }
}
