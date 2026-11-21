package com.nihaltp.sbskip.workers

interface DownloadWorkScheduler {
    suspend fun schedule(queueItemId: Long)

    fun scheduleSponsorBlockStatusCheck()
}
