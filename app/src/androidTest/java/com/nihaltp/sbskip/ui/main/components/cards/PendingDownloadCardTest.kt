package com.nihaltp.sbskip.ui.main.components.cards

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DetectedFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingDownloadCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `shows auto detect button when not detecting and no file detected`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val autoDetectText = context.getString(R.string.auto_detect_recent_download)

        composeTestRule.onNodeWithText(autoDetectText).assertIsDisplayed()
    }

    @Test
    fun `shows pick file manually button when no file detected`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val pickFileText = context.getString(R.string.pick_file_manually_button)

        composeTestRule.onNodeWithText(pickFileText).assertIsDisplayed()
    }

    @Test
    fun `shows cancel button when no file detected`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val cancelText = context.getString(R.string.cancel)

        composeTestRule.onNodeWithText(cancelText).assertIsDisplayed()
    }

    @Test
    fun `shows detecting state when isDetecting is true`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = true,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val detectingText = context.getString(R.string.detecting_recent_download)

        composeTestRule.onNodeWithText(detectingText).assertIsDisplayed()
    }

    @Test
    fun `shows countdown when estimatedReadyAtEpochMillis is in future`() {
        val futureTime = System.currentTimeMillis() + 60_000 // 1 minute in future

        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = futureTime,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        // The countdown format varies based on current time, just verify it shows the waiting text pattern
        composeTestRule.onNodeWithText(context.getString(R.string.waiting_for_newpipe_download, "")).assertIsDisplayed()
    }

    @Test
    fun `shows search now and cancel when counting down`() {
        val futureTime = System.currentTimeMillis() + 60_000

        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = futureTime,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val searchNowText = context.getString(R.string.search_now_button)
        val cancelText = context.getString(R.string.cancel)

        composeTestRule.onNodeWithText(searchNowText).assertIsDisplayed()
        composeTestRule.onNodeWithText(cancelText).assertIsDisplayed()
    }

    @Test
    fun `shows detected file section when file is detected`() {
        val detectedFile =
            DetectedFile(
                uri = "content://test/uri",
                score = 95,
                relativePath = "test_video.mp4",
                folderUri = null,
            )

        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = "test_video.mp4",
                detectedFile = detectedFile,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val foundText = context.getString(R.string.found_matching_file, 95)
        val confirmText = context.getString(R.string.clean_this_file_button)
        val autoDetectAgainText = context.getString(R.string.auto_detect_again_button)
        val pickFileText = context.getString(R.string.pick_file_manually_button)
        val cancelText = context.getString(R.string.cancel)

        composeTestRule.onNodeWithText(foundText).assertIsDisplayed()
        composeTestRule.onNodeWithText(confirmText).assertIsDisplayed()
        composeTestRule.onNodeWithText(autoDetectAgainText).assertIsDisplayed()
        composeTestRule.onNodeWithText(pickFileText).assertIsDisplayed()
        composeTestRule.onNodeWithText(cancelText).assertIsDisplayed()
    }

    @Test
    fun `shows pending download title in card`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "My Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("My Test Video Title").assertIsDisplayed()
    }

    @Test
    fun `shows waiting for download title`() {
        composeTestRule.setContent {
            PendingDownloadCard(
                pendingDownloadTitle = "Test Video Title",
                thumbnailUrl = "https://img.youtube.com/vi/test/mqdefault.jpg",
                detectedFileName = null,
                detectedFile = null,
                isDetecting = false,
                estimatedReadyAtEpochMillis = null,
                onAutoDetect = {},
                onSearchNow = {},
                onPickFileManually = {},
                onCancel = {},
                onConfirmDetectedFile = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val waitingTitleText = context.getString(R.string.waiting_for_download_title)

        composeTestRule.onNodeWithText(waitingTitleText).assertIsDisplayed()
    }
}
