package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.workers.helpers.ImportedVideoEnricher
import com.nihaltp.sbskip.workers.helpers.ImportedVideoEnrichment
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fills in the metadata of imported videos that is still missing, preferring their local file and
 * falling back to YouTube.
 *
 * Work is done in small batches with a few videos fetched in parallel, so that a large import is
 * described quickly without hammering YouTube. A batch that keeps failing is retried with
 * WorkManager's exponential backoff; after [MAX_ATTEMPTS] the videos are left with the metadata
 * they have instead of retrying forever. If the 8-minute time budget expires while items still
 * need enrichment, the worker retries itself so the backlog drains across multiple runs.
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
        private var failures = 0
        private val applicationContext: Context = appContext

        override suspend fun doWork(): Result {
            val startedAt = System.currentTimeMillis()
            failures = 0
            var hadMoreWork = false

            while (System.currentTimeMillis() - startedAt < MAX_RUN_MILLIS) {
                val batch = repository.findNeedingEnrichment(BATCH_SIZE)
                if (batch.isEmpty()) {
                    return Result.success()
                }

                // A full batch hints that more work likely exists after this one.
                hadMoreWork = batch.size == BATCH_SIZE

                batch
                    .chunked(ENRICHMENT_CONCURRENCY)
                    .forEach { chunk ->
                        val outcomes =
                            coroutineScope {
                                chunk
                                    .map { video -> async { video to enricher.enrich(video) } }
                                    .awaitAll()
                            }
                        outcomes.forEach { (video, outcome) -> onEnrichmentDone(video, outcome) }
                    }

                if (failures > 0) {
                    break
                }
            }

            if (failures == 0) {
                // Time budget expired but no failures: if more items remain, self-continue so a
                // huge import drains across multiple runs without exhausting MAX_ATTEMPTS.
                if (hadMoreWork || repository.findNeedingEnrichment(1).isNotEmpty()) {
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequestBuilder<EnrichImportedVideosWorker>().build(),
                    )
                }
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

        private suspend fun onEnrichmentDone(
            video: com.nihaltp.sbskip.model.DownloadedVideoEntity,
            outcome: ImportedVideoEnrichment,
        ) {
            when (outcome) {
                is ImportedVideoEnrichment.Enriched -> {
                    repository.updateMetadata(
                        id = video.id,
                        title = outcome.metadata.title,
                        author = outcome.metadata.author,
                        thumbnailUrl = outcome.metadata.thumbnailUrl,
                        durationSeconds = outcome.metadata.durationSeconds,
                    )
                }

                is ImportedVideoEnrichment.Retry -> {
                    failures++
                }

                is ImportedVideoEnrichment.Unavailable -> {
                    repository.clearNeedsEnrichment(video.id)
                }
            }
        }

        companion object {
            private const val TAG = "EnrichImportedVideosWorker"
            private const val WORK_NAME = "EnrichImportedVideos"
            private const val BATCH_SIZE = 10
            private const val ENRICHMENT_CONCURRENCY = 3
            private const val MAX_ATTEMPTS = 3
            private const val MAX_RUN_MILLIS = 8 * 60 * 1_000L
        }
    }
