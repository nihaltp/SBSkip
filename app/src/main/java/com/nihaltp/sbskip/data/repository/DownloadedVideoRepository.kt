package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow

interface DownloadedVideoRepository {
    val observeDownloadedVideos: Flow<List<DownloadedVideoEntity>>

    suspend fun insert(video: DownloadedVideoEntity): Long

    suspend fun findByVideoId(videoId: String): DownloadedVideoEntity?

    suspend fun findByOutputPath(outputPath: String): DownloadedVideoEntity?

    /**
     * Adds a file to a video that is already tracked. A video is never stored twice, so a file of
     * an already downloaded video is added to its existing entry.
     *
     * @return true when the video is tracked and the file was added to it.
     */
    suspend fun addOutputPath(
        videoId: String,
        outputPath: String,
    ): Boolean

    suspend fun deleteById(id: Long)

    suspend fun deleteAll()
}
