package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SponsorBlockStatusWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sponsorBlockService: SponsorBlockService,
    private val queueRepository: QueueRepository,
    private val dao: DownloadQueueDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppLogger.worker("SponsorBlockStatusWorker checking API status...")
        return try {
            val status = sponsorBlockService.checkApiStatus()
            AppLogger.worker("SponsorBlock API status page returned state: $status")
            if (status == "operational") {
                val failedItems = dao.findSponsorBlockFailedItems()
                AppLogger.worker("SponsorBlock API is UP (operational). Found ${failedItems.size} failed items to retry.")
                for (item in failedItems) {
                    AppLogger.worker("Retrying queue item id=${item.id} url=${item.url}")
                    queueRepository.retry(item.id)
                }
                Result.success()
            } else {
                AppLogger.worker("SponsorBlock API status is not operational ($status). Retrying status check later.")
                Result.retry()
            }
        } catch (throwable: Throwable) {
            AppLogger.error(
                "SponsorBlockStatusWorker",
                throwable,
                "Failed to check SponsorBlock API status. Retrying status check later.",
            )
            Result.retry()
        }
    }
}
