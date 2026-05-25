package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nihaltp.sbskip.downloader.VideoMetadataExtractor
import com.nihaltp.sbskip.downloader.YtDlpExecutionException
import com.nihaltp.sbskip.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MetadataQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val queueRepository: com.nihaltp.sbskip.data.repository.QueueRepository,
    private val metadataExtractor: VideoMetadataExtractor,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        AppLogger.worker("metadata queue worker started")

        return try {
            while (true) {
                val nextItem = queueRepository.claimNextQueuedItem() ?: break
                queueRepository.markFetchingInfo(nextItem.id)

                val metadata = try {
                    metadataExtractor.extract(nextItem.url)
                } catch (throwable: Throwable) {
                    queueRepository.markFailed(nextItem.id, userFriendlyMessage(throwable))
                    continue
                }

                queueRepository.markReady(nextItem.id, metadata)
            }

            AppLogger.worker("metadata queue worker finished")
            Result.success()
        } catch (throwable: Throwable) {
            AppLogger.error("Worker", throwable, "metadata queue worker failed")
            Result.retry()
        }
    }

    private fun userFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is IllegalArgumentException -> throwable.message ?: "Invalid YouTube URL"
            is YtDlpExecutionException -> "yt-dlp failed to read metadata"
            else -> throwable.message?.takeIf { it.isNotBlank() } ?: "Unable to fetch metadata"
        }
    }
}
