package com.nihaltp.sbskip.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A downloaded video.
 *
 * A video is tracked by its YouTube id: the very same video is never stored twice, even when it
 * exists as multiple files. All files of a video are collected in [outputPaths].
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
    val outputPaths: List<String> = emptyList(),
    val fileSize: Long? = null,
    val mediaType: String,
    /**
     * Whether at least one of [outputPaths] was confirmed to exist on this device. Videos found by
     * a folder scan are verified by definition; imported videos are verified when their backup path
     * still resolves, and stay unverified when the file is missing.
     */
    val isVerified: Boolean = true,
    /**
     * Whether the metadata of this video still has to be resolved from its local file or from
     * YouTube. Imported videos are queued for enrichment; scanned videos are already complete.
     */
    val needsEnrichment: Boolean = false,
) {
    /** The first known file of the video, used as its representative path. */
    val outputPath: String? get() = outputPaths.firstOrNull()
}
