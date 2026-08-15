package com.nihaltp.sbskip.ui.main.dialogs

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorDetailsDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDownloadButtonShownWhenDurationIsZeroAndFailed() {
        val item =
            DownloadQueueItem(
                id = 1L,
                url = "https://youtube.com/watch?v=123",
                title = "Test Video",
                localFileUri = "content://dummy",
                mediaType = MediaType.VIDEO,
                thumbnailUrl = null,
                durationSeconds = 0L,
                status = DownloadQueueStatus.FAILED,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
                errorMessage = "Picked file duration (0s) does not match YouTube video duration (10s)",
            )

        var downloadClicked = false

        composeTestRule.setContent {
            ErrorDetailsDialog(
                item = item,
                onDismiss = {},
                onRetryQueueItem = { _, _ -> },
                onDownload = { downloadClicked = true },
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val retryDownloadText = context.getString(R.string.retry_download)

        composeTestRule.onNodeWithText(retryDownloadText, ignoreCase = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(retryDownloadText, ignoreCase = true).performClick()
        assertTrue(downloadClicked)
    }

    @Test
    fun testDownloadButtonNotShownWhenDurationIsGreaterThanZero() {
        val item =
            DownloadQueueItem(
                id = 1L,
                url = "https://youtube.com/watch?v=123",
                title = "Test Video",
                localFileUri = "content://dummy",
                mediaType = MediaType.VIDEO,
                thumbnailUrl = null,
                durationSeconds = 120L,
                status = DownloadQueueStatus.FAILED,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
                errorMessage = "Picked file duration (120s) does not match YouTube video duration (130s)",
            )

        composeTestRule.setContent {
            ErrorDetailsDialog(
                item = item,
                onDismiss = {},
                onRetryQueueItem = { _, _ -> },
                onDownload = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val retryDownloadText = context.getString(R.string.retry_download)

        composeTestRule.onNodeWithText(retryDownloadText, ignoreCase = true).assertDoesNotExist()
    }
}
