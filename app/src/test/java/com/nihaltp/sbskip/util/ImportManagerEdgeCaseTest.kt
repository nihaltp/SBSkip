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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportManagerEdgeCaseTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var importManager: ImportManager
    private lateinit var importNotificationManager: ImportNotificationManager
    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var folderScanner: FolderScanner

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        importNotificationManager = mockk<ImportNotificationManager>(relaxed = true)
        downloadedVideoRepository = mockk<DownloadedVideoRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)
        folderScanner = mockk<FolderScanner>(relaxed = true)
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
    fun `enqueueForTesting with duplicate id adds both items`() {
        // Current implementation doesn't deduplicate by ID
        importManager.enqueueForTesting(item(1L))
        importManager.enqueueForTesting(item(1L))

        assertEquals(2, importManager.importQueue.value.size)
    }

    @Test
    fun `resumePersistedImports handles empty list`() =
        runTest(testDispatcher) {
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(importQueue = emptyList()))

            importManager.resumePersistedImports()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(importManager.importQueue.value.isEmpty())
        }

    @Test
    fun `resumePersistedImports restores all persisted items`() =
        runTest(testDispatcher) {
            val persisted =
                listOf(
                    PersistedFolderImport(
                        id = 1L,
                        treeUri = "content://com.test.provider/tree/1",
                        folderName = "Folder 1",
                        status = FolderImportStatus.PENDING,
                        importedCount = 0,
                        errorMessage = null,
                    ),
                    PersistedFolderImport(
                        id = 2L,
                        treeUri = "content://com.test.provider/tree/2",
                        folderName = "Folder 2",
                        status = FolderImportStatus.COMPLETED,
                        importedCount = 3,
                        errorMessage = null,
                    ),
                    PersistedFolderImport(
                        id = 3L,
                        treeUri = "content://com.test.provider/tree/3",
                        folderName = "Folder 3",
                        status = FolderImportStatus.FAILED,
                        importedCount = 0,
                        errorMessage = "error",
                    ),
                    PersistedFolderImport(
                        id = 4L,
                        treeUri = "content://com.test.provider/tree/4",
                        folderName = "Folder 4",
                        status = FolderImportStatus.SCANNING,
                        importedCount = 0,
                        errorMessage = null,
                    ),
                )
            // Create a fresh ImportManager with the specific settings flow
            val testSettingsRepository = mockk<SettingsRepository>(relaxed = true)
            coEvery { testSettingsRepository.settings } returns flowOf(AppSettings(importQueue = persisted))
            coEvery { testSettingsRepository.update(any()) } just Runs
            val testImportManager =
                ImportManager(
                    downloadedVideoRepository,
                    mockk<FfmpegMetadataReader>(relaxed = true),
                    folderScanner,
                    importNotificationManager,
                    testSettingsRepository,
                    ApplicationProvider.getApplicationContext(),
                )

            testImportManager.resumePersistedImports()
            testDispatcher.scheduler.advanceUntilIdle()

            val queue = testImportManager.importQueue.value
            // All persisted items are restored
            assertEquals(4, queue.size)
        }

    @Test
    fun `processQueue handles empty queue`() =
        runTest(testDispatcher) {
            // Should not crash
            importManager.processQueue()
            advanceUntilIdle()
        }

    @Test
    fun `removeQueueItem on non-existent id does not crash`() {
        importManager.enqueueForTesting(item(1L))

        importManager.removeQueueItem(999L)

        assertEquals(1, importManager.importQueue.value.size)
    }

    @Test
    fun `cancelCurrentScan multiple times does not crash`() =
        runTest(testDispatcher) {
            every { folderScanner.scanMediaFilesInFolder(any()) } returns
                flow {
                    awaitCancellation()
                }

            importManager.enqueueForTesting(item(1L))

            async {
                importManager.processQueue()
            }
            advanceUntilIdle()

            importManager.cancelCurrentScan()
            importManager.cancelCurrentScan() // Second call should not crash

            assertTrue(importManager.importQueue.value.isEmpty())
            assertNull(importManager.scanningJob)
        }

    @Test
    fun `persisted queue includes PENDING and SCANNING items after restart`() =
        runTest(testDispatcher) {
            val persisted =
                listOf(
                    PersistedFolderImport(
                        id = 1L,
                        treeUri = "content://com.test.provider/tree/1",
                        folderName = "Folder 1",
                        status = FolderImportStatus.PENDING,
                        importedCount = 0,
                        errorMessage = null,
                    ),
                    PersistedFolderImport(
                        id = 2L,
                        treeUri = "content://com.test.provider/tree/2",
                        folderName = "Folder 2",
                        status = FolderImportStatus.SCANNING,
                        importedCount = 0,
                        errorMessage = null,
                    ),
                )
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(importQueue = persisted))

            importManager.resumePersistedImports()
            testDispatcher.scheduler.advanceUntilIdle()

            val queue = importManager.importQueue.value
            // PENDING and SCANNING items should be restored
            assertEquals(2, queue.size)
        }

    @Test
    fun `queue size does not exceed MAX_QUEUE_SIZE when adding many items`() {
        val total = ImportManager.MAX_QUEUE_SIZE + 100
        repeat(total) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }

        assertEquals(ImportManager.MAX_QUEUE_SIZE, importManager.importQueue.value.size)
    }

    @Test
    fun `dismissCompletedItem on empty queue does not crash`() {
        importManager.dismissCompletedItem(1L)

        assertTrue(importManager.importQueue.value.isEmpty())
    }

    @Test
    fun `dismissCompletedItem updates folder queue notification`() =
        runTest(testDispatcher) {
            importManager.enqueueForTesting(item(1L, FolderImportStatus.COMPLETED))
            testDispatcher.scheduler.advanceUntilIdle()

            importManager.dismissCompletedItem(1L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 1) { importNotificationManager.updateFolderQueueNotification(0) }
        }

    @Test
    fun `enqueue respects MAX_QUEUE_SIZE limit with exact size`() {
        repeat(ImportManager.MAX_QUEUE_SIZE) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }

        // Add one more - should not exceed
        importManager.enqueueForTesting(item(ImportManager.MAX_QUEUE_SIZE.toLong()))

        assertEquals(ImportManager.MAX_QUEUE_SIZE, importManager.importQueue.value.size)
    }

    @Test
    fun `import queue maintains insertion order`() {
        importManager.enqueueForTesting(item(1L))
        importManager.enqueueForTesting(item(2L))
        importManager.enqueueForTesting(item(3L))

        val queue = importManager.importQueue.value
        assertEquals(3, queue.size)
        assertEquals(1L, queue[0].id)
        assertEquals(2L, queue[1].id)
        assertEquals(3L, queue[2].id)
    }

    @Test
    fun `removeQueueItem maintains order of remaining items`() {
        importManager.enqueueForTesting(item(1L))
        importManager.enqueueForTesting(item(2L))
        importManager.enqueueForTesting(item(3L))

        importManager.removeQueueItem(2L)

        val queue = importManager.importQueue.value
        assertEquals(2, queue.size)
        assertEquals(1L, queue[0].id)
        assertEquals(3L, queue[1].id)
    }
}
