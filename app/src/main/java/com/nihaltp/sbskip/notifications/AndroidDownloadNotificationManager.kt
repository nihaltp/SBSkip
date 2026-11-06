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
import com.nihaltp.sbskip.data.repository.QueueRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val queueRepository: QueueRepository,
    ) : DownloadNotificationManager {
        private val scope = CoroutineScope(Dispatchers.Default + Job())
        private var completionCount = 0
        private var failureCount = 0
        private var queuedCount = 0

        init {
            observeQueue()
        }

        private fun observeQueue() {
            scope.launch {
                queueRepository.observeQueue().collect { items ->
                    queuedCount = items.count { it.status == com.nihaltp.sbskip.model.DownloadQueueStatus.QUEUED }
                }
            }
        }

        override fun createForegroundInfo(
            id: Long,
            title: String,
            message: String,
        ): ForegroundInfo {
            val notification =
                buildActiveNotification(
                    title = title,
                    progress = 0,
                    message = message,
                )
            // Use DATA_SYNC foreground service type for uploads/downloads so apps
            // targeting SDK 31+ don't get InvalidForegroundServiceTypeException.
            val fgType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            return ForegroundInfo(id.toInt(), notification, fgType)
        }

        override fun showActive(
            id: Long,
            title: String,
            progress: Int,
            message: String,
        ) {
            notify(
                id = ACTIVE_ID,
                notification =
                    buildActiveNotification(
                        title = title,
                        progress = progress.coerceIn(0, 100),
                        message = message,
                    ),
            )
        }

        override fun showCompletion(
            id: Long,
            title: String,
        ) {
            completionCount++
            notify(
                id = COMPLETION_ID,
                notification =
                    buildSummaryNotification(
                        title = context.getString(R.string.notification_queue_completed_title),
                        message = context.getString(R.string.notification_queue_completed_message, completionCount),
                    ),
            )
        }

        override fun showFailure(
            id: Long,
            title: String,
            errorMessage: String,
        ) {
            failureCount++
            notify(
                id = FAILURE_ID,
                notification =
                    buildSummaryNotification(
                        title = context.getString(R.string.notification_queue_failed_title),
                        message = context.getString(R.string.notification_queue_failed_message, failureCount),
                    ),
            )
        }

        override fun showCancelled(
            id: Long,
            title: String,
        ) {
            // Use failure notification for cancelled items as well
            failureCount++
            notify(
                id = FAILURE_ID,
                notification =
                    buildSummaryNotification(
                        title = context.getString(R.string.notification_queue_failed_title),
                        message = context.getString(R.string.notification_queue_failed_message, failureCount),
                    ),
            )
        }

        private fun buildActiveNotification(
            title: String,
            progress: Int,
            message: String,
        ): Notification {
            ensureChannel()
            val remaining = queuedCount
            val contentText =
                if (remaining > 0) {
                    context.getString(R.string.notification_queue_active_text, title, progress, remaining)
                } else {
                    message
                }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.notification_queue_active_title))
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, progress, progress <= 0)
                .build()
        }

        private fun buildSummaryNotification(
            title: String,
            message: String,
        ): Notification {
            ensureChannel()
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(0, 0, false)
                .build()
        }

        private fun notify(
            id: Int,
            notification: Notification,
        ) {
            ensureChannel()
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        /**
         * Creates the notification channel of the download notifications. It is called when the app
         * starts, so that the channel exists before the first notification is posted.
         */
        override fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return

            val channel =
                NotificationChannel(
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
            const val ACTIVE_ID = 2000
            const val COMPLETION_ID = 2001
            const val FAILURE_ID = 2002
        }
    }
