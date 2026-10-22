package com.nihaltp.sbskip.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import com.nihaltp.sbskip.util.ImportManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
    private lateinit var ffmpegMetadataReader: FfmpegMetadataReader
    private lateinit var importManager: ImportManager
    private lateinit var application: Application
    private lateinit var viewModel: DownloadedHistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        downloadedVideoRepository = mockk(relaxed = true)
        ffmpegMetadataReader = mockk(relaxed = true)
        importManager = mockk(relaxed = true)
        application = ApplicationProvider.getApplicationContext()
        every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(emptyList())
        viewModel = DownloadedHistoryViewModel(downloadedVideoRepository, ffmpegMetadataReader, importManager, application)
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
                        thumbnailUrl = "https://img.youtube.com/vi/abc123/mqdefault.jpg",
                        durationSeconds = 180L,
                        outputPaths = listOf("/sdcard/Music/test.m4a"),
                        mediaType = "audio",
                    ),
                )
            every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(expected)

            viewModel = DownloadedHistoryViewModel(downloadedVideoRepository, ffmpegMetadataReader, importManager, application)

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
    fun `toggleSelection and toggleSelectAll work correctly`() =
        runTest(testDispatcher) {
            assertEquals(0, viewModel.selectedCount.value)
            assertEquals(emptySet<Long>(), viewModel.selectedIds.value)

            viewModel.toggleSelection(1L)
            assertEquals(1, viewModel.selectedCount.value)
            assertEquals(setOf(1L), viewModel.selectedIds.value)

            viewModel.toggleSelectAll(listOf(1L, 2L, 3L))
            assertEquals(3, viewModel.selectedCount.value)
            assertEquals(setOf(1L, 2L, 3L), viewModel.selectedIds.value)

            viewModel.toggleSelectAll(listOf(1L, 2L, 3L))
            assertEquals(0, viewModel.selectedCount.value)
            assertEquals(emptySet<Long>(), viewModel.selectedIds.value)
        }

    @Test
    fun `deleteSelectedVideos deletes all selected items and clears selection`() =
        runTest(testDispatcher) {
            coEvery { downloadedVideoRepository.deleteById(any()) } just Runs

            viewModel.toggleSelectAll(listOf(1L, 2L))
            assertEquals(2, viewModel.selectedCount.value)

            viewModel.deleteSelectedVideos()
            runCurrent()

            coVerify(exactly = 1) { downloadedVideoRepository.deleteById(1L) }
            coVerify(exactly = 1) { downloadedVideoRepository.deleteById(2L) }
            assertEquals(0, viewModel.selectedCount.value)
            assertEquals(emptySet<Long>(), viewModel.selectedIds.value)
        }

    @Test
    fun `clearHistory delegates to repository`() =
        runTest(testDispatcher) {
            coEvery { downloadedVideoRepository.deleteAll() } just Runs

            viewModel.clearHistory()
            runCurrent()

            coVerify(exactly = 1) { downloadedVideoRepository.deleteAll() }
        }

    @Test
    fun `dismissCompletedItem delegates to import manager`() =
        runTest(testDispatcher) {
            viewModel.dismissCompletedItem(7L)

            verify(exactly = 1) { importManager.dismissCompletedItem(7L) }
        }

    @Test
    fun `removeQueueItem delegates to import manager`() =
        runTest(testDispatcher) {
            viewModel.removeQueueItem(3L)

            verify(exactly = 1) { importManager.removeQueueItem(3L) }
        }
}
