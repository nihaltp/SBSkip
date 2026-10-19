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
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val outputPath: String?,
    val fileSize: Long? = null,
    val mediaType: String,
)
