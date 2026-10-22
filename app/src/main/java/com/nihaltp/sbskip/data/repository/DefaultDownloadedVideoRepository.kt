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
    }
