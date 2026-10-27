package com.nihaltp.sbskip.workers.helpers

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.fetcher.YouTubeOEmbedFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Metadata resolved for an imported video, from its local file and/or YouTube. */
data class EnrichedVideoMetadata(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)

/**
 * Resolves the metadata of an imported video, preferring what the local file already carries and
 * asking YouTube only for whatever the file cannot provide.
 *
 * A video with a single file that exposes a title and an artist is read locally, which costs
 * nothing and often beats YouTube's description. Videos with several files — or a file whose
 * embedded title and artist were blank — ask YouTube via oEmbed instead. Thumbnails are never a
 * network request: they are derived from the video id.
 *
 * Nothing is ever clobbered with a wrong title: a fallback to the stored id only happens after every
 * source has been tried, and a missing title flips the result to [ImportedVideoEnrichment.Retry].
 */
@Singleton
class ImportedVideoEnricher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val metadataFetcher: MetadataFetcher,
    ) {
        suspend fun enrich(video: DownloadedVideoEntity): ImportedVideoEnrichment {
            val filePath = video.outputPaths.singleOrNull()
            val fileTags = filePath?.let { readFileTags(it) }
            val needsRemote = fileTags == null || fileTags.title == null || fileTags.artist == null

            val videoId = video.videoId?.takeIf { it.isNotBlank() }
            if (!needsRemote && videoId == null) {
                // A single local file carries everything, but without a video id there is nowhere
                // to derive the thumbnail from — the row must still wait for enrichment.
                return ImportedVideoEnrichment.Retry
            }

            val remote = if (needsRemote) fetchRemote(videoId) else null

            return when (remote) {
                is ImportedVideoEnrichment.Enriched -> {
                    val metadata =
                        if (fileTags?.title?.isNotBlank() == true) {
                            EnrichedVideoMetadata(
                                title = fileTags!!.title,
                                author = fileTags.artist,
                                thumbnailUrl = Constants.buildYouTubeThumbnailUrl(videoId!!),
                                durationSeconds = fileTags.durationSeconds,
                            )
                        } else {
                            EnrichedVideoMetadata(
                                title = remote.metadata.title,
                                author = remote.metadata.author,
                                thumbnailUrl = Constants.buildYouTubeThumbnailUrl(videoId!!),
                                durationSeconds =
                                    fileTags?.durationSeconds
                                        ?: remote.metadata.durationSeconds,
                            )
                        }
                    ImportedVideoEnrichment.Enriched(metadata = metadata)
                }

                ImportedVideoEnrichment.Unavailable -> ImportedVideoEnrichment.Unavailable

                ImportedVideoEnrichment.Retry -> ImportedVideoEnrichment.Retry

                null -> ImportedVideoEnrichment.Retry
            }
        }

        private suspend fun fetchRemote(videoId: String?): ImportedVideoEnrichment {
            if (videoId == null) return ImportedVideoEnrichment.Retry
            val id = videoId
            try {
                val m = YouTubeOEmbedFetcher.fetch(id)
                if (m.title.isNullOrBlank()) {
                    // oEmbed can give an empty description even when the title looks trivial:
                    // either the video really has no title or we are out of luck. Either way, stop.
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
                AppLogger.error("ImportedVideoEnricher", e, "Metadata request failed for $videoId")
                ImportedVideoEnrichment.Retry
            }
            // The last arm above (IOException -> Unavailable) makes this reachable only for
            // unexpected exceptions after the path that produced Enriched or Retry already returned.
            return ImportedVideoEnrichment.Retry
        }

        private suspend fun readFileTags(path: String): FileTags? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(path)
                    val retriever = MediaMetadataRetriever()
                    try {
                        when (uri.scheme) {
                            null -> retriever.setDataSource(path)

                            ContentResolver.SCHEME_FILE -> retriever.setDataSource(uri.path ?: path)

                            else -> retriever.setDataSource(context, uri)
                        }
                        FileTags(
                            title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                            artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                            durationSeconds =
                                retriever
                                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()
                                    ?.div(1000)
                                    ?.takeIf { seconds -> seconds > 0 },
                        )
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }

        private fun MediaMetadataRetriever.metadata(key: Int): String? = extractMetadata(key)?.takeIf { value -> value.isNotBlank() }

        private data class FileTags(
            val title: String?,
            val artist: String?,
            val durationSeconds: Long?,
        )
    }
