package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.workers.helpers.ImportedVideoEnricher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

/**
 * Fills in the metadata of imported videos that is still missing, preferring their local file and
 * falling back to YouTube.
 *
 * Work is done in small batches with a pause between requests, so a large import cannot hammer
 * YouTube. A batch that keeps failing is retried with WorkManager's exponential backoff; after
 * [MAX_ATTEMPTS] the videos are left with the metadata they have instead of retrying forever.
 */
@HiltWorker
class EnrichImportedVideosWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val repository: DownloadedVideoRepository,
        private val enricher: ImportedVideoEnricher,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val startedAt = System.currentTimeMillis()
            var failures = 0

            while (System.currentTimeMillis() - startedAt < MAX_RUN_MILLIS) {
                val batch = repository.findNeedingEnrichment(BATCH_SIZE)
                if (batch.isEmpty()) {
                    return Result.success()
                }

                batch.forEachIndexed { index, video ->
                    if (index > 0) delay(REQUEST_SPACING_MILLIS)
                    val metadata =
                        runCatching { enricher.enrich(video) }
                            .onFailure { e ->
                                AppLogger.error(TAG, e, "Enrichment failed for ${video.videoId}")
                            }.getOrNull()
                    if (metadata == null) {
                        failures++
                    } else {
                        repository.updateMetadata(
                            id = video.id,
                            title = metadata.title,
                            author = metadata.author,
                            thumbnailUrl = metadata.thumbnailUrl,
                            durationSeconds = metadata.durationSeconds,
                        )
                    }
                }

                if (failures > 0) break
            }

            if (failures == 0) {
                return Result.success()
            }
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                return Result.retry()
            }

            // Out of attempts: keep what is known rather than retrying these videos forever.
            repository.findNeedingEnrichment(BATCH_SIZE).forEach { video ->
                repository.clearNeedsEnrichment(video.id)
            }
            return Result.success()
        }

        companion object {
            private const val TAG = "EnrichImportedVideosWorker"
            private const val BATCH_SIZE = 10
            private const val REQUEST_SPACING_MILLIS = 1_000L
            private const val MAX_ATTEMPTS = 3
            private const val MAX_RUN_MILLIS = 8 * 60 * 1_000L
        }
    }
