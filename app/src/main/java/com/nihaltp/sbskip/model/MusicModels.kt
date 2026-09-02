package com.nihaltp.sbskip.model

enum class MusicVideoType {
    OFFICIAL_MUSIC_VIDEO,
    OFFICIAL_AUDIO,
    LYRIC_VIDEO,
    LIVE,
    PERFORMANCE,
    OTHER,
}

data class MusicMetadata(
    val title: String?,
    val artists: List<String>,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val genre: String?,
    val artworkUrl: String?,
)
