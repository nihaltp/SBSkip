package com.nihaltp.sbskip.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {
    @Test
    fun `accepts and normalizes a bare standard video id`() {
        val videoId = "dQw4w9WgXcQ"

        assertEquals("https://www.youtube.com/watch?v=$videoId", YouTubeUrlParser.normalize(videoId))
        assertEquals(videoId, YouTubeUrlParser.extractVideoId(videoId))
    }

    @Test
    fun `extracts video ids from common youtube url forms`() {
        val videoId = "dQw4w9WgXcQ"
        val urls =
            listOf(
                "https://www.youtube.com/watch?v=$videoId&si=tracking",
                "https://youtu.be/$videoId?t=30",
                "https://www.youtube.com/shorts/$videoId",
                "https://www.youtube.com/embed/$videoId",
                "https://www.youtube.com/v/$videoId",
                "https://music.youtube.com/watch?v=$videoId",
                "https://youtube-nocookie.com/embed/$videoId",
                "music.youtube.com/watch?v=$videoId",
            )

        urls.forEach { url ->
            assertEquals("$url should contain the expected video ID", videoId, YouTubeUrlParser.extractVideoId(url))
            assertEquals("https://www.youtube.com/watch?v=$videoId", YouTubeUrlParser.normalize(url))
        }
    }

    @Test
    fun `extracts playlist ids without requiring a video id`() {
        val playlistUrl = "https://www.youtube.com/playlist?list=PL1234567890"

        assertEquals("PL1234567890", YouTubeUrlParser.extractPlaylistId(playlistUrl))
        assertNull(YouTubeUrlParser.extractVideoId(playlistUrl))
        assertTrue(YouTubeUrlParser.isYouTubeUrl(playlistUrl))
    }

    @Test
    fun `rejects non-standard bare ids and invalid urls`() {
        val invalidInputs =
            listOf(
                "eiGdshas",
                "dQw4w9WgXcQ1",
                "not a video id",
                "https://example.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=tooShort",
            )

        invalidInputs.forEach { input ->
            assertNull("$input should not produce a video ID", YouTubeUrlParser.extractVideoId(input))
            assertFalse("$input should not be recognized as YouTube", YouTubeUrlParser.isYouTubeUrl(input))
        }
    }

    // ── cleanVideoId: the video id that is stored in the file metadata ─────────

    @Test
    fun `cleans a video id that carries an overwrite parameter`() {
        assertEquals("2kjolTLZ_Mg", YouTubeUrlParser.cleanVideoId("2kjolTLZ_Mg?overwrite=true"))
    }

    @Test
    fun `keeps a clean video id unchanged`() {
        assertEquals("2kjolTLZ_Mg", YouTubeUrlParser.cleanVideoId("2kjolTLZ_Mg"))
    }

    @Test
    fun `cleans a video id that carries a fragment`() {
        assertEquals("2kjolTLZ_Mg", YouTubeUrlParser.cleanVideoId("2kjolTLZ_Mg#t=30"))
    }

    @Test
    fun `cleans a video id out of a url`() {
        assertEquals(
            "2kjolTLZ_Mg",
            YouTubeUrlParser.cleanVideoId("https://www.youtube.com/watch?v=2kjolTLZ_Mg&si=tracking"),
        )
    }

    @Test
    fun `returns null for an empty video id`() {
        assertNull(YouTubeUrlParser.cleanVideoId(null))
        assertNull(YouTubeUrlParser.cleanVideoId(""))
        assertNull(YouTubeUrlParser.cleanVideoId("   "))
    }

    @Test
    fun `returns null for a video id that cannot be cleaned`() {
        assertNull(YouTubeUrlParser.cleanVideoId("not a video id"))
    }
}
