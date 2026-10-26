package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for the metadata of imported videos to be resolved in the background.
 *
 * Enrichment is a single unique piece of work: importing again while it is running extends the
 * existing run instead of starting a second one.
 */
@Singleton
class ImportEnrichmentScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule() {
            val request =
                OneTimeWorkRequestBuilder<EnrichImportedVideosWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    // WorkManager multiplies this delay on every retry, giving a failing request
                    // more room each time instead of hammering YouTube.
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            private const val WORK_NAME = "EnrichImportedVideos"
        }
    }
