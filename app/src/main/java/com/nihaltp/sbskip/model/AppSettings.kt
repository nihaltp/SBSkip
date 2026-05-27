package com.nihaltp.sbskip.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val videoFolder: String = "Movies/SB Skip/",
    val audioFolder: String = "Music/SB Skip/",
    val videoFolderUri: String = "",
    val audioFolderUri: String = "",
    val tempFolder: String = "SB Skip/tmp/",
    val sponsorBlockSettings: SponsorBlockSettings = SponsorBlockSettings(),
    val filenameReplacement: Char = '_',
    val keepTempFiles: Boolean = false,
    val verboseLogging: Boolean = false,
    val sponsorBlockUrl: String = "https://sponsor.ajay.app",
    val overwriteBehavior: Boolean = true,
    val autoCleanSuffix: String = "_cleaned",
)
