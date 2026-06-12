package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.util.YouTubeDurationFetcher
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

        suspend fun fetchYouTubeMetadata(videoUrl: String): YouTubeMetadata =
            withContext(Dispatchers.IO) {
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
                    YouTubeMetadata(
                        title = parsed.title,
                        authorName = parsed.authorName,
                        authorUrl = parsed.authorUrl,
                        thumbnailUrl = parsed.thumbnailUrl,
                    )
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
)
