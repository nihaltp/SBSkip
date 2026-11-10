package com.nihaltp.sbskip.util.ytdlp

import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.model.MediaType
import java.io.File

/** Thrown when a yt-dlp command cannot be assembled from the request. */
class YtDlpCommandException(message: String) : Exception(message)

/**
 * Pure, side-effect-free assembly of the yt-dlp argument list (plan §5).
 *
 * Contains no Android imports or process handling so it is unit-testable without a device.
 * yt-dlp handles SponsorBlock removal and metadata embedding natively for YouTube downloads.
 */
object YtDlpCommandBuilder {
    /**
     * URL query markers the app uses internally (`bypassDurationCheck`, `overwrite`, `noSuffix`,
     * `categories`). They are internal markers and must never leak into a yt-dlp invocation.
     */
    internal val internalMarkers = listOf("bypassDurationCheck", "overwrite", "noSuffix", "categories")

    private val internalMarkerKeys = internalMarkers.map { it.lowercase() }

    private const val PROGRESS_TEMPLATE =
        "download:PROGRESS|%(progress._percent_str)s|%(progress._downloaded_bytes)s|" +
            "%(progress._total_bytes_estimate)s|%(progress._speed)s|%(progress._eta)s"

    internal const val OUTPUT_TEMPLATE = "out.%(ext)s"

    /** v1 quality presets (plan §5.1); new Video selection with a 1080p ceiling + mp4 bias. */
    internal const val BEST_VIDEO_FORMAT = "bv*+ba/b"
    internal const val AUDIO_FORMAT_SPEC = "ba/b"

    data class Request(
        val mediaType: MediaType,
        /** Raw queue URL, possibly with internal `?marker=value` markers appended. */
        val url: String,
        /** Per-download work dir; yt-dlp writes its output inside it. */
        val workDir: File,
        /** Maximum video height: "720", "1080", or "BEST" (best available). */
        val maxQuality: String = "BEST",
        /** Absolute path of the ffmpeg CLI launcher that yt-dlp execs for merging. */
        val ffmpegLocation: File? = null,
        /** SponsorBlock categories to remove via yt-dlp native `--sponsorblock-remove`. */
        val sponsorBlockCategories: Set<String>? = null,
        /** Extract audio instead of video (--extract-audio). */
        val convertToAudio: Boolean = false,
        /** YouTube video ID for SBSKIP_YOUTUBE_VIDEO_ID metadata tag. */
        val youtubeVideoId: String? = null,
        /** YouTube watch URL for SBSKIP_YOUTUBE_VIDEO_URL metadata tag. */
        val youtubeVideoUrl: String? = null,
        /** YouTube title for SBSKIP_YOUTUBE_TITLE metadata tag. */
        val youtubeTitle: String? = null,
        /** Author name for SBSKIP_AUTHOR_NAME metadata tag. */
        val authorName: String? = null,
        /** Author URL for SBSKIP_AUTHOR_URL metadata tag. */
        val authorUrl: String? = null,
        /** Thumbnail URL for SBSKIP_THUMBNAIL_URL metadata tag and `--embed-thumbnail`. */
        val thumbnailUrl: String? = null,
        /** SponsorBlock segments string for SBSKIP_SEGMENTS metadata tag. */
        val sbskipSegments: String? = null,
        /** Removed category names for SBSKIP_REMOVED_CATEGORIES metadata tag. */
        val removedCategories: Set<String>? = null,
    )

    fun build(request: Request): List<String> {
        val url = cleanUrl(request.url)
        if (url.isBlank()) {
            throw YtDlpCommandException(
                "No downloadable source URL (local file imports cannot use yt-dlp)",
            )
        }
        val args =
            mutableListOf(
                "--newline",
                "--progress-template",
                PROGRESS_TEMPLATE,
                "--no-playlist",
                "--no-cache-dir",
                "--no-warnings",
                "--retries",
                "5",
                "--fragment-retries",
                "10",
                "--extractor-retries",
                "2",
                "--file-access-retries",
                "5",
                "--socket-timeout",
                "15",
                "--no-mtime",
                "-o",
                File(request.workDir, OUTPUT_TEMPLATE).absolutePath,
            )
        request.ffmpegLocation?.let {
            args.add("--ffmpeg-location")
            args.add(it.absolutePath)
        }
        // Use the Android player client to avoid YouTube 403s on video data for the
        // default web client (which increasingly requires proof-of-origin tokens).
        args.add("--extractor-args")
        args.add("youtube:player_client=android")

        // SponsorBlock removal via yt-dlp native support.
        request.sponsorBlockCategories?.takeIf { it.isNotEmpty() }?.let { categories ->
            val ytDlpCategories =
                categories.mapNotNull { toYtDlpSponsorBlockCategory(it) }
            if (ytDlpCategories.isNotEmpty()) {
                // If all 9 yt-dlp-supported categories are present, use the shorthand "all".
                val allCategories =
                    setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "hook", "filler", "music_offtopic")
                if (ytDlpCategories.toSet() == allCategories) {
                    args.add("--sponsorblock-remove")
                    args.add("all")
                } else {
                    args.add("--sponsorblock-remove")
                    args.add(ytDlpCategories.joinToString(","))
                }
            }
        }

