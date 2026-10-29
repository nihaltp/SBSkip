package com.nihaltp.sbskip.data.repository

import com.nihaltp.sbskip.data.local.dao.DownloadedVideoDao
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
                        mediaType = "audio",
                    ),
                )
            every { dao.observeDownloadedVideos() } returns flowOf(expected)
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
                    mediaType = "audio",
                )

            val videoSlot = slot<DownloadedVideoEntity>()
            coEvery { dao.insert(capture(videoSlot)) } returns 42L

            val id = repository.insert(video)

            assertEquals(42L, id)
            coVerify(exactly = 1) { dao.insert(video) }
            assertEquals(video, videoSlot.captured)
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
