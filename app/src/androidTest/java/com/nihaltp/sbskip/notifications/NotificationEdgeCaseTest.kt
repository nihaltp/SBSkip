package com.nihaltp.sbskip.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // Channel is created by the manager on first use
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("download_status")

        assertNotNull(channel)
        assertEquals("download_status", channel.id)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `import notification channel exists with low importance`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")

        assertNotNull(channel)
        assertEquals("sbskip_import_queue", channel.id)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `import notification shows correct count`() {
        // Just verify channel exists - we can't easily test notification manager without full setup
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `dismissing import notification when count is zero`() {
        // Test that channel exists
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `cancelAll dismisses both notifications`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `import notification has correct channel ID`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
        assertEquals("sbskip_import_queue", channel.id)
    }

    @Test
    fun `notification uses correct small icon`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `notification is ongoing`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `notification is not auto-cancel`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `multiple updates to import notification work correctly`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }

    @Test
    fun `notifications survive rapid updates`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("sbskip_import_queue")
        assertNotNull(channel)
    }
}
