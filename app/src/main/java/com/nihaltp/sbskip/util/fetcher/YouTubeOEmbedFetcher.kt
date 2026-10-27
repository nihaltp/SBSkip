package com.nihaltp.sbskip.util.fetcher

import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.NetworkRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads a video's title, author and thumbnail from YouTube's oEmbed endpoint.
 *
 * oEmbed is the cheap way to describe a video: a small JSON document instead of the watch page that
 * the download flow scrapes. It is the right choice wherever only a title, an author and a cover
 * are shown; callers that also need the description, category or length have to scrape the watch
 * page themselves.
 */
object YouTubeOEmbedFetcher {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return the video's metadata. The thumbnail is derived from [videoId] rather than taken from
     *   the response, so it is always present and always the size the list expects.
     * @throws IOException when the video cannot be described, which includes the videos oEmbed
     *   refuses: private, deleted, or blocked in the current region.
     */
    suspend fun fetch(videoId: String): YouTubeMetadata =
        withContext(Dispatchers.IO) {
            NetworkRetry.execute {
                val request = Request.Builder().url(oEmbedUrl(videoId)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("oEmbed request failed: ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = json.decodeFromString(YouTubeOEmbedResponse.serializer(), body)
                    YouTubeMetadata(
                        title = parsed.title,
                        authorName = parsed.authorName,
                        authorUrl = parsed.authorUrl,
                        thumbnailUrl = Constants.buildYouTubeThumbnailUrl(videoId),
                    )
                }
            }
        }

    private fun oEmbedUrl(videoId: String): HttpUrl =
        O_EMBED_ENDPOINT
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("url", Constants.buildYouTubeWatchUrl(videoId))
            .addQueryParameter("format", "json")
            .build()

    private const val O_EMBED_ENDPOINT = "https://www.youtube.com/oembed"

    @Serializable
    private data class YouTubeOEmbedResponse(
        val title: String? = null,
        @SerialName("author_name") val authorName: String? = null,
        @SerialName("author_url") val authorUrl: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    )
}
