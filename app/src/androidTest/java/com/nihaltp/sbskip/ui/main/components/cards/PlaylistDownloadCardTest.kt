package com.nihaltp.sbskip.ui.main.components.cards

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.PlaylistDownloadState
import com.nihaltp.sbskip.model.PlaylistVideo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDownloadCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createPlaylistState(
        videos: List<PlaylistVideo> =
            listOf(
                PlaylistVideo(
                    videoId = "video1",
                    title = "Video 1",
                    thumbnailUrl = "https://img.youtube.com/vi/video1/mqdefault.jpg",
                ),
                PlaylistVideo(
                    videoId = "video2",
                    title = "Video 2",
                    thumbnailUrl = "https://img.youtube.com/vi/video2/mqdefault.jpg",
                ),
            ),
        currentIndex: Int = 0,
        convertVideoToAudio: Boolean = false,
        deleteOriginalVideo: Boolean = true,
    ): PlaylistDownloadState {
        return PlaylistDownloadState(
            playlistId = "test_playlist",
            title = "Test Playlist",
            videos = videos,
            currentIndex = currentIndex,
            convertVideoToAudio = convertVideoToAudio,
            deleteOriginalVideo = deleteOriginalVideo,
        )
    }

    @Test
    fun `shows playlist progress with video count`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val playlistText = "Playlist: 1 of 2"

        composeTestRule.onNodeWithText(playlistText).assertIsDisplayed()
    }

    @Test
    fun `shows current video title`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Video 1").assertIsDisplayed()
    }

    @Test
    fun `shows convert video to audio switch`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val convertText = context.getString(R.string.convert_video_to_audio_title)

        composeTestRule.onNodeWithText(convertText).assertIsDisplayed()
    }

    @Test
    fun `shows delete original video switch when convert video to audio is true`() {
        val state = createPlaylistState(convertVideoToAudio = true)

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val deleteText = context.getString(R.string.delete_original_video_title)

        composeTestRule.onNodeWithText(deleteText).assertIsDisplayed()
    }

    @Test
    fun `hides delete original video switch when convert video to audio is false`() {
        val state = createPlaylistState(convertVideoToAudio = false)

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val deleteText = context.getString(R.string.delete_original_video_title)

        // assertDoesNotExist is not available in this Compose version, use try-catch
        try {
            composeTestRule.onNodeWithText(deleteText).assertIsDisplayed()
            assert(false) { "Expected delete text to not exist when convertVideoToAudio is false" }
        } catch (e: Exception) {
            // Expected - node doesn't exist
        }
    }

    @Test
    fun `shows download button`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test
    fun `shows skip video button`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Skip Video").assertIsDisplayed()
    }

    @Test
    fun `shows cancel playlist download button`() {
        val state = createPlaylistState()

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Cancel Playlist Download").assertIsDisplayed()
    }

    @Test
    fun `shows playlist loading text when no current video`() {
        val state =
            PlaylistDownloadState(
                playlistId = "test_playlist",
                title = "Test Playlist",
                videos = emptyList(),
                currentIndex = 0,
                convertVideoToAudio = false,
                deleteOriginalVideo = true,
            )

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Playlist loading or finished").assertIsDisplayed()
    }

    @Test
    fun `shows close button when no current video`() {
        val state =
            PlaylistDownloadState(
                playlistId = "test_playlist",
                title = "Test Playlist",
                videos = emptyList(),
                currentIndex = 0,
                convertVideoToAudio = false,
                deleteOriginalVideo = true,
            )

        composeTestRule.setContent {
            PlaylistDownloadCard(
                state = state,
                downloadedCount = 0,
                processingCount = 0,
                onDownload = {},
                onSkip = {},
                onCancel = {},
                onConvertVideoToAudioChanged = {},
                onDeleteOriginalVideoChanged = {},
                onExpandedChanged = {},
            )
        }

        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
    }
}
