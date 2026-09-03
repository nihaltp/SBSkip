package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.config.AppVersions
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.util.fetcher.YouTubeDurationFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        private val json = Json { ignoreUnknownKeys = true }

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
                try {
                    val request = Request.Builder().url(videoUrl).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
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
                                    )

                                metadataCache[videoId] = CachedMetadata(cacheVersion, System.currentTimeMillis(), ytMetadata)
                                return@withContext ytMetadata
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.nihaltp.sbskip.util.AppLogger.error("MetadataFetcher", e, "Scraping failed")
                }

                // 2. Fallback to oEmbed
                val oEmbedUrl =
                    videoUrl.toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.scheme("https")
                        ?.host("www.youtube.com")
                        ?.encodedPath("/oembed")
                        ?.addQueryParameter("url", videoUrl)
                        ?.addQueryParameter("format", "json")
                        ?.build()
                        ?: throw IOException("Unable to parse video URL")

                val request = Request.Builder().url(oEmbedUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("oEmbed request failed: ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = json.decodeFromString(YouTubeOEmbedResponse.serializer(), body)
                    val customThumbnailUrl =
                        videoId?.let {
                            com.nihaltp.sbskip.util.Constants.buildYouTubeThumbnailUrl(
                                it,
                            )
                        } ?: parsed.thumbnailUrl

                    val ytMetadata =
                        YouTubeMetadata(
                            title = parsed.title,
                            authorName = parsed.authorName,
                            authorUrl = parsed.authorUrl,
                            thumbnailUrl = customThumbnailUrl,
                        )

                    if (videoId != null) {
                        metadataCache[videoId] = CachedMetadata(cacheVersion, System.currentTimeMillis(), ytMetadata)
                    }
                    ytMetadata
                }
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

@Serializable
private data class YouTubeOEmbedResponse(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

data class YouTubeMetadata(
    val title: String?,
    val authorName: String?,
    val authorUrl: String?,
    val thumbnailUrl: String?,
    val description: String? = null,
    val categoryId: Int? = null,
    val musicVideoType: com.nihaltp.sbskip.model.MusicVideoType? = null,
    val musicConfidence: Float = 0f,
    val musicMetadata: com.nihaltp.sbskip.model.MusicMetadata? = null,
)
