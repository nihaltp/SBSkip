package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.config.AppVersions
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSegment
import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.util.NetworkRetry
import com.nihaltp.sbskip.util.fetcher.YouTubeDurationFetcher
import com.nihaltp.sbskip.util.fetcher.YouTubeOEmbedFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

class MetadataFetcher
    @Inject
    constructor(
        private val sponsorBlockService: SponsorBlockService,
    ) {
        private val httpClient = OkHttpClient()

        private val metadataCache = mutableMapOf<String, CachedMetadata>()
        private val cacheVersion = AppVersions.METADATA_CACHE_VERSION

        private data class CachedMetadata(
            val version: Int,
            val timestamp: Long,
            val metadata: YouTubeMetadata,
        )

        suspend fun fetchYouTubeMetadata(videoUrl: String): YouTubeMetadata =
            withContext(Dispatchers.IO) {
                val videoId = com.nihaltp.sbskip.util.parser.YouTubeUrlParser.extractVideoId(videoUrl)
                if (videoId != null) {
                    val cached = metadataCache[videoId]
                    if (cached != null &&
                        cached.version == cacheVersion &&
                        (System.currentTimeMillis() - cached.timestamp < 3600000)
                    ) { // 1 hour cache
                        return@withContext cached.metadata
                    }
                }

                // 1. Try Scraping via HTML
                val scrapedMetadata =
                    try {
                        NetworkRetry.execute {
                            val request = Request.Builder().url(videoUrl).build()
                            httpClient.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) {
                                    throw IOException("YouTube metadata request failed: ${response.code}")
                                }
                                val body = response.body?.string().orEmpty()
                                val extractionResult = com.nihaltp.sbskip.util.parser.YouTubeMetadataParser.parse(body)

                                if (videoId != null) {
                                    // Scraped basic info from microformat/videoDetails might be limited if the parser didn't find ytInitialPlayerResponse.
                                    // If parser returns good confidence or basic metadata is present, use it.
                                    // YouTubeMetadata needs title, author, thumbnail
                                    val customThumbnailUrl = com.nihaltp.sbskip.util.Constants.buildYouTubeThumbnailUrl(videoId)

                                    val ytMetadata =
                                        YouTubeMetadata(
                                            title = extractionResult.rawTitle ?: extractionResult.musicMetadata?.title,
                                            authorName =
                                                extractionResult.rawChannelName
                                                    ?: extractionResult.musicMetadata?.artists?.firstOrNull(),
                                            authorUrl = null,
                                            thumbnailUrl = customThumbnailUrl,
                                            description = extractionResult.rawDescription,
                                            categoryId = extractionResult.rawCategoryId,
                                            musicVideoType = extractionResult.type,
                                            musicConfidence = extractionResult.confidence,
                                            musicMetadata = extractionResult.musicMetadata,
                                            durationSeconds = extractionResult.rawDurationSeconds,
                                        )

                                    metadataCache[videoId] = CachedMetadata(cacheVersion, System.currentTimeMillis(), ytMetadata)
                                    ytMetadata
                                } else {
                                    null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        com.nihaltp.sbskip.util.AppLogger.error("MetadataFetcher", e, "Scraping failed")
                        null
                    }
                if (scrapedMetadata != null) return@withContext scrapedMetadata

                // 2. Fallback to oEmbed
                if (videoId == null) {
                    throw IOException("Unable to read a video id from $videoUrl")
                }
                val oEmbedMetadata = YouTubeOEmbedFetcher.fetch(videoId)
                metadataCache[videoId] =
                    CachedMetadata(cacheVersion, System.currentTimeMillis(), oEmbedMetadata)
                oEmbedMetadata
            }

        suspend fun fetchSponsorSegments(
            videoId: String,
            categories: Set<SponsorBlockCategory>,
        ): List<SponsorBlockSegment> {
            return sponsorBlockService.fetchSegments(videoId, categories)
        }

        suspend fun fetchVideoDuration(videoId: String): Long? {
            return YouTubeDurationFetcher.fetchDuration(videoId)
        }

        suspend fun checkApiStatus(): String {
            return sponsorBlockService.checkApiStatus()
        }
    }
