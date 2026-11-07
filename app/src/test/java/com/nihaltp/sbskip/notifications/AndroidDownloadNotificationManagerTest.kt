package com.nihaltp.sbskip.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the notification channel of the download notifications.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidDownloadNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var manager: AndroidDownloadNotificationManager
    private lateinit var notificationManager: NotificationManager
    private val queueRepository: QueueRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        every { queueRepository.observeQueue() } returns emptyFlow()
        every { settingsRepository.settings } returns flowOf(AppSettings())
        manager = AndroidDownloadNotificationManager(context, queueRepository, settingsRepository)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `ensureChannel creates the download channel`() {
        manager.ensureChannel()

        val channel = notificationManager.getNotificationChannel("download_status")
        assertNotNull("Expected the download channel to exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
    }

    @Test
    fun `ensureChannel can be called more than once`() {
        manager.ensureChannel()
        manager.ensureChannel()

        assertNotNull(notificationManager.getNotificationChannel("download_status"))
    }
}
