package com.nihaltp.sbskip.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String?,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val downloadedAtEpochMillis: Long,
    val outputPath: String?,
    val mediaType: String,
)
