package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the urls that are built from a video id.
 */
class ConstantsTest {
    @Test
    fun `builds the watch url of a video`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            Constants.buildYouTubeWatchUrl("dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `builds the thumbnail url of a video`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            Constants.buildYouTubeThumbnailUrl("dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `a sanitized video id produces a valid thumbnail url`() {
        val cleanVideoId = YouTubeUrlParser.cleanVideoId("2kjolTLZ_Mg?overwrite=true")

        assertEquals("2kjolTLZ_Mg", cleanVideoId)
        assertEquals(
            "https://img.youtube.com/vi/2kjolTLZ_Mg/mqdefault.jpg",
            Constants.buildYouTubeThumbnailUrl(checkNotNull(cleanVideoId)),
        )
    }

    @Test
    fun `a watch url of a sanitized video id is valid`() {
        val cleanVideoId = YouTubeUrlParser.cleanVideoId("2kjolTLZ_Mg?overwrite=true")

        assertEquals(
            "https://www.youtube.com/watch?v=2kjolTLZ_Mg",
            Constants.buildYouTubeWatchUrl(checkNotNull(cleanVideoId)),
        )
    }
}
