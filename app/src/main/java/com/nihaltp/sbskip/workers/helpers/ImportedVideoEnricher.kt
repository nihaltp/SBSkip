package com.nihaltp.sbskip.workers.helpers

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata resolved for an imported video, from its local file and/or from YouTube. */
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
 * A video with exactly one file is read locally. With several files there is no single truth about
 * title and duration, so YouTube is used instead.
 */
@Singleton
class ImportedVideoEnricher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val metadataFetcher: MetadataFetcher,
    ) {
        /**
         * @return the resolved metadata, or null when neither the file nor YouTube provided
         *   anything, in which case the caller should retry later.
         */
        suspend fun enrich(video: DownloadedVideoEntity): EnrichedVideoMetadata? {
            val fileTags = video.outputPaths.singleOrNull()?.let { path -> readFileTags(path) }
            val videoId = video.videoId?.takeIf { it.isNotBlank() }
            val remote =
                if (fileTags == null || fileTags.title == null || fileTags.artist == null) {
                    fetchRemote(videoId)
                } else {
                    null
                }

            if (fileTags == null && remote == null) {
                return null
            }

            return EnrichedVideoMetadata(
                title = fileTags?.title ?: remote?.title?.takeIf { it.isNotBlank() } ?: video.title,
                author = fileTags?.artist ?: remote?.authorName?.takeIf { it.isNotBlank() } ?: video.author,
                thumbnailUrl =
                    video.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: videoId?.let { Constants.buildYouTubeThumbnailUrl(it) },
                durationSeconds = fileTags?.durationSeconds ?: remote?.durationSeconds ?: video.durationSeconds,
            )
        }

        private suspend fun fetchRemote(videoId: String?): YouTubeMetadata? {
            if (videoId == null) return null
            return runCatching {
                metadataFetcher.fetchYouTubeMetadata(Constants.buildYouTubeWatchUrl(videoId))
            }.onFailure { e ->
                AppLogger.error("ImportedVideoEnricher", e, "Metadata request failed for $videoId")
            }.getOrNull()
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
