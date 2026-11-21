package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nihaltp.sbskip.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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

        override suspend fun schedule(queueItemId: Long) {
            val workName = "CleanMedia_Queue_$queueItemId"
            val workRequest = buildWorkRequest(workName, queueItemId)

            // Use unique work name per queue item with KEEP policy.
            // This makes scheduling idempotent per queue-item ID (fixes restart reprocessing).
            // Each download runs independently, so failure of one doesn't cancel others.
            // Sequential execution is best-effort via WorkManager's default concurrency limits.
            //
            // Cancel any existing work first so that a retry replaces stale/running work
            // instead of being silently ignored by KEEP while the old worker is still alive.
            // Wait for the cancelled work to reach terminal state (process dead + mutex released)
            // before enqueueing replacement, to prevent race with yt-dlp force-kill linger.
            workManager.cancelUniqueWork(workName)

            // Wait for any RUNNING/ENQUEUED work with this name to finish
            awaitWorkTerminalState(workName)

            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)
        }

        /**
         * Waits for work with the given name to reach a terminal state (SUCCEEDED, FAILED, CANCELLED).
         * This ensures the old yt-dlp process is fully dead and downloadMutex is released
         * before a replacement download starts.
         */
        private suspend fun awaitWorkTerminalState(workName: String) {
            val startTime = System.currentTimeMillis()
            val timeoutMillis = 5000L // FORCE_KILL_LINGER_MILLIS (2s) + buffer

            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                val workInfos = workManager.getWorkInfosForUniqueWorkLiveData(workName).value
                val hasLiveWork =
                    workInfos?.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    } == true
                if (!hasLiveWork) return
                delay(200)
            }
            // Timeout - log warning but proceed (old process should be force-killed by now)
            AppLogger.worker("Timeout waiting for $workName to terminate; proceeding with new work")
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
