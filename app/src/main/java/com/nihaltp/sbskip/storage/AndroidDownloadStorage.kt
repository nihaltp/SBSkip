package com.nihaltp.sbskip.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.FilenameSanitizer
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

data class StoragePaths(
    val effectiveRelativePath: String?,
    val mediaStoreFolder: String,
    val useDownloadsUri: Boolean,
)

internal fun computeStoragePaths(
    mediaType: MediaType,
    customFolderUri: String?,
    resolvedCustomFolderName: String?,
    videoFolderSetting: String,
    audioFolderSetting: String,
    relativePath: String?,
): StoragePaths {
    val customFolder =
        if (!customFolderUri.isNullOrEmpty()) {
            resolvedCustomFolderName?.trimEnd('/') ?: ""
        } else if (mediaType == MediaType.VIDEO) {
            videoFolderSetting.trimEnd('/')
        } else {
            audioFolderSetting.trimEnd('/')
        }

    var effectiveRelativePath = relativePath
    if (!effectiveRelativePath.isNullOrEmpty() && customFolder.isNotEmpty()) {
        if (effectiveRelativePath.startsWith("$customFolder/")) {
            effectiveRelativePath = effectiveRelativePath.substringAfter("$customFolder/")
        } else if (effectiveRelativePath == customFolder) {
            effectiveRelativePath = ""
        }
    }

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

    val trueFinalFolder =
        if (!effectiveRelativePath.isNullOrEmpty()) {
            "$finalFolder/$effectiveRelativePath"
        } else {
            finalFolder
        }

    return StoragePaths(
        effectiveRelativePath = effectiveRelativePath,
        mediaStoreFolder = trueFinalFolder,
        useDownloadsUri = useDownloadsUri,
    )
}

