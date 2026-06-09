package com.nihaltp.sbskip.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object YouTubeDurationFetcher {
    private val client = OkHttpClient()

    suspend fun fetchDuration(videoId: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val request =
                    Request.Builder()
                        .url(url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36",
                        )
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error ${response.code}")
                    }
                    val html = response.body?.string().orEmpty()
                    return@withContext parseDurationFromHtml(html)
                }
            } catch (e: Exception) {
                AppLogger.error("YouTubeDurationFetcher", e, "Failed to scrape YouTube video duration for videoId=$videoId")
            }
            return@withContext null
        }

    fun parseDurationFromHtml(html: String): Long? {
        // 1. Try matching "lengthSeconds":"123" inside embedded InitialPlayerResponse JSON config
        val lengthSecondsMatch = Regex("\"lengthSeconds\"\\s*:\\s*\"(\\d+)\"").find(html)
        if (lengthSecondsMatch != null) {
            return lengthSecondsMatch.groupValues[1].toLongOrNull()
        }

        // 2. Fallback to approxDurationMs
        val approxDurationMsMatch = Regex("\"approxDurationMs\"\\s*:\\s*\"(\\d+)\"").find(html)
        if (approxDurationMsMatch != null) {
            val ms = approxDurationMsMatch.groupValues[1].toLongOrNull() ?: 0L
            return ms / 1000L
        }

        // 3. Alternate format meta tag duration e.g. itemprop="duration" content="PT3M45S"
        val metaDurationMatch = Regex("itemprop=\"duration\"\\s+content=\"PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?\"").find(html)
        if (metaDurationMatch != null) {
            val hours = metaDurationMatch.groupValues[1].toLongOrNull() ?: 0L
            val minutes = metaDurationMatch.groupValues[2].toLongOrNull() ?: 0L
            val seconds = metaDurationMatch.groupValues[3].toLongOrNull() ?: 0L
            return hours * 3600 + minutes * 60 + seconds
        }
        return null
    }
}
