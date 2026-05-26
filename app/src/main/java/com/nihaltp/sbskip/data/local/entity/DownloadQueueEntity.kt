package com.nihaltp.sbskip.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nihaltp.sbskip.model.DownloadQueueStatus

@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val localFileUri: String,
    val mediaType: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val status: DownloadQueueStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorMessage: String? = null,
)
