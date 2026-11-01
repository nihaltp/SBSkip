package com.nihaltp.sbskip.model

data class MediaFileMetadata(
    val title: String,
    val extension: String,
    val durationSeconds: Long?,
    val youtubeId: String? = null,
    val youtubeUrl: String? = null,
    val youtubeTitle: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val thumbnailUrl: String? = null,
    val sbskipSegments: String? = null,
    val sbskipVersion: String? = null,
    val sbskipProcessed: Boolean? = null,
    val sbskipProcessedAt: Long? = null,
    val sbskipRemovedCategories: String? = null,
    val fileSize: Long? = null,
)
