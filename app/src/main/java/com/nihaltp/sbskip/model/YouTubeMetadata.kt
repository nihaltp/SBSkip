package com.nihaltp.sbskip.model

data class YouTubeMetadata(
    val title: String?,
    val authorName: String?,
    val authorUrl: String?,
    val thumbnailUrl: String?,
    val description: String? = null,
    val categoryId: Int? = null,
    val musicVideoType: MusicVideoType? = null,
    val musicConfidence: Float = 0f,
    val musicMetadata: MusicMetadata? = null,
)
