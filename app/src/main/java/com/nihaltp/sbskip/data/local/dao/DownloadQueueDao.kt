package com.nihaltp.sbskip.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeQueue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE id = :id")
    suspend fun findById(id: Long): DownloadQueueEntity?

    @Query("SELECT * FROM download_queue WHERE status = 'QUEUED' ORDER BY createdAtEpochMillis ASC, id ASC LIMIT 1")
    suspend fun findNextQueuedItem(): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DownloadQueueEntity): Long

    @Query(
        """
        UPDATE download_queue
        SET status = :status,
            errorMessage = :errorMessage,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: Long,
        status: DownloadQueueStatus,
        errorMessage: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE download_queue
        SET url = :url,
            status = :status,
            errorMessage = :errorMessage,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateUrlAndStatus(
        id: Long,
        url: String,
        status: DownloadQueueStatus,
        errorMessage: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE download_queue
        SET title = :title,
            thumbnailUrl = :thumbnailUrl,
            durationSeconds = :durationSeconds,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(
        id: Long,
        title: String,
        thumbnailUrl: String?,
        durationSeconds: Long?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE download_queue
        SET status = :status,
            errorMessage = NULL,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun markStatus(
        id: Long,
        status: DownloadQueueStatus,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE download_queue
        SET status = 'COMPLETED',
            outputPath = :outputPath,
            errorMessage = NULL,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun markCompleted(
        id: Long,
        outputPath: String,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        SELECT * FROM download_queue
        WHERE status = 'FAILED'
          AND (errorMessage LIKE '%timeout%'
            OR errorMessage LIKE '%timed out%'
            OR errorMessage LIKE '%unable to resolve host%'
            OR errorMessage LIKE '%sponsor.ajay.app%')
        """,
    )
    suspend fun findSponsorBlockFailedItems(): List<DownloadQueueEntity>

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(entity: DownloadQueueEntity)
}
