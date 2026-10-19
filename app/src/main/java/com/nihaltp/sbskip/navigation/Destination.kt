package com.nihaltp.sbskip.navigation

abstract class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")

    data object Home : Destination("home")

    data object DownloadConfig : Destination("download_config")

    data object Settings : Destination("settings")

    data object Licenses : Destination("licenses")

    data object Logs : Destination("logs")

    data object DownloadedVideos : Destination("downloaded_videos")

    data object ImportFromFolder : Destination("import_from_folder")
}
