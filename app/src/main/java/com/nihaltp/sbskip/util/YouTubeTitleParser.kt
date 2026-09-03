package com.nihaltp.sbskip.util

object YouTubeTitleParser {
    private val AT_REGEX = Regex("@\\s*([\\w.-]+)")

    private val FEAT_REGEX = Regex("(?i)\\b(?:ft\\.|feat\\.)\\s*([^\\|\\[\\]\\(\\)\\-]+)")

    private val ENCLOSED_FLUFF_PATTERNS =
        listOf(
            "m/?v",
            "official( m/?v| video| music video| audio| lyrics?)?",
            "lyrics?",
            "hd",
            "hq",
            "\\d{3,4}p",
            "4k",
            "8k",
            "\\d{4}",
            "audio",
            "visualizer",
            "remastered",
            "full album",
            "album track",
        )

    private val TEXT_FLUFF_PATTERNS =
        listOf(
            "official (video|music video|audio|m/?v|4k music video)",
            "full album",
            "album track",
            "lyric video",
            "music video",
            "m/?v",
        )

    private val ENCLOSED_FLUFF_REGEX = Regex("(?i)[\\(\\[](${ENCLOSED_FLUFF_PATTERNS.joinToString("|")})[\\)\\]]")

    private val TEXT_FLUFF_REGEX = Regex("(?i)\\b(${TEXT_FLUFF_PATTERNS.joinToString("|")})\\b")

    private val FEAT_ARTIST_SPLIT_REGEX = Regex("[,&]")
    private val MULTIPLE_SPACES_REGEX = Regex("\\s{2,}")
    private val FLOATING_SEPARATORS_REGEX = Regex("^[\\s\\-|]+|[\\s\\-|]+\$")

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

        AT_REGEX.findAll(title).forEach { matchResult ->
            artists.add(matchResult.groupValues[1].trim())
        }

        FEAT_REGEX.findAll(title).forEach { matchResult ->
            val featArtistsStr = matchResult.groupValues[1]
            val featArtists = featArtistsStr.split(FEAT_ARTIST_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
            artists.addAll(featArtists)
        }

        return artists.toList()
    }

    /**
     * Removes common YouTube fluff from titles (e.g. [MV], (Official Video), (Lyrics))
     */
    fun cleanTitle(title: String): String {
        var clean = title

        clean = ENCLOSED_FLUFF_REGEX.replace(clean, "")
        clean = TEXT_FLUFF_REGEX.replace(clean, "")

        // Clean up any remaining multiple spaces or floating hyphens/separators that might have been left behind
        clean = clean.replace(MULTIPLE_SPACES_REGEX, " ")
        clean = clean.replace(FLOATING_SEPARATORS_REGEX, "")

        return clean.trim()
    }
}
