package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
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
}