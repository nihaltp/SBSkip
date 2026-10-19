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

    @Query("DELETE FROM downloaded_videos")
    suspend fun deleteAll()
}
