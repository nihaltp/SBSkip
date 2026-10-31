package com.nihaltp.sbskip.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY id DESC")
    fun observeDownloadedVideos(): Flow<List<DownloadedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DownloadedVideoEntity): Long

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(entity: DownloadedVideoEntity)

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun findByVideoId(videoId: String): DownloadedVideoEntity?

    /**
     * Videos whose metadata still has to be resolved, newest first, so that the rows at the top of
     * the downloaded history — the ones the user is looking at — are described first.
     */
    @Query("SELECT * FROM downloaded_videos WHERE needsEnrichment = 1 ORDER BY id DESC LIMIT :limit")
    suspend fun findNeedingEnrichment(limit: Int): List<DownloadedVideoEntity>

    @Query(
        """
        UPDATE downloaded_videos
        SET title = :title,
            author = :author,
            thumbnailUrl = :thumbnailUrl,
            durationSeconds = :durationSeconds,
            needsEnrichment = 0
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(
        id: Long,
        title: String,
        author: String?,
        thumbnailUrl: String?,
        durationSeconds: Long?,
    ): Int

    @Query("UPDATE downloaded_videos SET needsEnrichment = 0 WHERE id = :id")
    suspend fun clearNeedsEnrichment(id: Long): Int

    @Query("DELETE FROM downloaded_videos")
    suspend fun deleteAll()
}
