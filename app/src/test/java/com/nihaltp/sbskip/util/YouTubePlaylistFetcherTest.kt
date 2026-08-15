package com.nihaltp.sbskip.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistFetcherTest {
    @Test
    fun parsePlaylistHtml_extractsVideosCorrectly() {
        // Simple mock of ytInitialData containing playlistVideoRenderer
        val mockHtml =
            """
            <script>var ytInitialData = {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"playlistVideoListRenderer":{"contents":[
                {"playlistVideoRenderer":{"videoId":"video123","title":{"runs":[{"text":"First Video Title"}]},"isPlayable":true}},
                {"playlistVideoRenderer":{"videoId":"video456","title":{"runs":[{"text":"Second Video &amp; Title"}]},"isPlayable":true}}
            ]}}]}}]}}}}]}}};</script>
            """.trimIndent()

        val videos = YouTubePlaylistFetcher.parsePlaylistHtml(mockHtml)

        assertEquals(2, videos.size)
        assertEquals("video123", videos[0].videoId)
        assertEquals("First Video Title", videos[0].title)

        assertEquals("video456", videos[1].videoId)
        // Check if the unicode/html escaped chars are at least parsed (ampersands, etc, but our regex just replaces \u0026, though we might need html decode in the future).
    }

    @Test
    fun parsePlaylistHtml_handlesEmptyData() {
        val mockHtml = """<html><body>No scripts here</body></html>"""
        val videos = YouTubePlaylistFetcher.parsePlaylistHtml(mockHtml)
        assertTrue(videos.isEmpty())
    }
}
