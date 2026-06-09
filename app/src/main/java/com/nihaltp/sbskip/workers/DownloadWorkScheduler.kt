package com.nihaltp.sbskip.workers

interface DownloadWorkScheduler {
    fun schedule(queueItemId: Long)

    fun scheduleSponsorBlockStatusCheck()
}
