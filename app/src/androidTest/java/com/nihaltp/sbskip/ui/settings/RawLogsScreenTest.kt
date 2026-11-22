package com.nihaltp.sbskip.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.util.AppLogger
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RawLogsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var testLogFile: File
    private var logRefreshKey = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_logs")
        tempDir.mkdirs()
        testLogFile = File(tempDir, "sbskip.log")
        AppLogger.initForTesting(testLogFile)
        AppLogger.isVerboseLoggingEnabled = true
        AppLogger.clearLogs()
    }

    @After
    fun tearDown() {
        AppLogger.clearLogs()
    }

    @Test
    fun testRawLogsScreen_showsEmptyState() {
        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.logs_empty))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
    }

    @Test
    fun testRawLogsScreen_showsLogs() {
        AppLogger.queue("Test queue log message")
        AppLogger.worker("Test worker log message")

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        composeTestRule
            .onNodeWithText("Test queue log message")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Test worker log message")
            .assertIsDisplayed()
    }

    @Test
    fun testRawLogsScreen_copyButton() {
        AppLogger.queue("Test log to copy")

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        val copyContentDescription = context.getString(R.string.copy)
        composeTestRule
            .onNodeWithContentDescription(copyContentDescription)
            .assertIsDisplayed()
            .performClick()

        // Toast should appear - we can't easily test Toast, but we can verify no crash
        composeTestRule.waitForIdle()
    }

    @Test
    fun testRawLogsScreen_shareButton() {
        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        composeTestRule
            .onNodeWithContentDescription("Share")
            .assertIsDisplayed()
            .performClick()

        // Share intent chooser should appear - just verify no crash
        composeTestRule.waitForIdle()
    }

    @Test
    fun testRawLogsScreen_clearButton() {
        AppLogger.queue("Test log to clear")
        assertTrue(AppLogger.getLogs().contains("Test log to clear"))

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        val clearContentDescription = context.getString(R.string.clear)
        composeTestRule
            .onNodeWithContentDescription(clearContentDescription)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(context.getString(R.string.logs_empty))
            .assertIsDisplayed()

        assertTrue(AppLogger.getLogs().isEmpty())
    }

    @Test
    fun testRawLogsScreen_downloadButtonLaunchesDocumentPicker() {
        AppLogger.queue("Test log for download")

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        // The download button has contentDescription = logs_export_button
        val downloadContentDescription = context.getString(R.string.logs_export_button)
        composeTestRule
            .onNodeWithContentDescription(downloadContentDescription)
            .assertIsDisplayed()

        // Clicking the download button should not crash
        // (We can't fully test the document picker without Espresso/UiAutomator)
        composeTestRule
            .onNodeWithContentDescription(downloadContentDescription)
            .performClick()

        composeTestRule.waitForIdle()
    }

    @Test
    fun testRawLogsScreen_downloadButtonContentDescription() {
        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        val downloadContentDescription = context.getString(R.string.logs_export_button)
        composeTestRule
            .onNodeWithContentDescription(downloadContentDescription)
            .assertIsDisplayed()
    }

    @Test
    fun testRawLogsScreen_title() {
        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.logs_title))
            .assertIsDisplayed()
    }

    @Test
    fun testRawLogsScreen_backNavigation() {
        var navigateBackCalled = false

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = { navigateBackCalled = true })
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(navigateBackCalled)
    }

    @Test
    fun testRawLogsScreen_scrollable() {
        // Add many logs to make the screen scrollable
        for (i in 1..50) {
            AppLogger.queue("Log line number $i")
        }

        composeTestRule.setContent {
            RawLogsScreen(onNavigateBack = {})
        }

        // Verify we can find the last log line (should be scrollable)
        composeTestRule
            .onNodeWithText("Log line number 50")
            .assertIsDisplayed()
    }
}
