package com.nihaltp.sbskip.ui.history

import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [DownloadedHistoryViewModel].
 *
 * Covers:
 *  - feat: add downloaded videos tracker (issue #tracker)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadedHistoryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var viewModel: DownloadedHistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        downloadedVideoRepository = mockk(relaxed = true)
        every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(emptyList())
        viewModel = DownloadedHistoryViewModel(downloadedVideoRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `videos emits initial empty list`() =
        runTest(testDispatcher) {
            assertEquals(emptyList<DownloadedVideoEntity>(), viewModel.videos.first())
        }

    @Test
    fun `videos emits repository videos`() =
        runTest(testDispatcher) {
            val expected =
                listOf(
                    DownloadedVideoEntity(
                        id = 1L,
                        videoId = "abc123",
                        url = "https://www.youtube.com/watch?v=abc123",
                        title = "Test Video",
                        thumbnailUrl = null,
                        durationSeconds = 180L,
                        downloadedAtEpochMillis = 1000L,
                        outputPath = "/sdcard/Music/test.m4a",
                        mediaType = "audio",
                    ),
                )
            every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(expected)

            viewModel = DownloadedHistoryViewModel(downloadedVideoRepository)

            // first() returns initialValue (emptyList); skip it to get the upstream emission.
            assertEquals(expected, viewModel.videos.first { it.isNotEmpty() })
        }

    @Test
    fun `deleteVideo delegates to repository`() =
        runTest(testDispatcher) {
            val itemId = 5L

            coEvery { downloadedVideoRepository.deleteById(any()) } just Runs

            viewModel.deleteVideo(itemId)
            runCurrent()

            coVerify(exactly = 1) { downloadedVideoRepository.deleteById(id = itemId) }
        }

    @Test
    fun `clearHistory delegates to repository`() =
        runTest(testDispatcher) {
            coEvery { downloadedVideoRepository.deleteAll() } just Runs

            viewModel.clearHistory()
            runCurrent()

            coVerify(exactly = 1) { downloadedVideoRepository.deleteAll() }
        }
}
