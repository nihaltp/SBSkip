package com.nihaltp.sbskip.metadata

import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import kotlinx.serialization.json.Json

/**
 * Shared utility for extracting YouTube metadata from ffprobe tags when SBSKIP_* tags are not present.
 * Used by both [SbskipMetadataReader] and [FfmpegMetadataReader].
 */
object YouTubeMetadataExtractor {
    private val json = Json { ignoreUnknownKeys = true }

    data class ExtractedMetadata(
        val youtubeId: String?,
        val youtubeUrl: String?,
        val youtubeTitle: String?,
        val authorName: String?,
        val authorUrl: String?,
        val thumbnailUrl: String?,
    )

    /**
     * Attempts to extract YouTube metadata from common tags that might contain a YouTube URL.
     * Returns null if no YouTube ID could be extracted.
     *
     * @param tags Map of tag key-value pairs from ffprobe output
     * @param fallbackTitle Title to use if no youtubeTitle is found (e.g., from 'title' tag or filename)
     * @return ExtractedMetadata if YouTube ID found, null otherwise
     */
    fun extractFromTags(
        tags: Map<String, String>,
        fallbackTitle: String? = null,
    ): ExtractedMetadata? {
        // Check common tags that might contain a YouTube URL
        val urlCandidateTags = listOf("comment", "description", "purl", "source", "www")

        for (tagKey in urlCandidateTags) {
            val tagValue = tags[tagKey]?.takeIf { it.isNotBlank() }
            if (tagValue != null) {
                val extractedId = YouTubeUrlParser.extractVideoId(tagValue)
                if (extractedId != null) {
                    val normalizedUrl = YouTubeUrlParser.normalize(tagValue) ?: "https://www.youtube.com/watch?v=$extractedId"
                    val title = tags["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle
                    val author = tags["artist"]?.takeIf { it.isNotBlank() } ?: tags["author"]?.takeIf { it.isNotBlank() }
                    val thumbnail = Constants.buildYouTubeThumbnailUrl(extractedId)

                    AppLogger.metadata(
                        "YouTubeMetadataExtractor: Extracted YouTube ID=$extractedId from tag '$tagKey'",
                    )

                    return ExtractedMetadata(
                        youtubeId = extractedId,
                        youtubeUrl = normalizedUrl,
                        youtubeTitle = title,
                        authorName = author,
                        authorUrl = null, // Not available from fallback tags
                        thumbnailUrl = thumbnail,
                    )
                }
            }
        }

        return null
    }

    /**
     * Attempts to parse YouTube metadata from the `comment` tag as JSON.
     * This is the format that [MediaTagger] writes for already-processed files.
     */
    @Suppress("UNUSED_PARAMETER")
    fun extractFromCommentJson(comment: String): ExtractedMetadata? {
        val unescaped = comment.replace("\\\"", "\"")
        try {
            @Suppress("UNUSED_PARAMETER")
            @kotlinx.serialization.Serializable
            data class CommentMetadata(
                val youtubeId: String?,
                val youtubeUrl: String?,
                val youtubeTitle: String?,
                val authorName: String?,
            )

            val commentData = json.decodeFromString<CommentMetadata>(unescaped)
            if (commentData.youtubeId != null) {
                return ExtractedMetadata(
                    youtubeId = commentData.youtubeId,
                    youtubeUrl = commentData.youtubeUrl,
                    youtubeTitle = commentData.youtubeTitle,
                    authorName = commentData.authorName,
                    authorUrl = null,
                    thumbnailUrl = commentData.youtubeId?.let { Constants.buildYouTubeThumbnailUrl(it) },
                )
            }
        } catch (e: Exception) {
            AppLogger.metadata("YouTubeMetadataExtractor: Failed to parse comment JSON: ${e.message}")
        }
        return null
    }

    /**
     * Main entry point: tries SBSKIP_* tags first, then legacy tags, then comment JSON, then fallback tags.
     * Returns [ExtractedMetadata] if any YouTube ID found, null otherwise.
     */
    fun extract(
        tags: Map<String, String>,
        fallbackTitle: String? = null,
    ): ExtractedMetadata? {
        // 1. Try SBSKIP_* tags directly (primary)
        val sbskipId = tags["SBSKIP_YOUTUBE_VIDEO_ID"]?.takeIf { it.isNotBlank() }
        if (sbskipId != null) {
            return ExtractedMetadata(
                youtubeId = sbskipId,
                youtubeUrl = tags["SBSKIP_YOUTUBE_VIDEO_URL"]?.takeIf { it.isNotBlank() },
                youtubeTitle = tags["SBSKIP_YOUTUBE_TITLE"]?.takeIf { it.isNotBlank() },
                authorName = tags["SBSKIP_AUTHOR_NAME"]?.takeIf { it.isNotBlank() },
                authorUrl = tags["SBSKIP_AUTHOR_URL"]?.takeIf { it.isNotBlank() },
                thumbnailUrl = tags["SBSKIP_THUMBNAIL_URL"]?.takeIf { it.isNotBlank() },
            )
        }

        // 2. Try legacy YOUTUBE_* tags
        val legacyId = tags["YOUTUBE_VIDEO_ID"]?.takeIf { it.isNotBlank() }
        if (legacyId != null) {
            return ExtractedMetadata(
                youtubeId = legacyId,
                youtubeUrl = tags["YOUTUBE_VIDEO_URL"]?.takeIf { it.isNotBlank() },
                youtubeTitle = tags["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                authorName = tags["author"]?.takeIf { it.isNotBlank() },
                authorUrl = null,
                thumbnailUrl = Constants.buildYouTubeThumbnailUrl(legacyId),
            )
        }

        // 3. Try comment JSON (where MediaTagger writes for already-processed files)
        val comment = tags["comment"]?.takeIf { it.isNotBlank() }
        comment?.let {
            val fromJson = extractFromCommentJson(it)
            if (fromJson != null) return fromJson
        }

        // 4. Fallback: extract from common tags containing plain YouTube URLs
        return extractFromTags(tags, fallbackTitle)
    }
}
