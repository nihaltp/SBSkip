package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.QueueActionResult
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun observeQueue(): Flow<List<DownloadQueueItem>>

    suspend fun enqueue(
        localFileUri: String,
        title: String,
        youtubeUrl: String,
        mediaType: MediaType,
        convertVideoToAudio: Boolean = false,
        deleteOriginalVideo: Boolean = true,
        audioOutputDirUri: String? = null,
    ): QueueActionResult

    suspend fun retry(
        itemId: Long,
        bypassDurationCheck: Boolean = false,
    ): QueueActionResult

    suspend fun remove(itemId: Long)

    suspend fun findItemById(itemId: Long): DownloadQueueItem?

    suspend fun markFetchingSegments(itemId: Long)

    suspend fun markProcessing(itemId: Long)

    suspend fun markCompleted(
        itemId: Long,
        outputPath: String,
    )

    suspend fun markFailed(
        itemId: Long,
        errorMessage: String,
    )

    suspend fun updateMetadata(
        itemId: Long,
        title: String,
        thumbnailUrl: String?,
        durationSeconds: Long?,
    )
}
