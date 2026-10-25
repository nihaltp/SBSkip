package com.nihaltp.sbskip.util.ytdlp

import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    ): YtDlpCommandBuilder.Request =
        YtDlpCommandBuilder.Request(
            mediaType = mediaType,
            url = url,
            workDir = workDir,
            maxQuality = maxQuality,
            ffmpegLocation = ffmpegLocation,
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
        assertEquals("ba[ext=m4a]/ba/ba", args[args.indexOf("-f") + 1])
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
            "download:PROGRESS|%(progress._percent_str)|%(progress._downloaded_bytes)|" +
                "%(progress._total_bytes_estimate)|%(progress._speed)|%(progress._eta)",
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
}
