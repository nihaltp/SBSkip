package com.nihaltp.sbskip.util.ytdlp

import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YtDlpCommandBuilderTest {
    private val workDir = File("/data/user/0/com.nihaltp.sbskip.debug/cache/ytdlp-tmp/42")
    private val ffmpeg = File("/native/dir/libffmpeg.so")

    private fun request(
        mediaType: MediaType = MediaType.VIDEO,
        url: String = "https://www.youtube.com/watch?v=abcdefghijk",
        maxQuality: String = "1080",
        ffmpegLocation: File? = ffmpeg,
        sponsorBlockCategories: Set<String>? = null,
        convertToAudio: Boolean = false,
        youtubeVideoId: String? = null,
        youtubeVideoUrl: String? = null,
        youtubeTitle: String? = null,
        authorName: String? = null,
        authorUrl: String? = null,
        thumbnailUrl: String? = null,
        sbskipSegments: String? = null,
        removedCategories: Set<String>? = null,
    ): YtDlpCommandBuilder.Request =
        YtDlpCommandBuilder.Request(
            mediaType = mediaType,
            url = url,
            workDir = workDir,
            maxQuality = maxQuality,
            ffmpegLocation = ffmpegLocation,
            sponsorBlockCategories = sponsorBlockCategories,
            convertToAudio = convertToAudio,
            youtubeVideoId = youtubeVideoId,
            youtubeVideoUrl = youtubeVideoUrl,
            youtubeTitle = youtubeTitle,
            authorName = authorName,
            authorUrl = authorUrl,
            thumbnailUrl = thumbnailUrl,
            sbskipSegments = sbskipSegments,
            removedCategories = removedCategories,
        )

    @Test
    fun `video download muxes best video under 1080 with best audio into mp4`() {
        val args = YtDlpCommandBuilder.build(request())

        assertEquals("bv*[height<=1080]+ba/b", args[args.indexOf("-f") + 1])
        assertEquals("--merge-output-format", args[args.indexOf("mp4") - 1])
        assertEquals("mp4", args[args.indexOf("--merge-output-format") + 1])
        assertTrue("expected --no-video-multistreams", args.contains("--no-video-multistreams"))
    }

    @Test
    fun `best quality has no height ceiling`() {
        val args = YtDlpCommandBuilder.build(request(maxQuality = "BEST"))
        assertEquals("bv*+ba/b", args[args.indexOf("-f") + 1])
        assertFalse(args.joinToString(" ").contains("height<="))
    }

    @Test
    fun `audio-only download prefers m4a without merge`() {
        val args = YtDlpCommandBuilder.build(request(mediaType = MediaType.AUDIO))
        assertEquals("ba/b", args[args.indexOf("-f") + 1])
        assertFalse(args.contains("--merge-output-format"))
    }

    @Test(expected = YtDlpCommandException::class)
    fun `unsupported quality is rejected`() {
        YtDlpCommandBuilder.build(request(maxQuality = "480"))
    }

    @Test
    fun `fixed baseline args match the v1 contract`() {
        val args = YtDlpCommandBuilder.build(request())

        assertEquals("--newline", args.first())
        assertEquals(
            "download:PROGRESS|%(progress._percent_str)s|%(progress._downloaded_bytes)s|" +
                "%(progress._total_bytes_estimate)s|%(progress._speed)s|%(progress._eta)s",
            args[args.indexOf("--progress-template") + 1],
        )
        for (flag in listOf("--no-playlist", "--no-cache-dir", "--no-warnings", "--no-mtime")) {
            assertTrue("expected $flag", args.contains(flag))
        }
        for (pair in listOf(
            "--retries" to "5",
            "--fragment-retries" to "10",
            "--extractor-retries" to "2",
            "--file-access-retries" to "5",
            "--socket-timeout" to "15",
        )) {
            assertEquals(pair.second, args[args.indexOf(pair.first) + 1])
        }
    }

    @Test
    fun `output is a fixed temp file inside the work dir`() {
        val args = YtDlpCommandBuilder.build(request())
        assertEquals(
            "/data/user/0/com.nihaltp.sbskip.debug/cache/ytdlp-tmp/42/out.%(ext)s",
            args[args.indexOf("-o") + 1],
        )
    }

    @Test
    fun `ffmpeg location points the merge at the packaged CLI launcher`() {
        val args = YtDlpCommandBuilder.build(request())
        assertEquals(ffmpeg.absolutePath, args[args.indexOf("--ffmpeg-location") + 1])
    }

    @Test
    fun `url is passed last and untouched when clean`() {
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        val args = YtDlpCommandBuilder.build(request(url = url))
        assertEquals(url, args.last())
    }

    @Test
    fun `internal markers are stripped from the url`() {
        val args =
            YtDlpCommandBuilder.build(
                request(
                    url =
                        "https://www.youtube.com/watch?v=abcdefghijk" +
                            "&bypassDurationCheck=true&overwrite=true&noSuffix=true",
                ),
            )
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", args.last())
    }

    @Test
    fun `kept query parameters survive marker stripping`() {
        val result =
            YtDlpCommandBuilder.cleanUrl("https://www.youtube.com/watch?v=abcdefghijk&foo=bar&overwrite=true")
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&foo=bar", result)
    }

    @Test
    fun `categories marker with comma list is stripped`() {
        val result =
            YtDlpCommandBuilder.cleanUrl("https://www.youtube.com/watch?v=abcdefghijk?categories=SPONSOR,INTRO")
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", result)
    }

    @Test(expected = YtDlpCommandException::class)
    fun `sbskip local imports have no source url and are rejected`() {
        assertFalse(
            YtDlpCommandBuilder.cleanUrl("sbskip://local?overwrite=true").isNotBlank(),
        )
        YtDlpCommandBuilder.build(request(url = "sbskip://local?overwrite=true"))
    }

    @Test
    fun `never enables yt-dlp side post-processing`() {
        val args = YtDlpCommandBuilder.build(request(mediaType = MediaType.AUDIO))
        val joined = args.joinToString(" ").lowercase()
        assertFalse("sponsorblock (any case) must never reach yt-dlp", joined.contains("sponsorblock"))
        assertFalse(joined.contains("--write-metadata"))
        assertFalse(joined.contains("--write-thumbnail"))
        assertFalse(joined.contains("--embed-thumbnail"))
    }

    @Test
    fun `sponsorblock removal flag is included when categories provided`() {
        val req =
            request(
                sponsorBlockCategories = setOf("sponsor", "intro", "outro"),
            )
        val args = YtDlpCommandBuilder.build(req)
        assertTrue(args.contains("--sponsorblock-remove"))
        val idx = args.indexOf("--sponsorblock-remove")
        assertEquals("sponsor,intro,outro", args[idx + 1])
    }

    @Test
    fun `no sponsorblock flag when categories empty`() {
        val req = request(sponsorBlockCategories = emptySet())
        val args = YtDlpCommandBuilder.build(req)
        assertFalse(args.contains("--sponsorblock-remove"))
    }

    @Test
    fun `toYtDlpSponsorBlockCategory maps known categories`() {
        assertEquals("sponsor", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("sponsor"))
        assertEquals("selfpromo", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("self_promotion"))
        assertEquals("interaction", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("interaction_reminder"))
        assertEquals("intro", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("intro"))
        assertEquals("outro", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("outro"))
        assertEquals("preview", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("preview_recap"))
        assertEquals("hook", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("hook"))
        assertEquals("filler", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("filler_tangent"))
        assertEquals("music_offtopic", YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("music_offtopic"))
    }

    @Test
    fun `toYtDlpSponsorBlockCategory returns null for unknown categories`() {
        assertNull(YtDlpCommandBuilder.toYtDlpSponsorBlockCategory("unknown_category"))
        assertNull(YtDlpCommandBuilder.toYtDlpSponsorBlockCategory(""))
    }

    @Test
    fun `sponsorblock flag only includes categories yt-dlp supports`() {
        val req =
            request(
                sponsorBlockCategories = setOf("sponsor", "self_promotion", "unknown_category", "intro"),
            )
        val args = YtDlpCommandBuilder.build(req)
        val idx = args.indexOf("--sponsorblock-remove")
        assertNotEquals(-1, idx)
        assertEquals("sponsor,selfpromo,intro", args[idx + 1])
    }

    @Test
    fun `no sponsorblock flag when all categories unsupported`() {
        val req = request(sponsorBlockCategories = setOf("unknown_category"))
        val args = YtDlpCommandBuilder.build(req)
        assertFalse(args.contains("--sponsorblock-remove"))
    }

    @Test
    fun `sponsorblock flag uses "all" when every category is present`() {
        val req =
            request(
                sponsorBlockCategories =
                    setOf(
                        "sponsor",
                        "self_promotion",
                        "interaction_reminder",
                        "intro",
                        "outro",
                        "preview_recap",
                        "hook",
                        "filler_tangent",
                        "music_offtopic",
                    ),
            )
        val args = YtDlpCommandBuilder.build(req)
        val idx = args.indexOf("--sponsorblock-remove")
        assertNotEquals(-1, idx)
        assertEquals("all", args[idx + 1])
    }

    @Test
    fun `sponsorblock flag lists individual categories when not all are present`() {
        val req =
            request(
                sponsorBlockCategories = setOf("sponsor", "intro"),
            )
        val args = YtDlpCommandBuilder.build(req)
        val idx = args.indexOf("--sponsorblock-remove")
        assertNotEquals(-1, idx)
        assertEquals("sponsor,intro", args[idx + 1])
    }

    @Test
    fun `add-metadata and embed-thumbnail flags are included`() {
        val req =
            request(
                thumbnailUrl = "https://img.youtube.com/vi/abcdefghijk/mqdefault.jpg",
            )
        val args = YtDlpCommandBuilder.build(req)
        assertTrue(args.contains("--add-metadata"))
        assertTrue(args.contains("--embed-thumbnail"))
    }

    @Test
    fun `no embed-thumbnail when thumbnail url empty`() {
        val req = request(thumbnailUrl = null)
        val args = YtDlpCommandBuilder.build(req)
        assertTrue(args.contains("--add-metadata"))
        assertFalse(args.contains("--embed-thumbnail"))
    }

    @Test
    fun `sbskip metadata tags are included`() {
        val req =
            request(
                youtubeVideoId = "abcdefghijk",
                youtubeVideoUrl = "https://www.youtube.com/watch?v=abcdefghijk",
                youtubeTitle = "Test Video",
                authorName = "Test Author",
                authorUrl = "https://example.com/author",
                thumbnailUrl = "https://img.youtube.com/vi/abcdefghijk/mqdefault.jpg",
                sbskipSegments = "0-30;120-150",
                removedCategories = setOf("sponsor", "intro"),
            )
        val args = YtDlpCommandBuilder.build(req)
        val joined = args.joinToString(" ")
        assertTrue(joined.contains("SBSKIP_YOUTUBE_VIDEO_ID=abcdefghijk"))
        assertTrue(joined.contains("SBSKIP_YOUTUBE_VIDEO_URL=https://www.youtube.com/watch?v=abcdefghijk"))
        assertTrue(joined.contains("SBSKIP_YOUTUBE_TITLE=Test Video"))
        assertTrue(joined.contains("SBSKIP_AUTHOR_NAME=Test Author"))
        assertTrue(joined.contains("SBSKIP_AUTHOR_URL=https://example.com/author"))
        assertTrue(joined.contains("SBSKIP_THUMBNAIL_URL=https://img.youtube.com/vi/abcdefghijk/mqdefault.jpg"))
        assertTrue(joined.contains("SBSKIP_SEGMENTS=0-30;120-150"))
        assertTrue(joined.contains("SBSKIP_REMOVED_CATEGORIES=sponsor,intro"))
        assertTrue(joined.contains("SBSKIP_PROCESSED=true"))
    }

    @Test
    fun `extract-audio flag is included for audio conversion`() {
        val req =
            request(
                mediaType = MediaType.AUDIO,
                convertToAudio = true,
            )
        val args = YtDlpCommandBuilder.build(req)
        assertTrue(args.contains("--extract-audio"))
        assertTrue(args.contains("--audio-format"))
        val idx = args.indexOf("--audio-format")
        assertEquals("m4a", args[idx + 1])
    }

    @Test
    fun `no extract-audio flag for audio without conversion`() {
        val req =
            request(
                mediaType = MediaType.AUDIO,
                convertToAudio = false,
            )
        val args = YtDlpCommandBuilder.build(req)
        assertFalse(args.contains("--extract-audio"))
    }
}
