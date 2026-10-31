package com.nihaltp.sbskip.notifications

import androidx.work.ForegroundInfo

interface DownloadNotificationManager {
    /**
     * Creates the notification channel of the download notifications. Called when the app starts,
     * so that the channel exists before the first notification is posted.
     */
    fun ensureChannel()

    fun createForegroundInfo(
        id: Long,
        title: String,
        message: String,
    ): ForegroundInfo

    fun showActive(
        id: Long,
        title: String,
        progress: Int,
        message: String,
    )

    fun showCompletion(
        id: Long,
        title: String,
    )

    fun showFailure(
        id: Long,
        title: String,
        errorMessage: String,
    )

    fun showCancelled(
        id: Long,
        title: String,
    )
}
