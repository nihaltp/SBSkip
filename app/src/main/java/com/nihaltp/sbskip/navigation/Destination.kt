package com.nihaltp.sbskip.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object DownloadConfig : Destination("download_config")
    data object Settings : Destination("settings")
}
