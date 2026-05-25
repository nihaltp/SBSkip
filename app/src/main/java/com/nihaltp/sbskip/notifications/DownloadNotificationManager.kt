package com.nihaltp.sbskip.notifications

interface DownloadNotificationManager {
    fun showActive(id: Long, title: String, progress: Int, message: String)
    fun showCompletion(id: Long, title: String)
    fun showFailure(id: Long, title: String, errorMessage: String)
    fun showCancelled(id: Long, title: String)
}
