package com.nihaltp.sbskip.model

enum class MediaType {
    VIDEO,
    AUDIO,
}

enum class DownloaderType {
    NEWPIPE,
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
    SPONSOR, //              Sponsor segments
    SELF_PROMOTION, //       Unpaid/Self Promotion segments
    INTERACTION_REMINDER, // Interaction Reminder segments
    INTRO, //                Intermission/Intro Animation segments
    OUTRO, //                Endcards/Credits segments
    PREVIEW_RECAP, //        Preview/Recap segments
    HOOK, //                 Hook/Greetings segments
    FILLER_TANGENT, //       Tangents/Jokes segments
    MUSIC_OFFTOPIC, //       Music: Non-Music Section segments
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AudioSaveMode {
    PRESET_FOLDER,
    RUNTIME_PICKER,
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
    val webpageUrl: String? = null,
    val extractor: String? = null,
)

data class VideoMetadata(
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val uploader: String? = null,
    val webpageUrl: String? = null,
    val extractor: String? = null,
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
    val queueItemId: Long? = null,
    val title: String = "",
    val thumbnailUrl: String? = null,
    val request: DownloadRequest = DownloadRequest(),
)
