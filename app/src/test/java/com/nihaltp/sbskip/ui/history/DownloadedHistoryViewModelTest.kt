package com.nihaltp.sbskip.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.ExportData
import com.nihaltp.sbskip.model.ExportItem
import com.nihaltp.sbskip.model.ImportResult
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.SealDownloadHistoryEntry
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.ImportManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertTrue
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
    private lateinit var importManager: ImportManager
    private lateinit var application: Application
    private lateinit var viewModel: DownloadedHistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        downloadedVideoRepository = mockk(relaxed = true)
        importManager = mockk(relaxed = true)
        application = ApplicationProvider.getApplicationContext()
        every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(emptyList())
        viewModel =
            DownloadedHistoryViewModel(
                downloadedVideoRepository = downloadedVideoRepository,
                importManager = importManager,
                application = application,
            )
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

            viewModel =
                DownloadedHistoryViewModel(
                    downloadedVideoRepository = downloadedVideoRepository,
                    importManager = importManager,
                    application = application,
                )

            // first() returns initialValue (emptyList); skip it to get the upstream emission.
            assertEquals(expected, viewModel.videos.first { it.isNotEmpty() })
        }

    @Test
    fun `filteredVideos shows all videos for blank query`() =
        runTest(testDispatcher) {
            val items =
                listOf(
                    DownloadedVideoEntity(
                        id = 1L,
                        videoId = "aaa111",
                        url = "https://www.youtube.com/watch?v=aaa111",
                        title = "Alpha Video",
                        author = "ChannelOne",
                        thumbnailUrl = "https://img.youtube.com/vi/aaa111/mqdefault.jpg",
                        durationSeconds = 60L,
                        outputPaths = listOf("/sdcard/Music/alpha.m4a"),
                        mediaType = "audio",
                    ),
                    DownloadedVideoEntity(
                        id = 2L,
                        videoId = "bbb222",
                        url = "https://www.youtube.com/watch?v=bbb222",
                        title = "Beta Video",
                        author = "ChannelTwo",
                        thumbnailUrl = "https://img.youtube.com/vi/bbb222/mqdefault.jpg",
                        durationSeconds = 90L,
                        outputPaths = listOf("/sdcard/Music/beta.m4a"),
                        mediaType = "audio",
                    ),
                )
            every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(items)

            viewModel =
                DownloadedHistoryViewModel(
                    downloadedVideoRepository = downloadedVideoRepository,
                    importManager = importManager,
                    application = application,
                )

            assertEquals(items, viewModel.filteredVideos.first { it.isNotEmpty() })
        }

    @Test
    fun `filteredVideos filters case-insensitively on title, author, url, and video id`() =
        runTest(testDispatcher) {
            val items =
                listOf(
                    DownloadedVideoEntity(
                        id = 1L,
                        videoId = "aaa111",
                        url = "https://www.youtube.com/watch?v=aaa111",
                        title = "Alpha Video",
                        author = "ChannelOne",
                        thumbnailUrl = "https://img.youtube.com/vi/aaa111/mqdefault.jpg",
                        durationSeconds = 60L,
                        outputPaths = listOf("/sdcard/Music/alpha.m4a"),
                        mediaType = "audio",
                    ),
                    DownloadedVideoEntity(
                        id = 2L,
                        videoId = "bbb222",
                        url = "https://www.youtube.com/watch?v=bbb222",
                        title = "Beta Video",
                        author = "ChannelTwo",
                        thumbnailUrl = "https://img.youtube.com/vi/bbb222/mqdefault.jpg",
                        durationSeconds = 90L,
                        outputPaths = listOf("/sdcard/Music/beta.m4a"),
                        mediaType = "audio",
                    ),
                )
            every { downloadedVideoRepository.observeDownloadedVideos } returns flowOf(items)

            viewModel =
                DownloadedHistoryViewModel(
                    downloadedVideoRepository = downloadedVideoRepository,
                    importManager = importManager,
                    application = application,
                )

            // Must see the unfiltered emission before narrowing, so the filter state is settled.
            viewModel.filteredVideos.first { it.size == items.size }

            // Title match, case-insensitive
            viewModel.setSearchQuery("alpha")
            assertEquals(listOf(items[0]), viewModel.filteredVideos.first { it == listOf(items[0]) })

            // Author match, case-insensitive, when title does not match
            viewModel.setSearchQuery("channeltwo")
            assertEquals(listOf(items[1]), viewModel.filteredVideos.first { it == listOf(items[1]) })

            // URL match
            viewModel.setSearchQuery("watch?v=AaA111")
            assertEquals(listOf(items[0]), viewModel.filteredVideos.first { it == listOf(items[0]) })

            // Extracted video id match
            viewModel.setSearchQuery("BBB222")
            assertEquals(listOf(items[1]), viewModel.filteredVideos.first { it == listOf(items[1]) })

            // No matches
            viewModel.setSearchQuery("zeta")
            assertTrue(viewModel.filteredVideos.first { it.isEmpty() }.isEmpty())
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

    @Test
    fun `import plain video ids inserts videos into the downloaded history`() =
        runTest(testDispatcher) {
            val inserted = slot<DownloadedVideoEntity>()
            coEvery { downloadedVideoRepository.findByVideoId(any()) } returns null
            coEvery { downloadedVideoRepository.insert(capture(inserted)) } returns 1L

            val importedCount = viewModel.importVideos(ImportResult.Success(listOf("abc123")))

            assertEquals(1, importedCount)
            assertEquals("abc123", inserted.captured.videoId)
            assertEquals(Constants.buildYouTubeWatchUrl("abc123"), inserted.captured.url)
            assertEquals("abc123", inserted.captured.title)
            assertEquals(emptyList<String>(), inserted.captured.outputPaths)
            assertEquals(MediaType.VIDEO.name, inserted.captured.mediaType)
        }

    @Test
    fun `import json backup carries metadata and local file from the download history`() =
        runTest(testDispatcher) {
            val inserted = slot<DownloadedVideoEntity>()
            coEvery { downloadedVideoRepository.findByVideoId("abc123") } returns null
            coEvery { downloadedVideoRepository.insert(capture(inserted)) } returns 1L

            val importedCount =
                viewModel.importVideos(jsonBackup("abc123", "/sdcard/Download/video.mp4"))

            assertEquals(1, importedCount)
            val entity = inserted.captured
            assertEquals("abc123", entity.videoId)
            assertEquals("https://youtu.be/abc123", entity.url)
            assertEquals("Imported title", entity.title)
            assertEquals("Imported author", entity.author)
            assertEquals("https://example.com/thumb.jpg", entity.thumbnailUrl)
            assertEquals(listOf("/sdcard/Download/video.mp4"), entity.outputPaths)
        }

    @Test
    fun `import of an already tracked video adds the new local file instead of a second row`() =
        runTest(testDispatcher) {
            val existing =
                DownloadedVideoEntity(
                    id = 1L,
                    videoId = "abc123",
                    url = Constants.buildYouTubeWatchUrl("abc123"),
                    title = "Existing title",
                    outputPaths = listOf("/sdcard/Download/old.mp4"),
                    mediaType = MediaType.VIDEO.name,
                )
            coEvery { downloadedVideoRepository.findByVideoId("abc123") } returns existing
            coEvery {
                downloadedVideoRepository.addOutputPath("abc123", "/sdcard/Download/new.mp4")
            } returns true

            val importedCount =
                viewModel.importVideos(jsonBackup("abc123", "/sdcard/Download/new.mp4"))

            assertEquals(0, importedCount)
            coVerify(exactly = 0) { downloadedVideoRepository.insert(any()) }
            coVerify(exactly = 1) {
                downloadedVideoRepository.addOutputPath("abc123", "/sdcard/Download/new.mp4")
            }
        }

    @Test
    fun `import of an already tracked file is skipped`() =
        runTest(testDispatcher) {
            val existing =
                DownloadedVideoEntity(
                    id = 1L,
                    videoId = "abc123",
                    url = Constants.buildYouTubeWatchUrl("abc123"),
                    title = "Existing title",
                    outputPaths = listOf("/sdcard/Download/video.mp4"),
                    mediaType = MediaType.VIDEO.name,
                )
            coEvery { downloadedVideoRepository.findByVideoId("abc123") } returns existing

            val importedCount =
                viewModel.importVideos(jsonBackup("abc123", "/sdcard/Download/video.mp4"))

            assertEquals(0, importedCount)
            coVerify(exactly = 0) { downloadedVideoRepository.insert(any()) }
            coVerify(exactly = 0) { downloadedVideoRepository.addOutputPath(any(), any()) }
        }

    /**
     * A full JSON backup whose download history carries [videoPath] for the item, so that the
     * watch URL fallback of the import has to be used to find the local file.
     */
    private fun jsonBackup(
        videoId: String,
        videoPath: String?,
    ): ImportResult.JsonSuccess =
        ImportResult.JsonSuccess(
            ExportData(
                items =
                    listOf(
                        ExportItem(
                            videoId = videoId,
                            url = "https://youtu.be/$videoId",
                            title = "Imported title",
                            author = "Imported author",
                            thumbnailUrl = "https://example.com/thumb.jpg",
                            sponsorBlockCategories = null,
                        ),
                    ),
                downloadHistory =
                    listOfNotNull(
                        videoPath?.let { path ->
                            SealDownloadHistoryEntry(
                                id = 0,
                                videoTitle = "Imported title",
                                videoAuthor = "Imported author",
                                videoUrl = Constants.buildYouTubeWatchUrl(videoId),
                                thumbnailUrl = "https://example.com/thumb.jpg",
                                videoPath = path,
                                extractor = "YouTube",
                            )
                        },
                    ),
            ),
        )
}
