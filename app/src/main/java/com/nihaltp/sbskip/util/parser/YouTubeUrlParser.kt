package com.nihaltp.sbskip.util.parser

import com.nihaltp.sbskip.util.Constants
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object YouTubeUrlParser {
    private val supportedHosts =
        setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com",
            "youtu.be",
        )
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    fun normalize(rawInput: String): String? {
        val extracted = extractCandidateUrl(rawInput) ?: return null
        val videoId = extractVideoId(extracted) ?: return null
        return Constants.buildYouTubeWatchUrl(videoId)
    }

    fun extractVideoId(rawInput: String): String? {
        val url = extractCandidateUrl(rawInput) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in supportedHosts) return null
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

        return when {
            host == "youtu.be" -> pathSegments.firstOrNull()
            pathSegments.firstOrNull() in setOf("shorts", "embed", "v") -> pathSegments.getOrNull(1)
            else -> queryParameter(uri, "v")
        }?.takeIf(::isValidVideoId)
    }

    fun extractPlaylistId(rawInput: String): String? {
        val url = extractCandidateUrl(rawInput) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in supportedHosts) return null

        return queryParameter(uri, "list")
    }

    fun isYouTubeUrl(rawInput: String): Boolean = normalize(rawInput) != null || extractPlaylistId(rawInput) != null

    private fun extractCandidateUrl(rawInput: String): String? {
        val trimmed = rawInput.trim().trim('"', '\'', '(', ')', '[', ']', ',', '.', ';')
        if (trimmed.isBlank()) return null

        if (isValidVideoId(trimmed)) return "https://www.youtube.com/watch?v=$trimmed"

        val direct =
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else if (trimmed.startsWith(
                    "www.",
                    ignoreCase = true,
                ) || trimmed.startsWith("youtube.com", ignoreCase = true) || trimmed.startsWith("youtu.be", ignoreCase = true) ||
                trimmed.startsWith("m.youtube.com", ignoreCase = true) ||
                trimmed.startsWith("music.youtube.com", ignoreCase = true) ||
                trimmed.startsWith("youtube-nocookie.com", ignoreCase = true)
            ) {
                "https://$trimmed"
            } else {
                Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(trimmed)?.value
            }

        if (direct.isNullOrBlank()) return null
        val uri = runCatching { URI(direct) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return if (host in supportedHosts) direct else null
    }

    private fun queryParameter(
        uri: URI,
        name: String,
    ): String? =
        uri.rawQuery
            ?.split('&')
            ?.asSequence()
            ?.map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
            ?.firstOrNull { it.first == name }
            ?.second
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

    private fun isValidVideoId(value: String): Boolean = videoIdPattern.matches(value)
}
