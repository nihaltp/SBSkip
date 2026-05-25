package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerMetadataQueueScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : MetadataQueueWorkScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<MetadataQueueWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "metadata_queue_worker"
    }
}
