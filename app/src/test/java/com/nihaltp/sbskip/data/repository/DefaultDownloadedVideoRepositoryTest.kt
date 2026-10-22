package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.data.local.dao.DownloadedVideoDao
import com.nihaltp.sbskip.data.local.database.StringListConverter
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [DefaultDownloadedVideoRepository].
 *
 * Covers:
 *  - feat: add downloaded videos tracker (issue #tracker)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultDownloadedVideoRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var dao: DownloadedVideoDao
    private lateinit var repository: DefaultDownloadedVideoRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mockk(relaxed = true)
        coEvery { dao.observeDownloadedVideos() } returns flowOf(emptyList())
        repository = DefaultDownloadedVideoRepository(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeDownloadedVideos delegates to dao`() =
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
            // Override the mock and rebuild the repository so it captures the new flow.
            coEvery { dao.observeDownloadedVideos() } returns flowOf(expected)
            val repo = DefaultDownloadedVideoRepository(dao)

            val actual = repo.observeDownloadedVideos.first()

            assertEquals(expected, actual)
            coVerify(atLeast = 1) { dao.observeDownloadedVideos() }
        }

    @Test
    fun `insert delegates to dao and returns id`() =
        runTest(testDispatcher) {
            val video =
                DownloadedVideoEntity(
                    id = 0L,
                    videoId = "abc123",
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    thumbnailUrl = "https://img.youtube.com/vi/abc123/mqdefault.jpg",
                    durationSeconds = 180L,
                    outputPaths = listOf("/sdcard/Music/test.m4a"),
                    mediaType = "audio",
                )

            val insertedSlot = slot<DownloadedVideoEntity>()
            coEvery { dao.insert(capture(insertedSlot)) } returns 42L

            val id = repository.insert(video)

            assertEquals(42L, id)
            coVerify(exactly = 1) { dao.insert(video) }
            assertEquals(video, insertedSlot.captured)
        }

    @Test
    fun `addOutputPath appends the path to the tracked video`() =
        runTest(testDispatcher) {
            val video =
                DownloadedVideoEntity(
                    id = 5L,
                    videoId = "abc123",
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    outputPaths = listOf("/sdcard/Music/test.m4a"),
                    mediaType = "audio",
                )
            coEvery { dao.findByVideoId("abc123") } returns video
            coEvery { dao.updateOutputPaths(5L, any()) } returns 1

            val added = repository.addOutputPath("abc123", "/sdcard/Music/test copy.m4a")

            assertEquals(true, added)
            val pathsSlot = slot<String>()
            coVerify(exactly = 1) { dao.updateOutputPaths(5L, capture(pathsSlot)) }
            assertEquals(
                listOf("/sdcard/Music/test.m4a", "/sdcard/Music/test copy.m4a"),
                StringListConverter.decode(pathsSlot.captured),
            )
        }

    @Test
    fun `addOutputPath does not add the same path twice`() =
        runTest(testDispatcher) {
            val video =
                DownloadedVideoEntity(
                    id = 5L,
                    videoId = "abc123",
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    outputPaths = listOf("/sdcard/Music/test.m4a"),
                    mediaType = "audio",
                )
            coEvery { dao.findByVideoId("abc123") } returns video

            val added = repository.addOutputPath("abc123", "/sdcard/Music/test.m4a")

            assertEquals(true, added)
            coVerify(exactly = 0) { dao.updateOutputPaths(any(), any()) }
        }

    @Test
    fun `addOutputPath fails for a video that is not tracked`() =
        runTest(testDispatcher) {
            coEvery { dao.findByVideoId("abc123") } returns null

            val added = repository.addOutputPath("abc123", "/sdcard/Music/test.m4a")

            assertEquals(false, added)
        }

    @Test
    fun `findByOutputPath returns the video that owns the path`() =
        runTest(testDispatcher) {
            val video =
                DownloadedVideoEntity(
                    id = 5L,
                    videoId = "abc123",
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    outputPaths = listOf("/sdcard/Music/test.m4a", "/sdcard/Music/test copy.m4a"),
                    mediaType = "audio",
                )
            coEvery { dao.findByOutputPathCandidates(any()) } returns listOf(video)

            val found = repository.findByOutputPath("/sdcard/Music/test copy.m4a")

            assertEquals(video, found)
        }

    @Test
    fun `findByOutputPath ignores a path that is only a substring of a known one`() =
        runTest(testDispatcher) {
            val video =
                DownloadedVideoEntity(
                    id = 5L,
                    videoId = "abc123",
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    outputPaths = listOf("/sdcard/Music/test.m4a"),
                    mediaType = "audio",
                )
            coEvery { dao.findByOutputPathCandidates(any()) } returns listOf(video)

            val found = repository.findByOutputPath("/sdcard/Music/other.m4a")

            assertEquals(null, found)
        }

    @Test
    fun `deleteById delegates to dao`() =
        runTest(testDispatcher) {
            val itemId = 7L

            coEvery { dao.deleteById(any()) } just Runs

            repository.deleteById(itemId)

            coVerify(exactly = 1) { dao.deleteById(id = itemId) }
        }

    @Test
    fun `deleteAll delegates to dao`() =
        runTest(testDispatcher) {
            coEvery { dao.deleteAll() } just Runs

            repository.deleteAll()

            coVerify(exactly = 1) { dao.deleteAll() }
        }
}
