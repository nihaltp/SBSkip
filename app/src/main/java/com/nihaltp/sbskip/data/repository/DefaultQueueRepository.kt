package com.nihaltp.sbskip.data.repository

import android.content.Context
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.QueueActionResult
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import com.nihaltp.sbskip.workers.DownloadWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultQueueRepository @Inject constructor(
    private val dao: DownloadQueueDao,
    private val workScheduler: DownloadWorkScheduler,
    @ApplicationContext private val context: Context,
) : QueueRepository {
    override fun observeQueue(): Flow<List<DownloadQueueItem>> = dao.observeQueue().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun enqueue(
        localFileUri: String,
        title: String,
        youtubeUrl: String,
        mediaType: MediaType,
        convertVideoToAudio: Boolean,
        deleteOriginalVideo: Boolean,
        audioOutputDirUri: String?,
    ): QueueActionResult {
        val videoId = if (youtubeUrl.isBlank()) {
            null
        } else {
            YouTubeUrlParser.extractVideoId(youtubeUrl)
                ?: return QueueActionResult(success = false, message = context.getString(R.string.enter_valid_url))
        }

        val normalizedUrl = if (videoId != null) {
            val hasBypass = youtubeUrl.contains("bypassDurationCheck=true")
            "https://www.youtube.com/watch?v=$videoId" + if (hasBypass) "&bypassDurationCheck=true" else ""
        } else {
            ""
        }
        val now = System.currentTimeMillis()
        val entity = DownloadQueueEntity(
            url = normalizedUrl,
            title = title,
            localFileUri = localFileUri,
            mediaType = mediaType.name,
            thumbnailUrl = null,
            durationSeconds = null,
            status = DownloadQueueStatus.QUEUED,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            errorMessage = null,
            outputPath = null,
            convertVideoToAudio = convertVideoToAudio,
            deleteOriginalVideo = deleteOriginalVideo,
            audioOutputDirUri = audioOutputDirUri,
        )

        val id = dao.insert(entity)
        AppLogger.queue("queued item id=$id url=$normalizedUrl file=$localFileUri")
        workScheduler.schedule(id)
        return QueueActionResult(success = true, message = context.getString(R.string.download_started))
    }

    override suspend fun retry(itemId: Long): QueueActionResult {
        val item = dao.findById(itemId)
            ?: return QueueActionResult(success = false, message = context.getString(R.string.error_item_not_found))

        dao.updateStatus(
            id = itemId,
            status = DownloadQueueStatus.QUEUED,
            errorMessage = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        AppLogger.queue("retry item id=$itemId")
        workScheduler.schedule(itemId)
        return QueueActionResult(success = true, message = context.getString(R.string.retry_started))
    }

    override suspend fun remove(itemId: Long) {
        dao.deleteById(itemId)
        AppLogger.queue("removed item id=$itemId")
    }

    override suspend fun findItemById(itemId: Long): DownloadQueueItem? {
        return dao.findById(itemId)?.toDomain()
    }

    override suspend fun markFetchingSegments(itemId: Long) {
        AppLogger.queue("fetching segments for id=$itemId")
        dao.updateStatus(
            id = itemId,
            status = DownloadQueueStatus.FETCHING_SEGMENTS,
            errorMessage = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun markProcessing(itemId: Long) {
        AppLogger.queue("processing id=$itemId")
        dao.markStatus(
            id = itemId,
            status = DownloadQueueStatus.PROCESSING,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun markCompleted(itemId: Long, outputPath: String) {
        AppLogger.queue("completed id=$itemId outputPath=$outputPath")
        dao.markCompleted(
            id = itemId,
            outputPath = outputPath,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun markFailed(itemId: Long, errorMessage: String) {
        AppLogger.queue("failed id=$itemId error=$errorMessage")
        dao.updateStatus(
            id = itemId,
            status = DownloadQueueStatus.FAILED,
            errorMessage = errorMessage,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun updateMetadata(
        itemId: Long,
        title: String,
        thumbnailUrl: String?,
        durationSeconds: Long?,
    ) {
        AppLogger.queue("updateMetadata id=$itemId title=$title duration=$durationSeconds")
        dao.updateMetadata(
            id = itemId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun DownloadQueueEntity.toDomain(): DownloadQueueItem {
        val parsedMediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.VIDEO)
        return DownloadQueueItem(
            id = id,
            url = url,
            title = title,
            localFileUri = localFileUri,
            mediaType = parsedMediaType,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            status = status,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            errorMessage = errorMessage,
            outputPath = outputPath,
            convertVideoToAudio = convertVideoToAudio,
            deleteOriginalVideo = deleteOriginalVideo,
            audioOutputDirUri = audioOutputDirUri,
        )
    }
}
