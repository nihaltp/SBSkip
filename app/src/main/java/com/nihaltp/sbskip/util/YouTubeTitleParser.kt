package com.nihaltp.sbskip.util

object YouTubeTitleParser {
    /**
     * Extracts artists from a YouTube title using strict heuristics (e.g. looking for @ handles and ft./feat.).
     * Designed to be expanded with more sophisticated parsing rules later.
     */
    fun extractArtistsFromTitle(
        rawTitle: String,
        authorName: String?,
    ): List<String> {
        val title = cleanTitle(rawTitle)
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

    /**
     * Removes common YouTube fluff from titles (e.g. [MV], (Official Video), (Lyrics))
     */
    fun cleanTitle(title: String): String {
        var clean = title

        // Patterns to match enclosed fluff like [MV], (Official Video), (1080p), (1999)
        val enclosedFluffRegex =
            Regex(
                "(?i)[\\(\\[](m/?v|official( m/?v| video| music video| audio| lyrics?)?|" +
                    "lyrics?|hd|hq|\\d{3,4}p|4k|8k|\\d{4}|audio|visualizer|" +
                    "remastered|full album|album track)[\\)\\]]",
            )

        // Patterns for non-enclosed fluff
        val textFluffRegex =
            Regex(
                "(?i)\\b(official (video|music video|audio|m/?v|4k music video)|" +
                    "full album|album track|lyric video|music video|m/?v)\\b",
            )

        clean = enclosedFluffRegex.replace(clean, "")
        clean = textFluffRegex.replace(clean, "")

        // Clean up any remaining multiple spaces or floating hyphens/separators that might have been left behind
        clean = clean.replace(Regex("\\s{2,}"), " ")
        clean = clean.replace(Regex("^[\\s\\-|]+|[\\s\\-|]+\$"), "")

        return clean.trim()
    }
}
