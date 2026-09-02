package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.model.MusicMetadata
import com.nihaltp.sbskip.model.MusicVideoType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object YouTubeMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    data class ExtractionResult(
        val musicMetadata: MusicMetadata?,
        val confidence: Float,
        val type: MusicVideoType?,
        val rawTitle: String?,
        val rawChannelName: String?,
        val rawDescription: String?,
        val rawCategoryId: Int?,
    )

    fun parse(html: String): ExtractionResult {
        val ytInitialDataStr =
            extractJsonObjectString(html, "var ytInitialData")
                ?: extractJsonObjectString(html, "window\\[\"ytInitialData\"\\]")
        val ytInitialPlayerResponseStr =
            extractJsonObjectString(html, "var ytInitialPlayerResponse")
                ?: extractJsonObjectString(html, "ytcfg.set({PLAYER_VARS: {ytInitialPlayerResponse: ") // a bit complex for regex

        var title: String? = null
        var description: String? = null
        var categoryId: Int? = null
        var channelName: String? = null

        var initialData: JsonObject? = null
        if (ytInitialDataStr != null) {
            try {
                initialData = json.parseToJsonElement(ytInitialDataStr).jsonObject
            } catch (e: Exception) {
                AppLogger.error("YouTubeMetadataParser", e, "Failed to parse ytInitialData")
            }
        }

        var playerResponse: JsonObject? = null
        if (ytInitialPlayerResponseStr != null) {
            try {
                playerResponse = json.parseToJsonElement(ytInitialPlayerResponseStr).jsonObject
            } catch (e: Exception) {
                AppLogger.error("YouTubeMetadataParser", e, "Failed to parse ytInitialPlayerResponse")
            }
        }

        // Extract basic info
        playerResponse?.let { pr ->
            val videoDetails = pr["videoDetails"]?.jsonObject
            title = videoDetails?.get("title")?.jsonPrimitive?.content
            description = videoDetails?.get("shortDescription")?.jsonPrimitive?.content
            channelName = videoDetails?.get("author")?.jsonPrimitive?.content

            val microformat =
                pr["microformat"]?.jsonObject
                    ?.get("playerMicroformatRenderer")?.jsonObject
            categoryId =
                microformat?.get("category")?.jsonPrimitive?.content?.let { catStr ->
                    null
                }
        }

        // Try fallback for basic info from initialData
        // TODO: expand if needed

        // Extract Music Metadata
        val (metadata, confidenceBoost) =
            extractStructuredMusicMetadata(initialData)
                ?: extractFromDescription(description)
                ?: extractFromTitle(title, channelName)
                ?: Pair(null, 0f)

        val finalConfidence =
            calculateConfidence(
                title = title,
                description = description,
                categoryId = categoryId,
                baseConfidence = confidenceBoost,
            )

        val type = determineMusicVideoType(title, description)

        return ExtractionResult(
            musicMetadata = metadata,
            confidence = finalConfidence.coerceIn(0f, 1f),
            type = type,
            rawTitle = title,
            rawChannelName = channelName,
            rawDescription = description,
            rawCategoryId = categoryId,
        )
    }

    private fun extractStructuredMusicMetadata(initialData: JsonObject?): Pair<MusicMetadata, Float>? {
        if (initialData == null) return null

        val rows = mutableListOf<JsonObject>()
        findRenderers(initialData, "metadataRowRenderer", rows)

        if (rows.isEmpty()) return null

        var song: String? = null
        val artists = mutableListOf<String>()
        var album: String? = null
        var year: Int? = null

        for (row in rows) {
            val title = extractTextFromElement(row["title"])?.lowercase() ?: continue
            val contents = row["contents"]?.jsonArray ?: continue
            if (contents.isEmpty()) continue
            val contentText = extractTextFromElement(contents[0])

            when {
                title.contains("song") -> song = contentText
                title.contains("artist") -> contentText?.let { artists.add(it) }
                title.contains("album") -> album = contentText
                title.contains("year") -> year = contentText?.toIntOrNull()
            }
        }

        if (song != null || artists.isNotEmpty()) {
            return Pair(
                MusicMetadata(
                    title = song,
                    artists = artists,
                    album = album,
                    albumArtist = null,
                    year = year,
                    genre = null,
                    artworkUrl = null,
                ),
                0.9f, // Very high confidence for structured metadata
            )
        }
        return null
    }

    private fun extractFromDescription(description: String?): Pair<MusicMetadata, Float>? {
        if (description == null) return null

        // Fallback Description patterns
        // e.g. "Artist: The Weeknd\nSong: Blinding Lights\nAlbum: After Hours"
        var song: String? = null
        var artist: String? = null
        var album: String? = null

        description.lines().forEach { line ->
            val lowerLine = line.lowercase()
            when {
                lowerLine.startsWith("artist: ") -> artist = line.substring(8).trim()
                lowerLine.startsWith("song: ") -> song = line.substring(6).trim()
                lowerLine.startsWith("album: ") -> album = line.substring(7).trim()
                lowerLine.startsWith("track: ") -> song = line.substring(7).trim()
            }
        }

        if (song != null || artist != null) {
            return Pair(
                MusicMetadata(
                    title = song,
                    artists = artist?.let { listOf(it) } ?: emptyList(),
                    album = album,
                    albumArtist = null,
                    year = null,
                    genre = null,
                    artworkUrl = null,
                ),
                0.6f,
            )
        }
        return null
    }

    private fun extractFromTitle(
        title: String?,
        channelName: String?,
    ): Pair<MusicMetadata, Float>? {
        if (title == null) return null
        // e.g., "Artist - Song (Official Video)"
        val split = title.split(" - ", limit = 2)
        if (split.size == 2) {
            val parsedArtist = split[0].trim()
            var parsedSong = split[1].trim()

            // Clean up song title (e.g., remove "(Official Video)")
            val bracketIndex = parsedSong.indexOf('(')
            if (bracketIndex > 0) {
                parsedSong = parsedSong.substring(0, bracketIndex).trim()
            }

            return Pair(
                MusicMetadata(
                    title = parsedSong,
                    artists = listOf(parsedArtist),
                    album = null,
                    albumArtist = null,
                    year = null,
                    genre = null,
                    artworkUrl = null,
                ),
                0.2f, // Title alone is low confidence
            )
        }
        return null
    }

    private fun calculateConfidence(
        title: String?,
        description: String?,
        categoryId: Int?,
        baseConfidence: Float,
    ): Float {
        var score = baseConfidence

        val titleLower = title?.lowercase() ?: ""
        val descLower = description?.lowercase() ?: ""

        // Check Category 10
        // Wait, if categoryId is passed as int, we check it.
        if (categoryId == 10) {
            score += 0.2f
        } else if (categoryId != null && categoryId != 10) {
            // Negative signal for non-music category, but let's be careful
        }

        val positiveKeywords = Constants.positiveMusicKeywords
        val negativeKeywords = Constants.negativeMusicKeywords

        if (positiveKeywords.any { titleLower.contains(it) }) score += 0.3f
        if (negativeKeywords.any { titleLower.contains(it) }) score -= 0.8f

        if (positiveKeywords.any { descLower.contains(it) }) score += 0.1f
        if (negativeKeywords.any { descLower.contains(it) }) score -= 0.3f

        return score
    }

    private fun determineMusicVideoType(
        title: String?,
        description: String?,
    ): MusicVideoType? {
        val textLower = ((title ?: "") + " " + (description ?: "")).lowercase()
        return when {
            textLower.contains("official music video") || textLower.contains("official video") -> MusicVideoType.OFFICIAL_MUSIC_VIDEO
            textLower.contains("official audio") -> MusicVideoType.OFFICIAL_AUDIO
            textLower.contains("lyric video") || textLower.contains("lyrics") -> MusicVideoType.LYRIC_VIDEO
            textLower.contains("live performance") || textLower.contains("live at") || textLower.contains("concert") -> MusicVideoType.LIVE
            textLower.contains("performance") -> MusicVideoType.PERFORMANCE
            else -> null // OTHER or undetermined
        }
    }

    private fun extractTextFromElement(element: JsonElement?): String? {
        if (element == null) return null
        return try {
            val simpleText = element.jsonObject["simpleText"]?.jsonPrimitive?.content
            if (simpleText != null) return simpleText

            val runs = element.jsonObject["runs"]?.jsonArray
            if (runs != null) {
                return runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findRenderers(
        element: JsonElement,
        targetKey: String,
        results: MutableList<JsonObject>,
    ) {
        when (element) {
            is JsonObject -> {
                for ((k, v) in element) {
                    if (k == targetKey && v is JsonObject) {
                        results.add(v)
                    }
                    findRenderers(v, targetKey, results)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findRenderers(item, targetKey, results)
                }
            }
            else -> {}
        }
    }

    fun extractJsonObjectString(
        html: String,
        varPrefix: String,
    ): String? {
        var searchStr = "$varPrefix = {"
        var startIdx = html.indexOf(searchStr)
        if (startIdx == -1) {
            searchStr = "$varPrefix\":{"
            startIdx = html.indexOf(searchStr)
            if (startIdx == -1) return null
        }

        val jsonStartIdx = startIdx + searchStr.length - 1
        var braceCount = 0
        var inString = false
        var escapeNext = false

        for (i in jsonStartIdx until html.length) {
            val c = html[i]
            if (escapeNext) {
                escapeNext = false
                continue
            }
            if (c == '\\') {
                escapeNext = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') braceCount++
                if (c == '}') {
                    braceCount--
                    if (braceCount == 0) {
                        return html.substring(jsonStartIdx, i + 1)
                    }
                }
            }
        }
        return null
    }
}
