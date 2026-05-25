package com.nihaltp.sbskip.model

enum class MediaType {
    VIDEO,
    AUDIO,
}

enum class DownloadStatus {
    QUEUED,
    FETCHING_INFO,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class SponsorBlockCategory {
    SPONSOR,
    SELF_PROMOTION,
    INTRO,
    OUTRO,
    INTERACTION_REMINDER,
    PREVIEW_RECAP,
    FILLER_TANGENT,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class SponsorBlockSettings(
    val enabled: Boolean = true,
    val categories: Set<SponsorBlockCategory> = SponsorBlockCategory.entries.toSet(),
)

data class DownloadRequest(
    val url: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val useGlobalSponsorBlockSettings: Boolean = true,
    val sponsorBlockSettings: SponsorBlockSettings = SponsorBlockSettings(),
)

data class MediaMetadata(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val uploader: String? = null,
)

data class QueueItem(
    val id: Long,
    val title: String,
    val url: String,
    val mediaType: MediaType,
    val status: DownloadStatus,
    val progress: Int = 0,
    val message: String? = null,
)

data class RecentDownload(
    val id: Long,
    val title: String,
    val filePath: String,
    val status: DownloadStatus,
    val finishedAtEpochMillis: Long,
)

data class DownloadConfigurationState(
    val title: String = "",
    val thumbnailUrl: String? = null,
    val request: DownloadRequest = DownloadRequest(),
)