        // Metadata embedding via yt-dlp native support.
        args.add("--add-metadata")
        request.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let {
            args.add("--embed-thumbnail")
        }

        // Custom SBSKIP_* metadata tags.
        val metadataTags = mutableListOf<Pair<String, String>>()
        request.youtubeVideoId?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_YOUTUBE_VIDEO_ID" to it)
        }
        request.youtubeVideoUrl?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_YOUTUBE_VIDEO_URL" to it)
        }
        request.youtubeTitle?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_YOUTUBE_TITLE" to it)
        }
        request.authorName?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_AUTHOR_NAME" to it)
        }
        request.authorUrl?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_AUTHOR_URL" to it)
        }
        request.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_THUMBNAIL_URL" to it)
        }
        request.sbskipSegments?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_SEGMENTS" to it)
        }
        metadataTags.add("SBSKIP_VERSION" to BuildConfig.VERSION_NAME)
        metadataTags.add("SBSKIP_PROCESSED" to "true")
        metadataTags.add("SBSKIP_PROCESSED_AT" to System.currentTimeMillis().toString())
        request.removedCategories?.takeIf { it.isNotEmpty() }?.let {
            metadataTags.add("SBSKIP_REMOVED_CATEGORIES" to it.joinToString(","))
        }
        metadataTags.forEach { (key, value) ->
            args.add("--metadata")
            args.add("$key=$value")
        }

        when (request.mediaType) {
            MediaType.VIDEO -> {
                args.add("-f")
                args.add(videoFormatSpec(request.maxQuality))
                args.add("--merge-output-format")
                args.add("mp4")
                args.add("--no-video-multistreams")
            }
            MediaType.AUDIO -> {
                args.add("-f")
                args.add(AUDIO_FORMAT_SPEC)
                if (request.convertToAudio) {
                    args.add("--extract-audio")
                    args.add("--audio-format")
                    args.add("m4a")
                }
            }
        }
        args.add(url)
        return args
    }

    internal fun videoFormatSpec(maxQuality: String): String =
        when (maxQuality.lowercase()) {
            "best" -> BEST_VIDEO_FORMAT
            "720" -> "bv*[height<=720]+ba/b"
            "1080" -> "bv*[height<=1080]+ba/b"
            else ->
                throw YtDlpCommandException(
                    "Unsupported native download quality: $maxQuality",
                )
        }

    /**
     * Strips internal markers from a queue URL (keys may be `?`- or `&`-separated). URLs with the
     * `sbskip://` prefix are local file imports — no source URL exists — and reduce to empty.
     */
    internal fun cleanUrl(rawUrl: String): String {
        if (rawUrl.startsWith("sbskip://")) return ""
        val queryStart = rawUrl.indexOf('?').let { if (it >= 0) it else rawUrl.indexOf('&') }
        if (queryStart < 0) return rawUrl
        val base = rawUrl.substring(0, queryStart)
        val keptParts =
            rawUrl
                .substring(queryStart + 1)
                .split('?', '&')
                .filter { part ->
                    part.isNotBlank() &&
                        part.substringBefore('=').lowercase() !in internalMarkerKeys
                }
        return buildString {
            append(base)
            keptParts.forEachIndexed { index, part ->
                append(if (index == 0) '?' else '&')
                append(part)
            }
        }
    }

    /**
     * Maps SB Skip [SponsorBlockCategory] enum names to yt-dlp `--sponsorblock-remove` category names.
     *
     * SB Skip uses enum names like `SELF_PROMOTION`, `INTERACTION_REMINDER`, etc. yt-dlp uses different
     * names (e.g. `selfpromo`, `interaction`). Unsupported categories (e.g. `hook`) return null.
     */
    internal fun toYtDlpSponsorBlockCategory(category: String): String? {
        return when (category.lowercase()) {
            "sponsor" -> "sponsor"
            "self_promotion" -> "selfpromo"
            "interaction_reminder" -> "interaction"
            "intro" -> "intro"
            "outro" -> "outro"
            "preview_recap" -> "preview"
            "hook" -> "hook"
            "filler_tangent" -> "filler"
            "music_offtopic" -> "music_offtopic"
            else -> null
        }
    }
}
