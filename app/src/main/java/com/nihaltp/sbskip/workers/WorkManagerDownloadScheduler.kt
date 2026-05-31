package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadWorkScheduler {
    override fun schedule(queueItemId: Long) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_QUEUE_ITEM_ID to queueItemId))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    override fun scheduleSponsorBlockStatusCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SponsorBlockStatusWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SponsorBlockStatusCheck",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }
}
