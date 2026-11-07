package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerDownloadScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadWorkScheduler {
        private val workManager = WorkManager.getInstance(context)

        override fun schedule(queueItemId: Long) {
            val workName = "CleanMedia_Queue_$queueItemId"
            val workRequest = buildWorkRequest(workName, queueItemId)

            // Use unique work name per queue item with KEEP policy.
            // This makes scheduling idempotent per queue-item ID (fixes restart reprocessing).
            // Each download runs independently, so failure of one doesn't cancel others.
            // Sequential execution is best-effort via WorkManager's default concurrency limits.
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)
        }

        private fun buildWorkRequest(
            workName: String,
            queueItemId: Long,
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_QUEUE_ITEM_ID to queueItemId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS,
                )
                .addTag(workName)
                .build()
        }

        override fun scheduleSponsorBlockStatusCheck() {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<SponsorBlockStatusWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        1,
                        TimeUnit.MINUTES,
                    )
                    .build()

            workManager.enqueueUniqueWork(
                "SponsorBlockStatusCheck",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }
    }
