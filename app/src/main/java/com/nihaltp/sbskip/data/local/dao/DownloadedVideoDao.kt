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
     * All videos whose path list may contain [outputPath]. The paths are stored as a json list,
     * so this is a coarse filter: the exact match is checked by the caller.
     */
    @Query("SELECT * FROM downloaded_videos WHERE outputPaths LIKE '%' || :outputPath || '%'")
    suspend fun findByOutputPathCandidates(outputPath: String): List<DownloadedVideoEntity>

    @Query("UPDATE downloaded_videos SET outputPaths = :outputPaths WHERE id = :id")
    suspend fun updateOutputPaths(
        id: Long,
        outputPaths: String,
    ): Int

    /**
     * Videos whose metadata still has to be resolved, oldest first, so a background worker can
     * enrich them in bounded batches.
     */
    @Query("SELECT * FROM downloaded_videos WHERE needsEnrichment = 1 ORDER BY id ASC LIMIT :limit")
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

    @Query("UPDATE downloaded_videos SET isVerified = :isVerified WHERE id = :id")
    suspend fun updateVerified(
        id: Long,
        isVerified: Boolean,
    ): Int

    @Query("DELETE FROM downloaded_videos")
    suspend fun deleteAll()
}
