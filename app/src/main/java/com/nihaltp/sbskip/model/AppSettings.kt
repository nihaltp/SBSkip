package com.nihaltp.sbskip.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val downloader: DownloaderType = DownloaderType.NEWPIPE,
    val videoFolder: String = "Movies/SB Skip/",
    val audioFolder: String = "Music/SB Skip/",
    val videoFolderUri: String = "",
    val audioFolderUri: String = "",
    val newPipeVideoFolder: String = "Download/NewPipe/Video/",
    val newPipeAudioFolder: String = "Download/NewPipe/Audio/",
    val tempFolder: String = "SB Skip/tmp/",
    val sponsorBlockSettings: SponsorBlockSettings = SponsorBlockSettings(),
    val filenameReplacement: Char = '_',
    val keepTempFiles: Boolean = false,
    val verboseLogging: Boolean = false,
    val sponsorBlockUrl: String = "https://sponsor.ajay.app",
    val sponsorBlockStatusUrl: String = "https://status.sponsor.ajay.app",
    val overwriteBehavior: Boolean = true,
    val autoCleanSuffix: String = "_cleaned",
)
