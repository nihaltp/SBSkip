package com.nihaltp.sbskip.util

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.notifications.ImportNotificationManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
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
@RunWith(RobolectricTestRunner::class)
class ImportManagerTest {
    private lateinit var importManager: ImportManager
    private lateinit var importNotificationManager: ImportNotificationManager

    @Before
    fun setup() {
        importNotificationManager = mockk<ImportNotificationManager>(relaxed = true)
        importManager =
            ImportManager(
                mockk<DownloadedVideoRepository>(relaxed = true),
                mockk<FfmpegMetadataReader>(relaxed = true),
                importNotificationManager,
                ApplicationProvider.getApplicationContext(),
            )
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
    fun `queue never grows beyond the maximum queue size`() {
        val total = ImportManager.MAX_QUEUE_SIZE + 20
        repeat(total) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }

        val queue = importManager.importQueue.value
        assertEquals(ImportManager.MAX_QUEUE_SIZE, queue.size)
        // The oldest pending entries are dropped first, the newest one is always kept.
        assertEquals((total - ImportManager.MAX_QUEUE_SIZE).toLong(), queue.first().id)
        assertEquals((total - 1).toLong(), queue.last().id)
    }

    @Test
    fun `queue cap keeps scanning and finished items when possible`() {
        repeat(ImportManager.MAX_QUEUE_SIZE) { index ->
            importManager.enqueueForTesting(item(index.toLong()))
        }
        importManager.enqueueForTesting(item(999L, FolderImportStatus.COMPLETED))

        val queue = importManager.importQueue.value
        assertEquals(ImportManager.MAX_QUEUE_SIZE, queue.size)
        assertTrue(queue.any { it.id == 999L })
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
