package com.nihaltp.sbskip.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A downloaded video.
 *
 * A video is tracked by its YouTube id: the very same video is never stored twice. Metadata such
 * as the title, author and duration is resolved from YouTube via oEmbed for imported videos.
 */
@Entity(
    tableName = "downloaded_videos",
    indices = [Index(value = ["videoId"], unique = true)],
)
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String?,
    val url: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val fileSize: Long? = null,
    val mediaType: String,
    /**
     * Whether the metadata of this video still has to be resolved from YouTube. Imported videos
     * are queued for enrichment; videos created by a folder scan already carry file metadata.
     */
    val needsEnrichment: Boolean = false,
)
