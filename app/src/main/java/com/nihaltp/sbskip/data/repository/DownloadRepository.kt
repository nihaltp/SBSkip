package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.model.DownloadRequest
import com.nihaltp.sbskip.model.QueueItem
import com.nihaltp.sbskip.model.RecentDownload
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeQueue(): Flow<List<QueueItem>>

    fun observeRecentDownloads(): Flow<List<RecentDownload>>

    suspend fun queue(
        request: DownloadRequest,
        title: String,
    ): Long

    suspend fun cancel(id: Long)

    suspend fun remove(id: Long)
}
