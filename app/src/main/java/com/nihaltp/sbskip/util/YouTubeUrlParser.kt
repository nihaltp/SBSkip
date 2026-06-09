package com.nihaltp.sbskip.util

import android.net.Uri

object YouTubeUrlParser {
    private val supportedHosts =
        setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
        )

    fun normalize(rawInput: String): String? {
        val extracted = extractCandidateUrl(rawInput) ?: return null
        val videoId = extractVideoId(extracted) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun extractVideoId(rawInput: String): String? {
        val url = extractCandidateUrl(rawInput) ?: return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in supportedHosts) return null

        return when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull().orEmpty().takeIf { it.isNotBlank() }
            uri.pathSegments.firstOrNull() == "shorts" -> uri.pathSegments.getOrNull(1)
            uri.pathSegments.firstOrNull() == "embed" -> uri.pathSegments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
    }

    fun isYouTubeUrl(rawInput: String): Boolean = normalize(rawInput) != null

    private fun extractCandidateUrl(rawInput: String): String? {
        val trimmed = rawInput.trim().trim('"', '\'', '(', ')', '[', ']', ',', '.', ';')
        if (trimmed.isBlank()) return null

        val direct =
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else if (trimmed.startsWith(
                    "www.",
                    ignoreCase = true,
                ) || trimmed.startsWith("youtube.com", ignoreCase = true) || trimmed.startsWith("youtu.be", ignoreCase = true)
            ) {
                "https://$trimmed"
            } else {
                Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(trimmed)?.value
            }

        if (direct.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(direct) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return if (host in supportedHosts) direct else null
    }
}
