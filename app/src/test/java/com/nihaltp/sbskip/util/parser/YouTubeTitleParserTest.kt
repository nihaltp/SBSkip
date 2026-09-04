package com.nihaltp.sbskip.util.parser

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

    @Test
    fun `removes youtube fluff from titles`() {
        assertEquals("BLACKPINK - 'How You Like That'", YouTubeTitleParser.cleanTitle("BLACKPINK - 'How You Like That' M/V"))
        assertEquals("The Weeknd - Blinding Lights", YouTubeTitleParser.cleanTitle("The Weeknd - Blinding Lights (Official Video)"))
        assertEquals("Eminem - Without Me", YouTubeTitleParser.cleanTitle("Eminem - Without Me (Official Music Video)"))
        assertEquals("BTS (방탄소년단) 'Dynamite'", YouTubeTitleParser.cleanTitle("BTS (방탄소년단) 'Dynamite' Official MV"))
        assertEquals("Taylor Swift - Blank Space", YouTubeTitleParser.cleanTitle("Taylor Swift - Blank Space (Official)"))
        assertEquals("Ed Sheeran - Shape of You", YouTubeTitleParser.cleanTitle("Ed Sheeran - Shape of You [Official Video]"))
        assertEquals("Post Malone - Circles", YouTubeTitleParser.cleanTitle("Post Malone - Circles (Lyrics)"))
        assertEquals("Adele - Hello", YouTubeTitleParser.cleanTitle("Adele - Hello (Official Audio)"))
        assertEquals(
            "Arctic Monkeys - Do I Wanna Know?",
            YouTubeTitleParser.cleanTitle("Arctic Monkeys - Do I Wanna Know? (Official Video) (HD)"),
        )
        assertEquals("Daft Punk - Get Lucky", YouTubeTitleParser.cleanTitle("Daft Punk - Get Lucky (Full Album)"))
        assertEquals(
            "Oasis - Wonderwall",
            YouTubeTitleParser.cleanTitle("Oasis - Wonderwall (Remastered) (1080p)"),
        )
        assertEquals("Kendrick Lamar - HUMBLE.", YouTubeTitleParser.cleanTitle("Kendrick Lamar - HUMBLE. [4K]"))
        assertEquals("Gorillaz - Feel Good Inc.", YouTubeTitleParser.cleanTitle("Gorillaz - Feel Good Inc. Official Video"))
        assertEquals("Linkin Park - Numb", YouTubeTitleParser.cleanTitle("Linkin Park - Numb [Official Music Video]"))
        assertEquals("Coldplay - Yellow", YouTubeTitleParser.cleanTitle("Coldplay - Yellow (Official Video) [4K]"))
        assertEquals("Aha - Take On Me", YouTubeTitleParser.cleanTitle("Aha - Take On Me (Official Music Video)"))
        assertEquals(
            "Red Hot Chili Peppers - Californication",
            YouTubeTitleParser.cleanTitle("Red Hot Chili Peppers - Californication [Official Music Video]"),
        )
        // Live tests
        assertEquals("Nirvana - The Man Who Sold The World", YouTubeTitleParser.cleanTitle("Nirvana - The Man Who Sold The World (Live)"))
        assertEquals("Queen - Radio Ga Ga", YouTubeTitleParser.cleanTitle("Queen - Radio Ga Ga [Live Aid 1985]"))
        assertEquals("Dua Lipa - Don't Start Now", YouTubeTitleParser.cleanTitle("Dua Lipa - Don't Start Now (Live in LA)"))
        assertEquals("Adele - Someone Like You", YouTubeTitleParser.cleanTitle("Adele - Someone Like You Live Acoustic"))
        assertEquals("Ed Sheeran - Perfect", YouTubeTitleParser.cleanTitle("Ed Sheeran - Perfect Official Live Video"))

        // Trailing separator test
        assertEquals("Artist - Song", YouTubeTitleParser.cleanTitle("Artist - Song - (Official Video)"))
    }
}
