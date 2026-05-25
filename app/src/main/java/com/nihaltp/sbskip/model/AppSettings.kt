package com.nihaltp.sbskip.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val videoFolder: String = "Movies/SB Skip/",
    val audioFolder: String = "Music/SB Skip/",
    val tempFolder: String = "SB Skip/tmp/",
    val sponsorBlockSettings: SponsorBlockSettings = SponsorBlockSettings(),
    val filenameReplacement: Char = '_',
    val keepTempFiles: Boolean = false,
    val verboseLogging: Boolean = false,
)
