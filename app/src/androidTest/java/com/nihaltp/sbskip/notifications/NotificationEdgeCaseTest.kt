package com.nihaltp.sbskip.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationEdgeCaseTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun `download notification channel exists with correct importance`() {
        val manager = AndroidDownloadNotificationManager(context)

        val channel = context.getSystemService(Context.NOTIFICATION_SERVICE).getNotificationChannel("download_status")

        assertNotNull(channel)
        assertEquals("download_status", channel.id)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `import file queue notification channel exists with low importance`() {
        val manager = ImportNotificationManager(context)

        val channel = context.getSystemService(Context.NOTIFICATION_SERVICE).getNotificationChannel("import_file_queue")

        assertNotNull(channel)
        assertEquals("import_file_queue", channel.id)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `import folder queue notification channel exists with low importance`() {
        val manager = ImportNotificationManager(context)

        val channel = context.getSystemService(Context.NOTIFICATION_SERVICE).getNotificationChannel("import_folder_queue")

        assertNotNull(channel)
        assertEquals("import_folder_queue", channel.id)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `import file queue notification shows correct count`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(5)

        val notifications = manager.getActiveNotifications()
        val notification = notifications.firstOrNull { it.id == ImportNotificationManager.NOTIFICATION_FILE_QUEUE }

        assertNotNull(notification)
        // Verify notification text contains the count
        val notificationText = notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue("Notification should contain count", notificationText.contains("5"))
    }

    @Test
    fun `import folder queue notification shows correct count`() {
        val manager = ImportNotificationManager(context)

        manager.updateFolderQueueNotification(3)

        val notifications = manager.getActiveNotifications()
        val notification = notifications.firstOrNull { it.id == ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE }

        assertNotNull(notification)
        val notificationText = notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue("Notification should contain count", notificationText.contains("3"))
    }

    @Test
    fun `dismissing file queue notification when count is zero`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        var notification = getFileQueueNotification(manager)
        assertNotNull(notification)

        manager.updateFileQueueNotification(0)
        notification = getFileQueueNotification(manager)
        assertNull("Notification should be dismissed when count is 0", notification)
    }

    @Test
    fun `dismissing folder queue notification when count is zero`() {
        val manager = ImportNotificationManager(context)

        manager.updateFolderQueueNotification(1)
        var notification = getFolderQueueNotification(manager)
        assertNotNull(notification)

        manager.updateFolderQueueNotification(0)
        notification = getFolderQueueNotification(manager)
        assertNull("Notification should be dismissed when count is 0", notification)
    }

    @Test
    fun `cancelAll dismisses both notifications`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(2)
        manager.updateFolderQueueNotification(1)

        assertNotNull(getFileQueueNotification(manager))
        assertNotNull(getFolderQueueNotification(manager))

        manager.cancelAll()

        assertNull(getFileQueueNotification(manager))
        assertNull(getFolderQueueNotification(manager))
    }

    @Test
    fun `file queue notification has correct channel ID`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        val notification = getFileQueueNotification(manager)

        assertNotNull(notification)
        assertEquals("import_file_queue", notification.notification.channelId)
    }

    @Test
    fun `folder queue notification has correct channel ID`() {
        val manager = ImportNotificationManager(context)

        manager.updateFolderQueueNotification(1)
        val notification = getFolderQueueNotification(manager)

        assertNotNull(notification)
        assertEquals("import_folder_queue", notification.notification.channelId)
    }

    @Test
    fun `notification uses correct small icon`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        val notification = getFileQueueNotification(manager)

        assertNotNull(notification)
        assertTrue("Should have a small icon", notification.notification.smallIcon != 0)
    }

    @Test
    fun `notification is not ongoing`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        val notification = getFileQueueNotification(manager)

        assertNotNull(notification)
        assertFalse("Should not be ongoing", (notification.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun `notification is not auto-cancel`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        val notification = getFileQueueNotification(manager)

        assertNotNull(notification)
        assertFalse("Should not be auto-cancel", (notification.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0)
    }

    @Test
    fun `multiple updates to file queue notification work correctly`() {
        val manager = ImportNotificationManager(context)

        manager.updateFileQueueNotification(1)
        var notification = getFileQueueNotification(manager)
        assertNotNull(notification)

        manager.updateFileQueueNotification(5)
        notification = getFileQueueNotification(manager)
        assertNotNull(notification)
        val text = notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(text.contains("5"))

        manager.updateFileQueueNotification(0)
        notification = getFileQueueNotification(manager)
        assertNull(notification)
    }

    @Test
    fun `notifications survive rapid updates`() {
        val manager = ImportNotificationManager(context)

        // Rapidly update counts
        repeat(10) { i ->
            manager.updateFileQueueNotification(i)
        }

        val notification = getFileQueueNotification(manager)
        assertNotNull(notification)
        val text = notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(text.contains("9"))

        repeat(5) { i ->
            manager.updateFolderQueueNotification(i)
        }

        val folderNotification = getFolderQueueNotification(manager)
        assertNotNull(folderNotification)
    }

    private fun getFileQueueNotification(manager: ImportNotificationManager): StatusBarNotification? {
        return manager.getActiveNotifications().firstOrNull { it.id == ImportNotificationManager.NOTIFICATION_FILE_QUEUE }
    }

    private fun getFolderQueueNotification(manager: ImportNotificationManager): StatusBarNotification? {
        return manager.getActiveNotifications().firstOrNull { it.id == ImportNotificationManager.NOTIFICATION_FOLDER_QUEUE }
    }
}
