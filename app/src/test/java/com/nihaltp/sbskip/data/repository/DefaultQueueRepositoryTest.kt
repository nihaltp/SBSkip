package com.nihaltp.sbskip.data.repository

import android.content.Context
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.workers.DownloadWorkScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [DefaultQueueRepository].
 *
 * Covers:
 *  - feat: add updateLocalFileUri method (commit 6f3361)
 *  - feat: add outputDurationSeconds to DownloadQueueEntity (commit 40b623)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultQueueRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var dao: DownloadQueueDao
    private lateinit var workScheduler: DownloadWorkScheduler
    private lateinit var repository: DefaultQueueRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Mock context so context.getString() never throws Resources$NotFoundException
        context = mockk(relaxed = true)
        every { context.getString(any<Int>()) } returns ""
        dao = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)

        coEvery { dao.observeQueue() } returns flowOf(emptyList())

        repository = DefaultQueueRepository(dao, workScheduler, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── feat: add updateLocalFileUri (commit 6f3361) ──────────────────────────

    @Test
    fun `updateLocalFileUri delegates to dao with correct parameters`() =
        runTest(testDispatcher) {
            val itemId = 7L
            val newUri = "content://media/external/downloads/99"
            val relativePath = "Music/Downloads/"

            coEvery { dao.updateLocalFileUri(any(), any(), any(), any()) } just Runs

            repository.updateLocalFileUri(itemId, newUri, relativePath)

            coVerify(exactly = 1) {
                dao.updateLocalFileUri(
                    id = itemId,
                    localFileUri = newUri,
                    relativePath = relativePath,
                    updatedAtEpochMillis = any(),
                )
            }
        }

    @Test
    fun `updateLocalFileUri works with null relativePath`() =
        runTest(testDispatcher) {
            val itemId = 8L
            val newUri = "content://media/external/video/5"

            coEvery { dao.updateLocalFileUri(any(), any(), any(), any()) } just Runs

            repository.updateLocalFileUri(itemId, newUri, null)

            coVerify(exactly = 1) {
                dao.updateLocalFileUri(
                    id = itemId,
                    localFileUri = newUri,
                    relativePath = null,
                    updatedAtEpochMillis = any(),
                )
            }
        }

    // ── feat: add outputDurationSeconds to DownloadQueueEntity (commit 40b623) ─

    @Test
    fun `markCompleted delegates outputDurationSeconds to dao`() =
        runTest(testDispatcher) {
            val itemId = 3L
            val outputPath = "/sdcard/Music/song_sbskip.m4a"
            val outputDuration = 220L

            coEvery { dao.markCompleted(any(), any(), any(), any()) } just Runs

            repository.markCompleted(itemId, outputPath, outputDuration)

            coVerify(exactly = 1) {
                dao.markCompleted(
                    id = itemId,
                    outputPath = outputPath,
                    outputDurationSeconds = outputDuration,
                    updatedAtEpochMillis = any(),
                )
            }
        }

    @Test
    fun `markCompleted passes null outputDurationSeconds when not provided`() =
        runTest(testDispatcher) {
            val itemId = 4L
            val outputPath = "/sdcard/Music/song_sbskip.m4a"

            coEvery { dao.markCompleted(any(), any(), any(), any()) } just Runs

            repository.markCompleted(itemId, outputPath, null)

            coVerify(exactly = 1) {
                dao.markCompleted(
                    id = itemId,
                    outputPath = outputPath,
                    outputDurationSeconds = null,
                    updatedAtEpochMillis = any(),
                )
            }
        }

    // ── enqueue: URL normalization ─────────────────────────────────────────────

    @Test
    fun `enqueue returns failure for invalid YouTube URL`() =
        runTest(testDispatcher) {
            val result =
                repository.enqueue(
                    localFileUri = "content://media/video/1",
                    title = "Test",
                    youtubeUrl = "https://notyoutube.com/watch?v=abc",
                    mediaType = MediaType.VIDEO,
                )

            assertFalse("Expected failure for invalid YouTube URL", result.success)
        }

    @Test
    fun `enqueue succeeds for sbskip local scheme`() =
        runTest(testDispatcher) {
            val insertedIdSlot = slot<DownloadQueueEntity>()
            coEvery { dao.insert(capture(insertedIdSlot)) } returns 10L

            val result =
                repository.enqueue(
                    localFileUri = "content://media/video/1",
                    title = "Local Convert",
                    youtubeUrl = "sbskip://local",
                    mediaType = MediaType.VIDEO,
                )

            assertTrue("Expected success for sbskip:// scheme", result.success)
            assertEquals("sbskip://local", insertedIdSlot.captured.url)
        }

    @Test
    fun `enqueue preserves overwrite=true flag in normalized URL`() =
        runTest(testDispatcher) {
            val insertedEntitySlot = slot<DownloadQueueEntity>()
            coEvery { dao.insert(capture(insertedEntitySlot)) } returns 11L

            val result =
                repository.enqueue(
                    localFileUri = "content://media/video/1",
                    title = "Test",
                    youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&overwrite=true",
                    mediaType = MediaType.VIDEO,
                )

            assertTrue("Expected success", result.success)
            assertTrue(
                "Expected overwrite=true in normalized URL",
                insertedEntitySlot.captured.url.contains("overwrite=true"),
            )
        }

    @Test
    fun `findItemById returns null when dao has no matching item`() =
        runTest(testDispatcher) {
            coEvery { dao.findById(999L) } returns null

            val item = repository.findItemById(999L)

            assertEquals(null, item)
        }

    @Test
    fun `findItemById maps DownloadQueueEntity to DownloadQueueItem`() =
        runTest(testDispatcher) {
            val entity =
                DownloadQueueEntity(
                    id = 5L,
                    url = "https://www.youtube.com/watch?v=abc123",
                    title = "Test Video",
                    localFileUri = "content://media/5",
                    mediaType = "VIDEO",
                    thumbnailUrl = null,
                    durationSeconds = 180L,
                    status = DownloadQueueStatus.QUEUED,
                    createdAtEpochMillis = 1000L,
                    updatedAtEpochMillis = 1000L,
                    outputDurationSeconds = null,
                )

            coEvery { dao.findById(5L) } returns entity

            val item = repository.findItemById(5L)

            assertEquals(5L, item?.id)
            assertEquals("Test Video", item?.title)
            assertEquals(MediaType.VIDEO, item?.mediaType)
            assertEquals(180L, item?.durationSeconds)
            assertEquals(DownloadQueueStatus.QUEUED, item?.status)
        }
}
