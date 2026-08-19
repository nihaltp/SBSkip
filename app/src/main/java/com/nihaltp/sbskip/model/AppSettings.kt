package com.nihaltp.sbskip.model

import com.nihaltp.sbskip.util.Constants

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val downloader: DownloaderType = DownloaderType.NEWPIPE,
    val videoFolder: String = "Movies/SB Skip/",
    val audioFolder: String = "Music/SB Skip/",
    val videoFolderUri: String = "",
    val audioFolderUri: String = "",
    val tempFolder: String = "SB Skip/tmp/",
    val sponsorBlockSettings: SponsorBlockSettings = SponsorBlockSettings(),
    val filenameReplacement: Char = '_',
    val verboseLogging: Boolean = false,
    val sponsorBlockUrl: String = Constants.SPONSORBLOCK_BASE_URL,
    val sponsorBlockStatusUrl: String = Constants.SPONSORBLOCK_STATUS_BASE_URL,
    val overwriteBehavior: Boolean = true,
    val autoCleanSuffix: String = "",
    val autoStartCleaning: Boolean = true,
    val defaultConvertVideoToAudio: Boolean = false,
    val defaultDeleteOriginalVideo: Boolean = true,
    val bypassSmallDurationDifference: Boolean = false,
    val maxDurationDifferenceSeconds: Int = 1,
    val watchlist: List<WatchlistFolder> = emptyList(),
    val pendingDownloads: List<PendingDownload> = emptyList(),
    val playlistDownloadState: PlaylistDownloadState? = null,
)
