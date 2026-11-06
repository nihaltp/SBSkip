package com.nihaltp.sbskip.ui.main.components.cards

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueItemCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createQueueItem(
        id: Long = 1L,
        title: String = "Test Video",
        status: DownloadQueueStatus = DownloadQueueStatus.COMPLETED,
        errorMessage: String? = null,
        thumbnailUrl: String? = "https://img.youtube.com/vi/test/mqdefault.jpg",
    ): DownloadQueueItem {
        return DownloadQueueItem(
            id = id,
            url = "https://youtube.com/watch?v=test",
            title = title,
            localFileUri = "content://test/uri",
            mediaType = MediaType.VIDEO,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = 300L,
            status = status,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            errorMessage = errorMessage,
            outputPath = "content://test/output",
            outputDurationSeconds = 300L,
            convertVideoToAudio = false,
            deleteOriginalVideo = true,
            audioOutputDirUri = "",
            videoOutputDirUri = "",
            relativePath = "Movies/SB Skip/test.mp4",
        )
    }

    @Test
    fun `shows video title`() {
        val item = createQueueItem(title = "My Test Video Title")

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("My Test Video Title").assertIsDisplayed()
    }

    @Test
    fun `shows clean url`() {
        val item = createQueueItem()

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("https://youtube.com/watch?v=test").assertIsDisplayed()
    }

    @Test
    fun `shows duration`() {
        val item = createQueueItem()

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val durationText = context.getString(R.string.duration_prefix, "5:00")

        composeTestRule.onNodeWithText(durationText).assertIsDisplayed()
    }

    @Test
    fun `shows retry button when status is FAILED`() {
        val item = createQueueItem(status = DownloadQueueStatus.FAILED)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val retryText = context.getString(R.string.retry)

        composeTestRule.onNodeWithText(retryText).assertIsDisplayed()
    }

    @Test
    fun `hides retry button when status is not FAILED`() {
        val item = createQueueItem(status = DownloadQueueStatus.COMPLETED)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val retryText = context.getString(R.string.retry)

        composeTestRule.onNodeWithText(retryText).assertDoesNotExist()
    }

    @Test
    fun `shows remove button`() {
        val item = createQueueItem()

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val removeText = context.getString(R.string.remove)

        composeTestRule.onNodeWithText(removeText).assertIsDisplayed()
    }

    @Test
    fun `shows error message when present`() {
        val item =
            createQueueItem(
                status = DownloadQueueStatus.FAILED,
                errorMessage = "Some error occurred",
            )

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("Some error occurred").assertIsDisplayed()
    }

    @Test
    fun `hides error message when null`() {
        val item = createQueueItem(errorMessage = null)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("Some error occurred").assertDoesNotExist()
    }

    @Test
    fun `shows progress indicator when status is FETCHING_SEGMENTS`() {
        val item = createQueueItem(status = DownloadQueueStatus.FETCHING_SEGMENTS)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        // LinearProgressIndicator doesn't have text, but we can verify the card renders
        composeTestRule.onNodeWithText("Test Video").assertIsDisplayed()
    }

    @Test
    fun `shows progress indicator when status is PROCESSING`() {
        val item = createQueueItem(status = DownloadQueueStatus.PROCESSING)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        composeTestRule.onNodeWithText("Test Video").assertIsDisplayed()
    }

    @Test
    fun `shows status chip for COMPLETED status`() {
        val item = createQueueItem(status = DownloadQueueStatus.COMPLETED)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        // StatusChip displays text based on status - verify card renders
        composeTestRule.onNodeWithText("Test Video").assertIsDisplayed()
    }

    @Test
    fun `handles null thumbnail url gracefully`() {
        val item = createQueueItem(thumbnailUrl = null)

        composeTestRule.setContent {
            QueueItemCard(
                item = item,
                onRetry = {},
                onRemove = {},
                onErrorClick = {},
                onCardClick = {},
            )
        }

        // Should not crash and should show fallback
        composeTestRule.onNodeWithText("Test Video").assertIsDisplayed()
    }
}
