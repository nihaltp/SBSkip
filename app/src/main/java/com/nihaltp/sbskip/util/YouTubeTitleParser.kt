package com.nihaltp.sbskip.util

object YouTubeTitleParser {
    /**
     * Extracts artists from a YouTube title using strict heuristics (e.g. looking for @ handles and ft./feat.).
     * Designed to be expanded with more sophisticated parsing rules later.
     */
    fun extractArtistsFromTitle(
        title: String,
        authorName: String?,
    ): List<String> {
        val artists = mutableSetOf<String>()

        authorName?.takeIf { it.isNotBlank() }?.let { artists.add(it.trim()) }

        val atRegex = Regex("@\\s*([\\w.-]+)")
        atRegex.findAll(title).forEach { matchResult ->
            artists.add(matchResult.groupValues[1].trim())
        }

        val featRegex = Regex("(?i)\\b(?:ft\\.|feat\\.)\\s*([^\\|\\[\\]\\(\\)\\-]+)")
        featRegex.findAll(title).forEach { matchResult ->
            val featArtistsStr = matchResult.groupValues[1]
            val featArtists = featArtistsStr.split(Regex("[,&]")).map { it.trim() }.filter { it.isNotEmpty() }
            artists.addAll(featArtists)
        }

        return artists.toList()
    }
}
