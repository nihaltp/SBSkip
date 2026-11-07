package com.nihaltp.sbskip.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val downloadSource: String = DownloadSource.LOCAL_FILE.name,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorMessage: String? = null,
    val outputPath: String? = null,
    val outputDurationSeconds: Long? = null,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
    val audioOutputDirUri: String? = null,
    val videoOutputDirUri: String? = null,
    val relativePath: String? = null,
    val overwrite: Boolean = false,
    val bypassDurationCheck: Boolean = false,
    val noSuffix: Boolean = false,
    // Custom SponsorBlock categories for this download (comma-separated category names)
    val customSponsorBlockCategories: String = "",
)
