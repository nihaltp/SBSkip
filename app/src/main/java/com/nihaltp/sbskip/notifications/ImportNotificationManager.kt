package com.nihaltp.sbskip.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nihaltp.sbskip.MainActivity
import com.nihaltp.sbskip.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silent, ongoing notifications for the folder import queue.
 *
 * Two independent notifications are used:
 *  - [NOTIFICATION_FILE_QUEUE] shows how many media files are still left in the folder that is
 *    currently being scanned.
 *  - [NOTIFICATION_FOLDER_QUEUE] shows how many folders are still queued.
 *
 * Both are posted on a low importance channel, so they never make a sound and never show up as a
 * heads-up notification. They are dismissed automatically as soon as their counter reaches zero.
 */
@Singleton
class ImportNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)

        /**
         * Updates the notification that tracks the files of the folder that is currently scanned.
         *
         * @param remainingFiles number of media files that are still left. The notification is
         * dismissed when this reaches zero.
         * @param currentFileName name of the file that is being probed right now.
         * @param totalFiles total number of media files in the folder, used for the progress bar.
         */
        fun updateFileQueueNotification(
            remainingFiles: Int,
            currentFileName: String? = null,
            totalFiles: Int = 0,
        ) {
            if (remainingFiles <= 0) {
                notificationManager.cancel(NOTIFICATION_FILE_QUEUE)
                return
            }

            val notification =
                buildNotification(
                    title = context.getString(R.string.notification_import_files_title),
                    text = context.getString(R.string.notification_import_files_remaining, remainingFiles),
                    subText = currentFileName,
                    total = totalFiles,
                    progress = (totalFiles - remainingFiles).coerceAtLeast(0),
                )
            notificationManager.notify(NOTIFICATION_FILE_QUEUE, notification)
        }

        /**
         * Updates the notification that tracks the folders that are still queued.
         *
         * @param foldersRemaining number of folders left. The notification is dismissed when this
         * reaches zero.
         */
        fun updateFolderQueueNotification(foldersRemaining: Int) {
            if (foldersRemaining <= 0) {
                notificationManager.cancel(NOTIFICATION_FOLDER_QUEUE)
                return
            }

            val notification =
                buildNotification(
                    title = context.getString(R.string.notification_import_folders_title),
                    text = context.getString(R.string.notification_import_folders_remaining, foldersRemaining),
                    subText = null,
                    total = 0,
                    progress = 0,
                )
            notificationManager.notify(NOTIFICATION_FOLDER_QUEUE, notification)
        }

        /**
         * Dismisses both import notifications, e.g. when the import queue ran empty.
         */
        fun cancelAll() {
            notificationManager.cancel(NOTIFICATION_FILE_QUEUE)
            notificationManager.cancel(NOTIFICATION_FOLDER_QUEUE)
        }

        private fun buildNotification(
            title: String,
            text: String,
            subText: String?,
            total: Int,
            progress: Int,
        ): Notification {
            ensureChannel()
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent())
                .setProgress(total, progress, total <= 0)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notification_import_cancel),
                    cancelPendingIntent(),
                )
                .build()
        }

        /**
         * Pending intent of the "Cancel scan" action. It stops the scan of the folder that is
         * currently being imported.
         */
        private fun cancelPendingIntent(): PendingIntent? {
            val intent =
                Intent(context, ImportCancelReceiver::class.java).apply {
                    action = ACTION_CANCEL_SCAN
                }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_CANCEL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun contentIntent(): PendingIntent? {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Creates the notification channel of the import notifications. It is called when the app
         * starts, so that the channel exists before the first notification is posted.
         */
        fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_import_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_import_description)
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                }
            manager.createNotificationChannel(channel)
        }

        companion object {
            /** Notification id of the "files remaining" notification. */
            const val NOTIFICATION_FILE_QUEUE = 1001

            /** Notification id of the "folders remaining" notification. */
            const val NOTIFICATION_FOLDER_QUEUE = 1002

            /** Action of the pending intent that cancels the running scan. */
            const val ACTION_CANCEL_SCAN = "com.nihaltp.sbskip.action.CANCEL_IMPORT_SCAN"

            private const val CHANNEL_ID = "sbskip_import_queue"
            private const val REQUEST_CODE_CANCEL = 1003
        }
    }
