package com.nihaltp.sbskip.storage

import com.nihaltp.sbskip.model.MediaType
import java.io.File

interface DownloadStorage {
    suspend fun deleteTemporaryFile(path: String)
    suspend fun copyUriToTempFile(uriString: String, tempFile: File)
    suspend fun saveToPublicStorage(
        tempFile: File,
        title: String,
        extension: String,
        mediaType: MediaType,
        customFolderUri: String? = null,
        overwrite: Boolean = false,
    ): String
    suspend fun queryMetadata(uriString: String): MediaFileMetadata?
    suspend fun deleteUri(uriString: String): Boolean
    suspend fun checkFileExists(
        title: String,
        extension: String,
        mediaType: MediaType,
        customFolderUri: String? = null,
    ): Boolean
    suspend fun getUniqueTitle(
        baseTitle: String,
        extension: String,
        mediaType: MediaType,
        customFolderUri: String? = null,
    ): String
}

data class MediaFileMetadata(
    val title: String,
    val extension: String,
    val durationSeconds: Long?,
)
