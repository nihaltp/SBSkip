package com.nihaltp.sbskip.storage

interface DownloadStorage {
    suspend fun resolveOutputPath(directory: String, title: String, extension: String): String
    suspend fun deleteTemporaryFile(path: String)
}
