package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.model.PlaylistVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object YouTubePlaylistFetcher {
    private val httpClient = OkHttpClient()

    /**
     * Fetches the HTML of a YouTube playlist and attempts to extract the list of videos.
     * Note: This relies on HTML scraping (`ytInitialData`) which is somewhat brittle.
     */
    suspend fun fetchPlaylistVideos(playlistId: String): List<PlaylistVideo> =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            val request =
                Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to fetch playlist: ${response.code}")
                }
                val html = response.body?.string() ?: throw IOException("Empty response body")
                parsePlaylistHtml(html)
            }
        }

    internal fun parsePlaylistHtml(html: String): List<PlaylistVideo> {
        val videos = mutableListOf<PlaylistVideo>()

        // Find ytInitialData
        val initialDataMatch =
            Regex("var ytInitialData = (\\{.*?\\});</script>", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("window\\[\"ytInitialData\"\\] = (\\{.*?\\});", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: return emptyList()

        val jsonString = initialDataMatch.groupValues[1]

        // Use regex to find all playlistVideoRenderer objects (older UI)
        val rendererPattern = Regex("\"playlistVideoRenderer\":\\{(.*?)\"isPlayable\"", RegexOption.DOT_MATCHES_ALL)
        val matches = rendererPattern.findAll(jsonString)

        for (match in matches) {
            val content = match.groupValues[1]

            val videoIdMatch = Regex("\"videoId\":\"([^\"]+)\"").find(content)
            val titleMatch = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]\\}", RegexOption.DOT_MATCHES_ALL).find(content)

            if (videoIdMatch != null && titleMatch != null) {
                val videoId = videoIdMatch.groupValues[1]
                val title = titleMatch.groupValues[1].replace("\\u0026", "&").replace("\\\"", "\"")
                val thumbnailUrl = Constants.buildYouTubeThumbnailUrl(videoId)

                if (videos.none { it.videoId == videoId }) {
                    videos.add(PlaylistVideo(videoId, title, thumbnailUrl))
                }
            }
        }

        // Try lockupViewModel format (newer UI)
        val lockupPattern = Regex("\"lockupViewModel\":\\{(.*?)\"contentType\"", RegexOption.DOT_MATCHES_ALL)
        val lockupMatches = lockupPattern.findAll(jsonString)

        for (match in lockupMatches) {
            val content = match.groupValues[1]
            val videoIdMatch = Regex("\"contentId\":\"([^\"]+)\"").find(content)
            val titleMatch = Regex("\"title\":\\{\"content\":\"(.*?)\"\\}", RegexOption.DOT_MATCHES_ALL).find(content)

            if (videoIdMatch != null && titleMatch != null) {
                val videoId = videoIdMatch.groupValues[1]
                val title = titleMatch.groupValues[1].replace("\\u0026", "&").replace("\\\"", "\"")
                val thumbnailUrl = Constants.buildYouTubeThumbnailUrl(videoId)

                if (videos.none { it.videoId == videoId }) {
                    videos.add(PlaylistVideo(videoId, title, thumbnailUrl))
                }
            }
        }

        return videos
    }
}
