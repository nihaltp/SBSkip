package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow

interface DownloadedVideoRepository {
    val observeDownloadedVideos: Flow<List<DownloadedVideoEntity>>

    suspend fun insert(video: DownloadedVideoEntity): Long

    suspend fun findByVideoId(videoId: String): DownloadedVideoEntity?

    suspend fun findByOutputPath(outputPath: String): DownloadedVideoEntity?

    suspend fun deleteById(id: Long)

    suspend fun deleteAll()
}
