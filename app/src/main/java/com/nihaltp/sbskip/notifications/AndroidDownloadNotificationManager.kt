package com.nihaltp.sbskip.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.nihaltp.sbskip.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadNotificationManager {
    override fun createForegroundInfo(id: Long, title: String, message: String): ForegroundInfo {
        val notification = buildNotification(
            title = title,
            message = message,
            ongoing = true,
            indeterminate = true,
            progress = 0,
        )
        // Use DATA_SYNC foreground service type for uploads/downloads so apps
        // targeting SDK 31+ don't get InvalidForegroundServiceTypeException.
        val fgType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return ForegroundInfo(id.toInt(), notification, fgType)
    }

    override fun showActive(id: Long, title: String, progress: Int, message: String) {
        notify(
            id = id,
            notification = buildNotification(
                title = title,
                message = message,
                ongoing = true,
                indeterminate = progress <= 0,
                progress = progress.coerceIn(0, 100),
            ),
        )
    }

    override fun showCompletion(id: Long, title: String) {
        notify(
            id = id,
            notification = buildNotification(
                title = title,
                message = context.getString(R.string.download_completed),
                ongoing = false,
                indeterminate = false,
                progress = 100,
            ),
        )
    }

    override fun showFailure(id: Long, title: String, errorMessage: String) {
        notify(
            id = id,
            notification = buildNotification(
                title = title,
                message = errorMessage,
                ongoing = false,
                indeterminate = false,
                progress = 0,
            ),
        )
    }

    override fun showCancelled(id: Long, title: String) {
        notify(
            id = id,
            notification = buildNotification(
                title = title,
                message = context.getString(R.string.download_cancelled),
                ongoing = false,
                indeterminate = false,
                progress = 0,
            ),
        )
    }

    private fun buildNotification(
        title: String,
        message: String,
        ongoing: Boolean,
        indeterminate: Boolean,
        progress: Int,
    ): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    private fun notify(id: Long, notification: Notification) {
        ensureChannel()
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_download_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_download_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "download_status"
    }
}
