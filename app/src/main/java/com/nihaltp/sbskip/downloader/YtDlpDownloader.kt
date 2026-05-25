package com.nihaltp.sbskip.downloader

import com.nihaltp.sbskip.model.DownloadRequest
import com.nihaltp.sbskip.model.MediaMetadata

interface YtDlpDownloader {
    suspend fun fetchMetadata(url: String): MediaMetadata
    suspend fun download(request: DownloadRequest): DownloadResult
}

data class DownloadResult(
    val outputFile: String,
    val logs: List<String> = emptyList(),
)