@Singleton
class AndroidDownloadStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) : DownloadStorage {
        override suspend fun deleteTemporaryFile(path: String) =
            withContext(Dispatchers.IO) {
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

        override suspend fun copyUriToTempFile(
            uriString: String,
            tempFile: File,
        ) = withContext(Dispatchers.IO) {
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
            customFolderUri: String?,
            overwrite: Boolean,
            relativePath: String?,
        ): String =
            withContext(Dispatchers.IO) {
                val sanitizedTitle = FilenameSanitizer.sanitize(title)
                val filename = "$sanitizedTitle.$extension"
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                        ?: if (mediaType == MediaType.VIDEO) "video/$extension" else "audio/$extension"

                val settings = settingsRepository.settings.first()
                val folderUriStr =
                    if (!customFolderUri.isNullOrEmpty()) {
                        customFolderUri
                    } else if (mediaType == MediaType.VIDEO) {
                        settings.videoFolderUri
                    } else {
                        settings.audioFolderUri
                    }

                val resolvedCustomFolderName =
                    if (!customFolderUri.isNullOrEmpty()) {
                        resolveRelativePathFromUri(context, Uri.parse(customFolderUri))
                    } else {
                        null
                    }

                val paths =
                    computeStoragePaths(
                        mediaType = mediaType,
                        customFolderUri = customFolderUri,
                        resolvedCustomFolderName = resolvedCustomFolderName,
                        videoFolderSetting = settings.videoFolder,
                        audioFolderSetting = settings.audioFolder,
                        relativePath = relativePath,
                    )
                val effectiveRelativePath = paths.effectiveRelativePath

                if (folderUriStr.isNotEmpty() && folderUriStr.startsWith("content://")) {
                    try {
                        val folderUri = Uri.parse(folderUriStr)
                        var dirFile = DocumentFile.fromTreeUri(context, folderUri)
                        if (dirFile != null && dirFile.exists() && dirFile.isDirectory) {
                            if (!effectiveRelativePath.isNullOrEmpty()) {
                                val parts = effectiveRelativePath.split('/').filter { it.isNotEmpty() }
                                for (part in parts) {
                                    var subDir = dirFile?.findFile(part)
                                    if (subDir == null) {
                                        subDir = dirFile?.createDirectory(part)
                                    }
                                    dirFile = subDir
                                }
                            }
                            if (dirFile != null && dirFile.exists() && dirFile.isDirectory) {
                                // Use a unique tmp name to avoid any MIME-based renaming collisions
                                val tmpId = System.currentTimeMillis()
                                val tmpFilename = "sbskip_tmp_$tmpId.tmp"
                                val existingTmp = dirFile.findFile(tmpFilename)
                                existingTmp?.delete()

                                val newFile =
                                    dirFile.createFile(mimeType, tmpFilename)
                                        ?: throw IOException("Failed to create document file inside SAF directory")

                                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                    FileInputStream(tempFile).use { input ->
                                        input.copyTo(output)
                                    }
                                } ?: throw IOException("Failed to open SAF output stream")

                                // Only delete the existing file AFTER tmp is written successfully,
                                // but BEFORE rename to prevent Android appending (1)
                                if (overwrite || settings.overwriteBehavior) {
                                    val existingFile = dirFile.findFile(filename)
                                    if (existingFile != null) {
                                        existingFile.delete()
                                        AppLogger.worker("Deleted existing file before rename: $filename")
                                    }
                                }

                                val renamed = newFile.renameTo(filename)
                                if (!renamed) {
                                    throw IOException("Failed to rename temporary file to final filename: $filename")
                                }

                                AppLogger.worker("Successfully saved clean file to custom SAF directory: ${newFile.name ?: filename}")
                                return@withContext newFile.uri.toString()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.error("Storage", e, "Failed to save via SAF to custom folder $folderUriStr, falling back to MediaStore")
                    }
                }

                // 2. Failsafe Fallback: Standard MediaStore or Legacy storage
                val trueFinalFolder = paths.mediaStoreFolder
                val useDownloadsUri = paths.useDownloadsUri
                val contentUri =
                    if (useDownloadsUri && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else if (mediaType == MediaType.VIDEO) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }

                val tmpFilename = "$filename.tmp"
                val contentValues =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, tmpFilename)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, trueFinalFolder)
                        }
                    }

                val resolver = context.contentResolver
                val uri =
                    resolver.insert(contentUri, contentValues)
                        ?: throw IOException("Failed to insert media entry into MediaStore")

                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open MediaStore output stream")

                    if (overwrite || settings.overwriteBehavior) {
                        val selection: String
                        val selectionArgs: Array<String>
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                            selectionArgs = arrayOf(filename, trueFinalFolder + "/")
                        } else {
                            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                            selectionArgs = arrayOf(filename)
                        }
                        try {
                            val deletedCount = resolver.delete(contentUri, selection, selectionArgs)
                            AppLogger.worker("Deleted existing MediaStore entry for overwrite: $filename count=$deletedCount")
                        } catch (e: Exception) {
                            AppLogger.error("Storage", e, "Failed to delete existing MediaStore entry for overwrite")
                        }
                    }

                    val updateValues =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        }
                    resolver.update(uri, updateValues, null, null)

                    AppLogger.worker("Saved clean file to MediaStore: $trueFinalFolder/$filename")
                    uri.toString()
                } catch (e: Exception) {
                    // Cleanup inserted failed record
                    resolver.delete(uri, null, null)
                    throw e
                }
            }

        override suspend fun queryMetadata(uriString: String): MediaFileMetadata? =
            withContext(Dispatchers.IO) {
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
                val (title, extension) =
                    if (dotIndex != -1 && dotIndex < displayName.length - 1) {
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
                    } catch (ignored: Exception) {
                    }
                }

                MediaFileMetadata(
                    title = title,
                    extension = extension,
                    durationSeconds = durationSeconds,
                )
            }

        override suspend fun deleteUri(uriString: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        val deleted = documentFile.delete()
                        AppLogger.worker("Deleted URI via SAF: $uriString status=$deleted")
                        if (deleted) return@withContext true
                    }
                    // Fallback to ContentResolver
                    val deletedCount = context.contentResolver.delete(uri, null, null)
                    AppLogger.worker("Deleted URI via ContentResolver: $uriString count=$deletedCount")
                    deletedCount > 0
                } catch (e: Exception) {
                    AppLogger.error("Storage", e, "Failed to delete URI: $uriString")
                    false
                }
            }

        override suspend fun checkFileExists(
            title: String,
            extension: String,
            mediaType: MediaType,
            customFolderUri: String?,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val filename = "$title.$extension"
                val settings = settingsRepository.settings.first()
                val folderUriStr =
                    if (!customFolderUri.isNullOrEmpty()) {
                        customFolderUri
                    } else if (mediaType == MediaType.VIDEO) {
                        settings.videoFolderUri
                    } else {
                        settings.audioFolderUri
                    }
                if (folderUriStr.isNotEmpty() && folderUriStr.startsWith("content://")) {
                    try {
                        val folderUri = Uri.parse(folderUriStr)
                        val dirFile = DocumentFile.fromTreeUri(context, folderUri)
                        if (dirFile != null && dirFile.exists() && dirFile.isDirectory) {
                            return@withContext dirFile.findFile(filename) != null
                        }
                    } catch (e: Exception) {
                        AppLogger.error("Storage", e, "Failed to check file existence in SAF folder")
                    }
                }

                // Fallback to MediaStore
                val contentUri =
                    if (mediaType == MediaType.VIDEO) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(filename)
                try {
                    context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        return@withContext cursor.count > 0
                    }
                } catch (e: Exception) {
                    AppLogger.error("Storage", e, "Failed to check file existence in MediaStore")
                }

                return@withContext false
            }

        override suspend fun getUniqueTitle(
            baseTitle: String,
            extension: String,
            mediaType: MediaType,
            customFolderUri: String?,
        ): String =
            withContext(Dispatchers.IO) {
                var suffixNum = 1
                var candidateTitle = "${baseTitle}_$suffixNum"
                while (checkFileExists(candidateTitle, extension, mediaType, customFolderUri)) {
                    suffixNum++
                    candidateTitle = "${baseTitle}_$suffixNum"
                }
                return@withContext candidateTitle
            }

        override suspend fun hasPersistedPermission(uriString: String): Boolean {
            val uri = Uri.parse(uriString)
            return context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        }

        override suspend fun getMatchedWatchlistFolder(localFileUri: String): com.nihaltp.sbskip.model.MatchedWatchFolder? =
            withContext(Dispatchers.IO) {
                val settings = settingsRepository.settings.first()
                if (settings.watchlist.isEmpty()) return@withContext null

                val uri = Uri.parse(localFileUri)
                var relativePath: String? = null
                var docId: String? = null

                if (uri.scheme == "content") {
                    try {
                        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                            docId = android.provider.DocumentsContract.getDocumentId(uri)
                        } else {
                            val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
                            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                                    if (index != -1) {
                                        relativePath = cursor.getString(index)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.error("Storage", e, "Failed to resolve path for $localFileUri")
                    }
                } else if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null) {
                        for (folder in settings.watchlist) {
                            // Watchlist folder path is something like "Download/NewPipe/"
                            // File path is "/storage/emulated/0/Download/NewPipe/video.mp4"
                            if (path.contains(folder.path)) {
                                val relPath = path.substringAfter(folder.path).trimStart('/').substringBeforeLast('/', "")
                                return@withContext com.nihaltp.sbskip.model.MatchedWatchFolder(folder, relPath)
                            }
                        }
                    }
                }

                for (folder in settings.watchlist) {
                    // Direct string prefix match is robust for SAF tree URIs containing documents
                    if (uri.toString().startsWith(folder.uri)) {
                        val folderTreeId =
                            try {
                                val treeUri = Uri.parse(folder.uri)
                                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                            } catch (e: Exception) {
                                null
                            }

                        val relPath =
                            if (docId != null && folderTreeId != null && docId.startsWith(folderTreeId)) {
                                val remaining = docId.removePrefix(folderTreeId).trimStart('/')
                                remaining.substringBeforeLast('/', "")
                            } else {
                                ""
                            }
                        return@withContext com.nihaltp.sbskip.model.MatchedWatchFolder(folder, relPath)
                    }

                    val folderTreeId =
                        try {
                            val treeUri = Uri.parse(folder.uri)
                            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                        } catch (e: Exception) {
                            null
                        }

                    if (docId != null && folderTreeId != null && docId.startsWith(folderTreeId)) {
                        // docId: primary:Download/NewPipe/Playlist/video.mp4
                        // folderTreeId: primary:Download/NewPipe
                        val remaining = docId.removePrefix(folderTreeId).trimStart('/')
                        val relPath = remaining.substringBeforeLast('/', "")
                        return@withContext com.nihaltp.sbskip.model.MatchedWatchFolder(folder, relPath)
                    }

                    if (relativePath != null && relativePath!!.contains(folder.path)) {
                        val relPath = relativePath!!.substringAfter(folder.path).trim('/')
                        return@withContext com.nihaltp.sbskip.model.MatchedWatchFolder(folder, relPath)
                    }
                }
                null
            }
    }

private fun resolveRelativePathFromUri(
    context: android.content.Context,
    uri: Uri,
): String {
    try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)

        try {
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use {
                    cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index != -1) {
                        val dispName = cursor.getString(index)
                        if (!dispName.isNullOrBlank()) {
                            return "$dispName/"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        val split = docId.split(":")
        val rawPath = if (split.size > 1) split[1] else docId
        val trimmedPath = rawPath.trim('/')
        return if (trimmedPath.isEmpty()) "SB Skip/" else "$trimmedPath/"
    } catch (e: Exception) {
        val path = uri.path ?: ""
        return if (path.isEmpty()) "SB Skip/" else "${path.trim('/')}/"
    }
}
