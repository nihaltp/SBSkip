package com.nihaltp.sbskip.notifications

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the silent import queue notifications.
 */
@RunWith(RobolectricTestRunner::class)
class ImportNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var manager: ImportNotificationManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context = appContext
        manager = ImportNotificationManager(context)
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    private fun activeNotification(id: Int): StatusBarNotification? {
        val notifications = NotificationManagerCompat.from(context).activeNotifications
        return notifications.firstOrNull { it.id == id }
    }

    @Test
    fun `file queue notification is posted on a silent low importance channel`() {
        manager.updateFileQueueNotification(remainingFiles = 3, currentFileName = "clip.mp4", totalFiles = 5)

        val notification = activeNotification(ImportNotificationManager.NOTIFICATION_FILE_QUEUE)
        assertNotNull("Expected a file queue notification", notification)
        val channelId = notification!!.notification.channelId
        assertEquals("sbskip_import_queue", channelId)
        val channel = notificationManager.getNotificationChannel(channelId)
        assertNotNull("Expected the import channel to exist", channel)
        assertEquals(
            "The import channel must stay low importance so no sound or heads-up is shown",
            NotificationManager.IMPORTANCE_LOW,
            channel!!.importance,
        )
    }

    @Test
    fun `file queue notification is dismissed when no files remain`() {
        manager.updateFileQueueNotification(remainingFiles = 3, currentFileName = "clip.mp4", totalFiles = 3)
        assertNotNull(activeNotification(ImportNotificationManager.NOTIFICATION_FILE_QUEUE))

        manager.updateFileQueueNotification(remainingFiles = 0)

        assertNull(activeNotification(ImportNotificationManager.NOTIFICATION_FILE_QUEUE))
    }

    @Test
    fun `folder queue notification is posted while folders remain`() {
        manager.updateFolderQueueNotification(foldersRemaining = 4)

        assertNotNull(activeNotification(ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE))
    }

    @Test
    fun `folder queue notification is dismissed when no folders remain`() {
        manager.updateFolderQueueNotification(foldersRemaining = 4)
        assertNotNull(activeNotification(ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE))

        manager.updateFolderQueueNotification(foldersRemaining = 0)

        assertNull(activeNotification(ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE))
    }

    @Test
    fun `cancelAll dismisses both notifications`() {
        manager.updateFileQueueNotification(remainingFiles = 2, currentFileName = "clip.mp4", totalFiles = 2)
        manager.updateFolderQueueNotification(foldersRemaining = 2)

        manager.cancelAll()

        assertNull(activeNotification(ImportNotificationManager.NOTIFICATION_FILE_QUEUE))
        assertNull(activeNotification(ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE))
    }

    @Test
    fun `file and folder notifications use different ids`() {
        assertEquals(1001, ImportNotificationManager.NOTIFICATION_FILE_QUEUE)
        assertEquals(1002, ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE)
    }
}
