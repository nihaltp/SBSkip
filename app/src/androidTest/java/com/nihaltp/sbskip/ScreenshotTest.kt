package com.nihaltp.sbskip

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.di.TestEntryPoint
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    private lateinit var settingsRepository: SettingsRepository
    private val videoTitle = "KULASTHREE (Official Video) - ThirumaLi x ThudWiser _ Def Jam India.m4a"
    var screenshotCounter = 1

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entryPoint = dagger.hilt.EntryPoints.get(context, TestEntryPoint::class.java)
        val database = entryPoint.database()
        settingsRepository = entryPoint.settingsRepository()

        // Seed clean database and queue list state before drawing tests using Hilt's database instance
        runBlocking {
            database.clearAllTables()

            val now = System.currentTimeMillis()
            val entity = DownloadQueueEntity(
                id = 1,
                url = "https://youtube.com/watch?v=SyluwH2ycDA&si=OaqUlygAss9wEXdq",
                title = videoTitle,
                localFileUri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FYT%2FKULASTHREE%20(Official%20Video)%20-%20ThirumaLi%20x%20ThudWiser%20_%20Def%20Jam%20India.m4a",
                mediaType = "AUDIO",
                thumbnailUrl = "https://img.youtube.com/vi/SyluwH2ycDA/mqdefault.jpg",
                durationSeconds = 295,
                status = DownloadQueueStatus.COMPLETED,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                errorMessage = null,
                outputPath = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FYT%2FKULASTHREE%20(Official%20Video)%20-%20ThirumaLi%20x%20ThudWiser%20_%20Def%20Jam%20India.m4a",
            )
            database.downloadQueueDao().insert(entity)
        }
    }

    @Test
    fun captureScreenshots() {
        // Hide system status bar and navigation bar dynamically to get only the app screen
        composeTestRule.activity.runOnUiThread {
            val window = composeTestRule.activity.window
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        composeTestRule.waitForIdle()

        // 1. LIGHT Theme - Main Screen
        runBlocking {
            settingsRepository.update { it.copy(themeMode = ThemeMode.LIGHT) }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(2400) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // 2. LIGHT Theme - Media Details
        composeTestRule.waitUntil(10000) {
            try {
                composeTestRule.onNodeWithText(videoTitle).assertExists()
                true
            } catch (e: Throwable) {
                false
            }
        }
        composeTestRule.onNodeWithText(videoTitle).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(1200) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // Close details
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        // 3. LIGHT Theme - Settings Screen
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(1200) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // Go Back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        // 4. DARK Theme - Main Screen
        runBlocking {
            settingsRepository.update { it.copy(themeMode = ThemeMode.DARK) }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(1200) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // 5. DARK Theme - Media Details
        composeTestRule.waitUntil(10000) {
            try {
                composeTestRule.onNodeWithText(videoTitle).assertExists()
                true
            } catch (e: Throwable) {
                false
            }
        }
        composeTestRule.onNodeWithText(videoTitle).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(1200) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // Close details
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        // 6. DARK Theme - Settings Screen
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(1200) // Wait between screens/actions for fluid settled states
        Screengrab.screenshot(screenshotCounter.toString())
        screenshotCounter++

        // Copy screenshots to external files directory for reliable pulling on Windows
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val internalDir = java.io.File(context.filesDir.parentFile, "app_screengrab")
        val externalDir = context.getExternalFilesDir(null)
        if (internalDir.exists() && externalDir != null) {
            internalDir.copyRecursively(java.io.File(externalDir, "app_screengrab"), overwrite = true)
        }
    }
}
