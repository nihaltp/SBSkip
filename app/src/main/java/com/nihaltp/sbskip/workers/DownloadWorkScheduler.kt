package com.nihaltp.sbskip.workers

interface DownloadWorkScheduler {
    suspend fun schedule(queueItemId: Long)

    fun scheduleRecovery(queueItemId: Long)

    fun scheduleSponsorBlockStatusCheck()
}

internal fun downloadWorkName(queueItemId: Long): String = "CleanMedia_Queue_$queueItemId"
