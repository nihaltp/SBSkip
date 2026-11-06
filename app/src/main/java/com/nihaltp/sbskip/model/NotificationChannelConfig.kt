package com.nihaltp.sbskip.model

import com.nihaltp.sbskip.R

/**
 * Centralized definition of all notification channels and their notification IDs.
 * Single source of truth for channel metadata.
 */
data class NotificationChannelConfig(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val importance: Int,
    val notificationIds: List<NotificationIdConfig>,
)

data class NotificationIdConfig(
    val id: Int,
    val nameRes: Int,
    val descriptionRes: Int,
)

object NotificationChannels {
    // Channel IDs must match the CHANNEL_ID constants in the notification managers
    val ALL =
        listOf(
            NotificationChannelConfig(
                id = "sbskip_import_queue",
                nameRes = R.string.notification_channel_import_name,
                descriptionRes = R.string.notification_channel_import_description,
                importance = android.app.NotificationManager.IMPORTANCE_LOW,
                notificationIds =
                    listOf(
                        NotificationIdConfig(
                            id = 1001,
                            nameRes = R.string.notification_import_files_title,
                            descriptionRes = R.string.notification_import_files_desc,
                        ),
                        NotificationIdConfig(
                            id = 1002,
                            nameRes = R.string.notification_import_folders_title,
                            descriptionRes = R.string.notification_import_folders_desc,
                        ),
                    ),
            ),
            NotificationChannelConfig(
                id = "download_status",
                nameRes = R.string.notification_channel_download_name,
                descriptionRes = R.string.notification_channel_download_description,
                importance = android.app.NotificationManager.IMPORTANCE_LOW,
                notificationIds =
                    listOf(
                        NotificationIdConfig(
                            id = 2000,
                            nameRes = R.string.notification_active_download_title,
                            descriptionRes = R.string.notification_active_download_desc,
                        ),
                        NotificationIdConfig(
                            id = 2001,
                            nameRes = R.string.notification_completed_title,
                            descriptionRes = R.string.notification_completed_desc,
                        ),
                        NotificationIdConfig(
                            id = 2002,
                            nameRes = R.string.notification_failed_title,
                            descriptionRes = R.string.notification_failed_desc,
                        ),
                    ),
            ),
        )

    fun getById(id: String): NotificationChannelConfig? = ALL.find { it.id == id }
}
