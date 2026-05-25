package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.QueueActionResult
import com.nihaltp.sbskip.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun observeQueue(): Flow<List<DownloadQueueItem>>
    suspend fun enqueue(rawInput: String): QueueActionResult
    suspend fun retry(itemId: Long): QueueActionResult
    suspend fun remove(itemId: Long)
    suspend fun claimNextQueuedItem(): DownloadQueueItem?
    suspend fun markFetchingInfo(itemId: Long)
    suspend fun markReady(itemId: Long, metadata: VideoMetadata)
    suspend fun markFailed(itemId: Long, errorMessage: String)
}
