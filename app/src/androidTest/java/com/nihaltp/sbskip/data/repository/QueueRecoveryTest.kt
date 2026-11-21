package com.nihaltp.sbskip.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.model.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.workers.DownloadWorkScheduler
import com.nihaltp.sbskip.workers.DownloadWorker
import com.nihaltp.sbskip.workers.downloadWorkName
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QueueRecoveryTest {
    @Test
    fun recoverySkipsLiveWorkAndSchedulesTheOrphan() {
        runBlocking {
            withTimeout(10_000) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val workManager = WorkManager.getInstance(context)
                val baseId = System.currentTimeMillis()
                val items =
                    listOf(
                        queueItem(baseId, DownloadQueueStatus.QUEUED),
                        queueItem(baseId + 1, DownloadQueueStatus.FETCHING_SEGMENTS),
                        queueItem(baseId + 2, DownloadQueueStatus.DOWNLOADING),
                        queueItem(baseId + 3, DownloadQueueStatus.DOWNLOADING),
                    )
                val existing = items.dropLast(1)
                val dao = mockk<DownloadQueueDao>()
                val scheduler = mockk<DownloadWorkScheduler>(relaxed = true)
                coEvery { dao.findActiveItems() } returns items
                val repository = DefaultQueueRepository(dao, mockk(), scheduler, workManager, context)

                try {
                    existing.forEach { item ->
                        val delayedWork =
                            OneTimeWorkRequestBuilder<DownloadWorker>()
                                .setInitialDelay(1, TimeUnit.HOURS)
                                .build()
                        workManager.enqueueUniqueWork(downloadWorkName(item.id), ExistingWorkPolicy.KEEP, delayedWork).await()
                    }

                    repository.resumeStuckItems()

                    verify(exactly = 1) { scheduler.scheduleRecovery(items.last().id) }
                    existing.forEach { item ->
                        verify(exactly = 0) { scheduler.scheduleRecovery(item.id) }
                    }
                    coVerify(exactly = 0) { scheduler.schedule(any()) }
                } finally {
                    withContext(NonCancellable) {
                        existing.forEach { workManager.cancelUniqueWork(downloadWorkName(it.id)).await() }
                    }
                }
            }
        }
    }

    private fun queueItem(
        id: Long,
        status: DownloadQueueStatus,
    ): DownloadQueueEntity =
        DownloadQueueEntity(
            id = id,
            url = "https://www.youtube.com/watch?v=abcdefghijk",
            title = "Test Video",
            localFileUri = "",
            mediaType = "VIDEO",
            thumbnailUrl = null,
            durationSeconds = null,
            status = status,
            createdAtEpochMillis = id,
            updatedAtEpochMillis = id,
        )
}
