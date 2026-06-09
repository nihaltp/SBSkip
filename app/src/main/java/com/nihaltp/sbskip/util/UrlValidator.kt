package com.nihaltp.sbskip.util

import android.net.Uri

object UrlValidator {
    private val supportedHosts =
        setOf(
            "youtube.com",
            "m.youtube.com",
            "www.youtube.com",
            "youtu.be",
        )

    fun normalize(input: String): String? {
        val candidate = input.trim()
        if (candidate.isEmpty()) return null

        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host !in supportedHosts) return null
        return candidate
    }

    fun isSupportedYouTubeUrl(input: String): Boolean = normalize(input) != null
}
