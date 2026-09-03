package com.nihaltp.sbskip.util

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeTitleParserTest {
    @Test
    fun `extracts artists after ft and feat`() {
        val title = "@SaiAbhyankkar - Pavazha Malli (Music Video) | ft.Kayadu | feat.Shruti_Haasan"
        val authorName = "Vevo"

        val result = YouTubeTitleParser.extractArtistsFromTitle(title, authorName)
        assertEquals(listOf("Vevo", "SaiAbhyankkar", "Kayadu", "Shruti_Haasan"), result)
    }

    @Test
    fun `extracts @ handles as artists`() {
        val title = "@ SaiAbhyankkar - Pavazha Malli (Music Video) | ft. Kayadu | feat. Shruti_Haasan"
        val authorName = "Think Indie"

        val result = YouTubeTitleParser.extractArtistsFromTitle(title, authorName)
        assertEquals(listOf("Think Indie", "SaiAbhyankkar", "Kayadu", "Shruti_Haasan"), result)
    }

    @Test
    fun `extracts artists after feat ignoring brackets and separators`() {
        val title = "DJ Snake - Taki Taki (Official Video) ft. Selena Gomez, Ozuna, Cardi B [4K]"
        val authorName = "DJ Snake"

        val result = YouTubeTitleParser.extractArtistsFromTitle(title, authorName)
        assertEquals(listOf("DJ Snake", "Selena Gomez", "Ozuna", "Cardi B"), result)
    }

    @Test
    fun `returns empty list when no artists found and author is null`() {
        val title = "Just a regular video without any artist info"
        val authorName = null

        val result = YouTubeTitleParser.extractArtistsFromTitle(title, authorName)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `extracts channel name as artist`() {
        val title = "Some random title"
        val authorName = "My Channel"

        val result = YouTubeTitleParser.extractArtistsFromTitle(title, authorName)
        assertEquals(listOf("My Channel"), result)
    }
}
