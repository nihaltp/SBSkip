package com.nihaltp.sbskip.downloader

import com.nihaltp.sbskip.model.VideoMetadata
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeMetadataFallbackExtractor @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun extract(url: String): VideoMetadata = withContext(Dispatchers.IO) {
        val normalizedUrl = YouTubeUrlParser.normalize(url) ?: throw IllegalArgumentException("Invalid YouTube URL")
        val videoId = YouTubeUrlParser.extractVideoId(normalizedUrl) ?: throw IllegalArgumentException("Unable to read video id")

        val titleAndThumb = fetchOEmbed(normalizedUrl)
        val durationSeconds = fetchDuration(videoId)
        VideoMetadata(
            title = titleAndThumb.first,
            thumbnailUrl = titleAndThumb.second,
            durationSeconds = durationSeconds,
        )
    }

    private fun fetchOEmbed(url: String): Pair<String, String?> {
        val request = Request.Builder()
            .url("https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(url, Charsets.UTF_8.name())}&format=json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("oEmbed request failed with ${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val title = json.optString("title").ifBlank { throw IllegalStateException("Missing title from oEmbed") }
            return title to json.optString("thumbnail_url").takeIf { it.isNotBlank() }
        }
    }

    private fun fetchDuration(videoId: String): Long? {
        val requestBody = """
            {
              "videoId": "$videoId",
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "2.20250214.00.00"
                }
              }
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.metadata("player api failed with ${response.code}")
                return null
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            return videoDetails.optString("lengthSeconds").toLongOrNull()
        }
    }
}
