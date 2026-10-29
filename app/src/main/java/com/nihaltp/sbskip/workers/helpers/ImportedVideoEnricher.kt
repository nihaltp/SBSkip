package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.fetcher.YouTubeOEmbedFetcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of describing an imported video.
 *
 * [Enriched] is the normal case: at least one source supplied a non-blank title and the worker
 * persists it. [Retry] means nothing could be described right now, so the worker should stop
 * asking while the budget or attempts run out and try again later. [Unavailable] means the video
 * will never be describable through oEmbed (deleted, private, region-blocked): the worker should
 * stop asking forever.
 */
sealed interface ImportedVideoEnrichment {
    data class Enriched(
        val metadata: EnrichedVideoMetadata,
    ) : ImportedVideoEnrichment

    data object Retry : ImportedVideoEnrichment

    data object Unavailable : ImportedVideoEnrichment
}

/** Metadata resolved for an imported video from YouTube. */
data class EnrichedVideoMetadata(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)

/**
 * Resolves the metadata of an imported video from YouTube via oEmbed.
 *
 * The video id is the only thing the worker needs: the title and author come from YouTube, and the
 * thumbnail is derived from the id — so no local file is read and no path is stored.
 *
 * Nothing is ever clobbered with a wrong title: a fallback to the stored id only happens after every
 * source has been tried, and a missing title flips the result to [ImportedVideoEnrichment.Retry].
 */
@Singleton
class ImportedVideoEnricher
    @Inject
    constructor() {
        suspend fun enrich(video: DownloadedVideoEntity): ImportedVideoEnrichment {
            val videoId = video.videoId?.takeIf { it.isNotBlank() }
            if (videoId == null) return ImportedVideoEnrichment.Retry

            val id = videoId
            return try {
                val m = YouTubeOEmbedFetcher.fetch(id)
                if (m.title.isNullOrBlank()) {
                    // oEmbed can give an empty title for videos that really have none or that are
                    // out of luck. Either way, stop asking.
                    ImportedVideoEnrichment.Unavailable
                } else {
                    ImportedVideoEnrichment.Enriched(
                        metadata =
                            EnrichedVideoMetadata(
                                title = m.title,
                                author = m.authorName,
                                thumbnailUrl = Constants.buildYouTubeThumbnailUrl(id),
                                durationSeconds = null,
                            ),
                    )
                }
            } catch (e: java.io.IOException) {
                // oEmbed refuses deleted, private or region-blocked videos: nothing to describe.
                ImportedVideoEnrichment.Unavailable
            } catch (e: Exception) {
                AppLogger.error("ImportedVideoEnricher", e, "Metadata request failed for $id")
                ImportedVideoEnrichment.Retry
            }
        }
    }
