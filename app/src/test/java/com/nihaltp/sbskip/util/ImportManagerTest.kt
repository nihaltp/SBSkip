package com.nihaltp.sbskip.util

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.notifications.ImportNotificationManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the import queue bookkeeping of [ImportManager].
 *
 * Covers:
 *  - auto-removal of finished (COMPLETED/FAILED) queue items
 *  - bounded queue size
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var importManager: ImportManager
    private lateinit var importNotificationManager: ImportNotificationManager
    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var folderScanner: FolderScanner

    @Before
    fun setup() {
        // The import manager runs its coroutines on the main dispatcher.
        Dispatchers.setMain(testDispatcher)
        importNotificationManager = mockk<ImportNotificationManager>(relaxed = true)
        downloadedVideoRepository = mockk<DownloadedVideoRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)
        folderScanner = mockk<FolderScanner>(relaxed = true)
        // A relaxed mock would return a mocked entity instead of null.
        coEvery { downloadedVideoRepository.findByVideoId(any()) } returns null
        coEvery { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { settingsRepository.update(any()) } just Runs
        importManager =
            ImportManager(
                downloadedVideoRepository,
                mockk<FfmpegMetadataReader>(relaxed = true),
                folderScanner,
                importNotificationManager,
                settingsRepository,
                ApplicationProvider.getApplicationContext(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(
        id: Long,
        status: FolderImportStatus = FolderImportStatus.PENDING,
    ) = FolderImportQueueItem(
        id = id,
        treeUri = Uri.parse("content://com.test.provider/tree/$id"),
        folderName = "Folder $id",
        status = status,
        importedCount = if (status == FolderImportStatus.COMPLETED) 3 else 0,
        errorMessage = if (status == FolderImportStatus.FAILED) "boom" else null,
    )

    @Test
    fun `dismissCompletedItem removes a completed item`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.COMPLETED))
        importManager.enqueueForTesting(item(2L, FolderImportStatus.PENDING))

        importManager.dismissCompletedItem(1L)

        assertEquals(listOf(2L), importManager.importQueue.value.map { it.id })
    }

    @Test
    fun `dismissCompletedItem removes a failed item`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.FAILED))

        importManager.dismissCompletedItem(1L)

        assertTrue(importManager.importQueue.value.isEmpty())
    }

    @Test
    fun `dismissCompletedItem keeps pending and scanning items`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.PENDING))
        importManager.enqueueForTesting(item(2L, FolderImportStatus.SCANNING))

        importManager.dismissCompletedItem(1L)
        importManager.dismissCompletedItem(2L)

        assertEquals(
            listOf(
                FolderImportStatus.PENDING,
                FolderImportStatus.SCANNING,
            ),
            importManager.importQueue.value.map { it.status },
        )
    }

    @Test
    fun `dismissCompletedItem ignores unknown ids`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.COMPLETED))

        importManager.dismissCompletedItem(99L)

        assertEquals(1, importManager.importQueue.value.size)
    }

    @Test
    fun `removeQueueItem removes pending items`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.PENDING))

        importManager.removeQueueItem(1L)

        assertTrue(importManager.importQueue.value.isEmpty())
    }

    @Test
    fun `removeQueueItem cancels the in-flight scan and removes the folder`() =
        runTest(testDispatcher) {
            // Mock the scanner to return a flow that never completes, keeping the scan job in-flight.
            every { folderScanner.scanMediaFilesInFolder(any()) } returns
                flow {
                    awaitCancellation()
                }

            importManager.enqueueForTesting(item(1L, FolderImportStatus.PENDING))

            // Start the queue processor in a coroutine so it can run while we cancel.
            async {
                importManager.processQueue()
            }

            // Wait until the scan job has been created and is active.
            advanceUntilIdle()
            val scanJob = importManager.scanningJob
            assertNotNull("Expected a scanning job to be running", scanJob)
            assertTrue("Expected the scan job to be active", scanJob!!.isActive)

            importManager.removeQueueItem(1L)

            // The queue item should be removed.
            assertTrue(
                "The folder that is currently scanned should be removed from the queue",
                importManager.importQueue.value.isEmpty(),
            )
            // The scanning job reference should be cleared.
            assertNull("Expected scanningJob to be cleared after cancellation", importManager.scanningJob)
            // The underlying job should have been cancelled.
            assertTrue("Expected the scan job to be cancelled", scanJob!!.isCancelled)
        }

    @Test
    fun `cancelCurrentScan cancels the in-flight scan and removes the folder`() =
        runTest(testDispatcher) {
            // Mock the scanner to return a flow that never completes, keeping the scan job in-flight.
            every { folderScanner.scanMediaFilesInFolder(any()) } returns
                flow {
                    awaitCancellation()
                }

            importManager.enqueueForTesting(item(1L, FolderImportStatus.PENDING))

            // Start the queue processor in a coroutine so it can run while we cancel.
            async {
                importManager.processQueue()
            }

            // Wait until the scan job has been created and is active.
            advanceUntilIdle()
            val scanJob = importManager.scanningJob
            assertNotNull("Expected a scanning job to be running", scanJob)
            assertTrue("Expected the scan job to be active", scanJob!!.isActive)

            importManager.cancelCurrentScan()

            // The queue item should be removed.
            assertTrue(
                "The cancelled folder should be removed from the queue",
                importManager.importQueue.value.isEmpty(),
            )
            // The scanning job reference should be cleared.
            assertNull("Expected scanningJob to be cleared after cancellation", importManager.scanningJob)
            // The underlying job should have been cancelled.
            assertTrue("Expected the scan job to be cancelled", scanJob!!.isCancelled)
        }

    @Test
    fun `cancelCurrentScan keeps the queue when no folder is scanned`() {
        importManager.enqueueForTesting(item(1L, FolderImportStatus.PENDING))

        importManager.cancelCurrentScan()

        assertEquals(
            "A pending folder must not be removed when no scan is running",
            1,
            importManager.importQueue.value.size,
        )
    }

    @Test
    fun `the queue survives a restart of the app`() =
        runTest(testDispatcher) {
            val persisted =
                listOf(
                    PersistedFolderImport(
                        id = 7L,
                        treeUri = "content://com.test.provider/tree/7",
                        folderName = "Folder 7",
                        status = FolderImportStatus.COMPLETED,
                        importedCount = 3,
                        errorMessage = null,
                    ),
                )
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(importQueue = persisted))

            importManager.resumePersistedImports()
            testDispatcher.scheduler.advanceUntilIdle()

            val queue = importManager.importQueue.value
            assertEquals(1, queue.size)
            assertEquals("Folder 7", queue.first().folderName)
            assertEquals(Uri.parse("content://com.test.provider/tree/7"), queue.first().treeUri)
            assertEquals(7L, queue.first().id)
        }

    @Test
    fun `the queue is persisted when it changes`() =
        runTest(testDispatcher) {
            val updateSlot = slot<(AppSettings) -> AppSettings>()
            coEvery { settingsRepository.update(capture(updateSlot)) } just Runs
            importManager.resumePersistedImports()
            testDispatcher.scheduler.advanceUntilIdle()

            importManager.enqueueForTesting(item(1L))
            testDispatcher.scheduler.advanceUntilIdle()

            val persisted = updateSlot.captured(AppSettings())
            assertEquals(1, persisted.importQueue.size)
            assertEquals(1L, persisted.importQueue.first().id)
            assertEquals("Folder 1", persisted.importQueue.first().folderName)
        }

    @Test
    fun `queue marks overflow items as FAILED instead of dropping them`() {
        val total = ImportManager.MAX_QUEUE_SIZE + 20
        repeat(total) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }

        val queue = importManager.importQueue.value
        // Overflow items are marked FAILED and kept in the queue, not silently removed.
        assertEquals(total, queue.size)
        // All but the newest item overflowed and were marked FAILED.
        assertEquals(total - 1, queue.count { it.status == FolderImportStatus.FAILED })
        assertEquals(1, queue.count { it.status == FolderImportStatus.PENDING })
        // Overflowed items carry an error message explaining why.
        queue.filter { it.status == FolderImportStatus.FAILED }.forEach { failed ->
            assertEquals("Import queue full", failed.errorMessage)
        }
        // The newest item is always kept.
        assertEquals((total - 1).toLong(), queue.last().id)
        assertEquals(FolderImportStatus.PENDING, queue.last().status)
    }

    @Test
    fun `queue cap keeps scanning and finished items when possible`() {
        repeat(ImportManager.MAX_QUEUE_SIZE) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }
        importManager.enqueueForTesting(item(999L, FolderImportStatus.COMPLETED))

        val queue = importManager.importQueue.value
        // The overflow PENDING item (id 0) is marked FAILED, not removed.
        assertEquals(ImportManager.MAX_QUEUE_SIZE + 1, queue.size)
        assertEquals(1, queue.count { it.status == FolderImportStatus.FAILED })
        assertEquals(0L, queue.first { it.status == FolderImportStatus.FAILED }.id)
        // The newest COMPLETED item is kept.
        assertTrue(queue.any { it.id == 999L && it.status == FolderImportStatus.COMPLETED })
    }

    @Test
    fun `enqueueing a folder updates the folder queue notification`() {
        importManager.enqueueForTesting(item(1L))

        verify(exactly = 1) { importNotificationManager.updateFolderQueueNotification(1) }
    }

    @Test
    fun `removing a pending folder updates the folder queue notification`() {
        importManager.enqueueForTesting(item(1L))

        importManager.removeQueueItem(1L)

        verify(exactly = 1) { importNotificationManager.updateFolderQueueNotification(0) }
    }
}
