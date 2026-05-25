package com.nihaltp.sbskip.data.repository

import android.content.Context
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.QueueActionResult
import com.nihaltp.sbskip.model.VideoMetadata
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import com.nihaltp.sbskip.workers.MetadataQueueWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultQueueRepository @Inject constructor(
    private val dao: DownloadQueueDao,
    private val workScheduler: MetadataQueueWorkScheduler,
    @ApplicationContext private val context: Context,
) : QueueRepository {
    override fun observeQueue(): Flow<List<DownloadQueueItem>> = dao.observeQueue().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun enqueue(rawInput: String): QueueActionResult {
        val normalizedUrl = YouTubeUrlParser.normalize(rawInput)
            ?: return QueueActionResult(success = false, message = context.getString(R.string.enter_valid_url))

        val now = System.currentTimeMillis()
        val entity = DownloadQueueEntity(
            url = normalizedUrl,
            title = context.getString(R.string.fetching_metadata),
            thumbnailUrl = null,
            durationSeconds = null,
            status = DownloadQueueStatus.QUEUED,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            errorMessage = null,
        )

        val id = dao.insert(entity)
        AppLogger.queue("queued item id=$id url=$normalizedUrl")
        workScheduler.schedule()
        return QueueActionResult(success = true, message = context.getString(R.string.queued_for_metadata_fetch))
    }

    override suspend fun retry(itemId: Long): QueueActionResult {
        dao.updateStatus(
            id = itemId,
            status = DownloadQueueStatus.QUEUED,
            errorMessage = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        AppLogger.queue("retry item id=$itemId")
        workScheduler.schedule()
        return QueueActionResult(success = true, message = context.getString(R.string.retry_started))
    }

    override suspend fun remove(itemId: Long) {
        dao.deleteById(itemId)
        AppLogger.queue("removed item id=$itemId")
    }

    override suspend fun claimNextQueuedItem(): DownloadQueueItem? {
        return dao.findNextQueuedItem()?.toDomain()
    }

    override suspend fun markFetchingInfo(itemId: Long) {
        AppLogger.queue("fetching metadata for id=$itemId")
        dao.updateStatus(
            id = itemId,
            status = DownloadQueueStatus.FETCHING_INFO,
            errorMessage = null,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun markReady(itemId: Long, metadata: VideoMetadata) {
        AppLogger.queue("ready id=$itemId title=${metadata.title}")
        dao.markReady(
            id = itemId,
            title = metadata.title,
            thumbnailUrl = metadata.thumbnailUrl,
            durationSeconds = metadata.durationSeconds,
            status = DownloadQueueStatus.READY,
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

    private fun DownloadQueueEntity.toDomain(): DownloadQueueItem {
        return DownloadQueueItem(
            id = id,
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            status = status,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            errorMessage = errorMessage,
        )
    }
}
