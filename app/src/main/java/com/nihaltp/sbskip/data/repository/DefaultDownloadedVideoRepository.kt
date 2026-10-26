package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.data.local.dao.DownloadedVideoDao
import com.nihaltp.sbskip.data.local.database.StringListConverter
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDownloadedVideoRepository
    @Inject
    constructor(
        private val dao: DownloadedVideoDao,
    ) : DownloadedVideoRepository {
        override val observeDownloadedVideos: Flow<List<DownloadedVideoEntity>> =
            dao.observeDownloadedVideos()

        override suspend fun insert(video: DownloadedVideoEntity): Long = dao.insert(video)

        override suspend fun findByVideoId(videoId: String): DownloadedVideoEntity? = dao.findByVideoId(videoId)

        override suspend fun findByOutputPath(outputPath: String): DownloadedVideoEntity? =
            dao.findByOutputPathCandidates(outputPath)
                .firstOrNull { it.outputPaths.contains(outputPath) }

        override suspend fun addOutputPath(
            videoId: String,
            outputPath: String,
        ): Boolean {
            val existing = dao.findByVideoId(videoId) ?: return false
            if (existing.outputPaths.contains(outputPath)) return true
            val updatedPaths = existing.outputPaths + outputPath
            return dao.updateOutputPaths(existing.id, StringListConverter.encode(updatedPaths)) > 0
        }

        override suspend fun deleteById(id: Long) {
            dao.deleteById(id)
        }

        override suspend fun deleteAll() {
            dao.deleteAll()
        }

        override suspend fun findNeedingEnrichment(limit: Int): List<DownloadedVideoEntity> = dao.findNeedingEnrichment(limit)

        override suspend fun updateMetadata(
            id: Long,
            title: String,
            author: String?,
            thumbnailUrl: String?,
            durationSeconds: Long?,
        ): Boolean =
            dao.updateMetadata(
                id = id,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
            ) > 0

        override suspend fun clearNeedsEnrichment(id: Long) {
            dao.clearNeedsEnrichment(id)
        }

        override suspend fun setVerified(
            id: Long,
            isVerified: Boolean,
        ) {
            dao.updateVerified(id, isVerified)
        }
    }
