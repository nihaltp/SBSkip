package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.data.local.dao.DownloadedVideoDao
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

        override suspend fun findByOutputPath(outputPath: String): DownloadedVideoEntity? = dao.findByOutputPath(outputPath)

        override suspend fun deleteById(id: Long) {
            dao.deleteById(id)
        }

        override suspend fun deleteAll() {
            dao.deleteAll()
        }
    }
