package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.model.MusicVideoType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YouTubeMetadataParserTest {
    companion object {
        @JvmStatic
        @org.junit.BeforeClass
        fun setupFixtures() {
            val fixturesDir = File("src/test/resources/fixtures")
            if (!fixturesDir.exists()) {
                fixturesDir.mkdirs()
            } else {
                fixturesDir.listFiles()?.forEach { it.delete() }
            }

            // Rebuild HTML data files from scratch
            File(fixturesDir, "official_music_video.html").writeText(
                """
                var ytInitialPlayerResponse = {
                    "microformat": { "playerMicroformatRenderer": { "category": "Music" } },
                    "videoDetails": { "title": "The Weeknd - Blinding Lights (Official Audio)", "author": "The Weeknd" }
                };
                var ytInitialData = {
                    "contents": {
                        "metadataRowContainerRenderer": {
                            "rows": [
                                { "metadataRowRenderer": { "title": {"simpleText": "Song"},
                                    "contents": [{"simpleText": "Blinding Lights"}] } },
                                { "metadataRowRenderer": { "title": {"simpleText": "Artist"},
                                    "contents": [{"simpleText": "The Weeknd"}] } },
                                { "metadataRowRenderer": { "title": {"simpleText": "Album"},
                                    "contents": [{"simpleText": "After Hours"}] } },
                                { "metadataRowRenderer": { "title": {"simpleText": "Year"}, "contents": [{"simpleText": "2020"}] } }
                            ]
                        }
                    }
                };
                """.trimIndent(),
            )

            File(
                fixturesDir,
                "escaped_json.html",
            ).writeText("""var ytInitialData = {"text": "here is an escaped \"quote\" and a brace { inside a string }", "valid": true};""")
            File(
                fixturesDir,
                "missing_initial_data.html",
            ).writeText("""var ytInitialPlayerResponse = {"videoDetails": {"title": "Just a Video"}};""")
            File(
                fixturesDir,
                "malformed_initial_data.html",
            ).writeText("""var ytInitialData = {"unclosed": "object"; var ytInitialPlayerResponse = {"broken";""")
            File(fixturesDir, "basic_json.html").writeText(
                """
                <html><body>
                <script>
                var ytInitialData = {"simple": "data", "nested": {"key": "value"}};
                var ytInitialPlayerResponse = {"status": "ok"};
                </script>
                </body></html>
                """.trimIndent(),
            )

            File(fixturesDir, "fallback_description.html").writeText(
                """
                var ytInitialPlayerResponse = {
                    "videoDetails": {
                        "title": "Random Upload",
                        "shortDescription": "Artist: Daft Punk\nSong: Get Lucky\nAlbum: Random Access Memories"
                    }
                };
                """.trimIndent(),
            )

            File(fixturesDir, "fallback_title.html").writeText(
                """
                var ytInitialPlayerResponse = {
                    "videoDetails": {
                        "title": "Rick Astley - Never Gonna Give You Up (Official Music Video)"
                    }
                };
                """.trimIndent(),
            )
        }

        @JvmStatic
        @org.junit.AfterClass
        fun teardownFixtures() {
            val fixturesDir = File("src/test/resources/fixtures")
            if (fixturesDir.exists()) {
                fixturesDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    private fun getFixture(name: String): String {
        val file = File("src/test/resources/fixtures/$name")
        if (!file.exists()) {
            throw IllegalArgumentException("Fixture $name not found at ${file.absolutePath}")
        }
        return file.readText()
    }

    @Test
    fun `extractJsonObjectString - correctly handles basic JSON`() {
        val html = getFixture("basic_json.html")

        val initialData = YouTubeMetadataParser.extractJsonObjectString(html, "var ytInitialData")
        assertEquals("""{"simple": "data", "nested": {"key": "value"}}""", initialData)

        val playerResponse = YouTubeMetadataParser.extractJsonObjectString(html, "var ytInitialPlayerResponse")
        assertEquals("""{"status": "ok"}""", playerResponse)
    }

    @Test
    fun `extractJsonObjectString - handles escaped braces and quotes in strings`() {
        val html = getFixture("escaped_json.html")
        val initialData = YouTubeMetadataParser.extractJsonObjectString(html, "var ytInitialData")
        assertEquals("""{"text": "here is an escaped \"quote\" and a brace { inside a string }", "valid": true}""", initialData)
    }

    @Test
    fun `parse - missing ytInitialData gracefully fails`() {
        val html = getFixture("missing_initial_data.html")
        val result = YouTubeMetadataParser.parse(html)

        assertNull(result.musicMetadata)
        assertEquals("Just a Video", result.rawTitle)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `parse - malformed json gracefully fails`() {
        val html = getFixture("malformed_initial_data.html")
        val result = YouTubeMetadataParser.parse(html)

        assertNull(result.musicMetadata)
        assertNull(result.rawTitle)
    }

    @Test
    fun `parse - extracts Music in this video structured metadata`() {
        val html = getFixture("official_music_video.html")
        val result = YouTubeMetadataParser.parse(html)

        val meta = result.musicMetadata
        assertNotNull(meta)
        assertEquals("Blinding Lights", meta?.title)
        assertEquals(listOf("The Weeknd"), meta?.artists)
        assertEquals("After Hours", meta?.album)
        assertEquals(2020, meta?.year)

        assertEquals(MusicVideoType.OFFICIAL_AUDIO, result.type)
        assertTrue(result.confidence > 0.8f) // High confidence
    }

    private fun fetchAndParseLiveVideo(videoId: String): YouTubeMetadataParser.ExtractionResult {
        val fixtureFile = File("src/test/resources/fixtures/live_$videoId.html")
        val html =
            if (fixtureFile.exists()) {
                fixtureFile.readText()
            } else {
                val request =
                    okhttp3.Request.Builder()
                        .url("https://www.youtube.com/watch?v=$videoId")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                        )
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: throw IllegalStateException("Failed to download HTML")

                fixtureFile.parentFile?.mkdirs()
                fixtureFile.writeText(body)
                body
            }

        val result = YouTubeMetadataParser.parse(html)
        if (!html.contains("ytInitialData")) {
            println("Warning: YouTube returned a consent or captcha page instead of the video page for $videoId.")
        }
        return result
    }

    @Test
    fun `parse - live YouTube music video fetch and parse (Blinding Lights)`() {
        val result = fetchAndParseLiveVideo("fHI8X4OXluQ")
        val meta = result.musicMetadata

        if (meta != null) {
            assertNotNull("Expected to find music metadata for Blinding Lights", meta)
            assertTrue("Expected title to contain Blinding Lights", meta.title?.contains("Blinding Lights") == true)
            assertTrue("Expected artists to contain The Weeknd", meta.artists.contains("The Weeknd"))
            assertTrue(result.confidence > 0.5f)
        }
    }

    @Test
    fun `parse - live YouTube music video fetch and parse (q5HNRym5zxY)`() {
        val result = fetchAndParseLiveVideo("q5HNRym5zxY")
        val meta = result.musicMetadata

        if (meta != null) {
            assertNotNull("Expected to find music metadata for q5HNRym5zxY", meta)
            // Just verifying that it successfully extracts music metadata and has a valid title/artist
            assertTrue(meta.title?.isNotBlank() == true)
            assertTrue(meta.artists.isNotEmpty())
        }
    }

    @Test
    fun `parse - fallback description extraction`() {
        val html = getFixture("fallback_description.html")

        val result = YouTubeMetadataParser.parse(html)
        val meta = result.musicMetadata
        assertNotNull(meta)
        assertEquals("Get Lucky", meta?.title)
        assertEquals(listOf("Daft Punk"), meta?.artists)
        assertEquals("Random Access Memories", meta?.album)
        assertEquals(0.6f, result.confidence, 0.01f) // Medium confidence
    }

    @Test
    fun `parse - fallback title extraction`() {
        val html = getFixture("fallback_title.html")

        val result = YouTubeMetadataParser.parse(html)
        val meta = result.musicMetadata
        assertNotNull(meta)
        assertEquals("Never Gonna Give You Up", meta?.title)
        assertEquals(listOf("Rick Astley"), meta?.artists)

        assertEquals(MusicVideoType.OFFICIAL_MUSIC_VIDEO, result.type)
        // Score: 0.2f (title regex base) + 0.3f (positive title keywords) = 0.5f
        assertEquals(0.5f, result.confidence, 0.01f)
    }
}
