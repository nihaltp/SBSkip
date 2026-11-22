package com.nihaltp.sbskip.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeMetadataExtractorTest {
    @Test
    fun `extract should return SBSKIP tags when present`() {
        val tags =
            mapOf(
                "SBSKIP_YOUTUBE_VIDEO_ID" to "dQw4w9WgXcQ",
                "SBSKIP_YOUTUBE_VIDEO_URL" to "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "SBSKIP_YOUTUBE_TITLE" to "Test Video",
                "SBSKIP_AUTHOR_NAME" to "Test Author",
                "SBSKIP_AUTHOR_URL" to "https://youtube.com/@testauthor",
                "SBSKIP_THUMBNAIL_URL" to "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            )

        val result = YouTubeMetadataExtractor.extract(tags, "fallback")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", result.youtubeUrl)
        assertEquals("Test Video", result.youtubeTitle)
        assertEquals("Test Author", result.authorName)
        assertEquals("https://youtube.com/@testauthor", result.authorUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", result.thumbnailUrl)
    }

    @Test
    fun `extract should return legacy YOUTUBE_ tags when SBSKIP not present`() {
        val tags =
            mapOf(
                "YOUTUBE_VIDEO_ID" to "dQw4w9WgXcQ",
                "YOUTUBE_VIDEO_URL" to "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "title" to "Test Video",
                "author" to "Test Author",
            )

        val result = YouTubeMetadataExtractor.extract(tags, "fallback")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", result.youtubeUrl)
        assertEquals("Test Video", result.youtubeTitle)
        assertEquals("Test Author", result.authorName)
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg", result.thumbnailUrl)
        assertNull(result.authorUrl)
    }

    @Test
    fun `extract should parse comment JSON when present`() {
        val commentJson =
            """
            {
                "youtubeId": "dQw4w9WgXcQ",
                "youtubeUrl": "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "youtubeTitle": "Test Video",
                "authorName": "Test Author"
            }
            """.trimIndent()

        val tags = mapOf("comment" to commentJson)

        val result = YouTubeMetadataExtractor.extract(tags, "fallback")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("Test Video", result.youtubeTitle)
        assertEquals("Test Author", result.authorName)
    }

    @Test
    fun `extract should extract from fallback tags when no other tags present`() {
        val tags =
            mapOf(
                "comment" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "title" to "Test Song",
                "artist" to "Test Artist",
            )

        val result = YouTubeMetadataExtractor.extract(tags, "fallback title")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result.youtubeUrl)
        assertEquals("Test Song", result.youtubeTitle)
        assertEquals("Test Artist", result.authorName)
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg", result.thumbnailUrl)
    }

    @Test
    fun `extract should handle multiple fallback tags in priority order`() {
        val tags =
            mapOf(
                "comment" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "description" to "https://www.youtube.com/watch?v=oHg5SJYRHA0",
                "title" to "Test Song",
            )

        val result = YouTubeMetadataExtractor.extract(tags, "fallback")

        assertNotNull(result)
        // comment has higher priority than description
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
    }

    @Test
    fun `extract should return null when no YouTube ID found`() {
        val tags =
            mapOf(
                "title" to "Regular Song",
                "artist" to "Regular Artist",
            )

        val result = YouTubeMetadataExtractor.extract(tags, "fallback")

        assertNull(result)
    }

    @Test
    fun `extractFromTags should handle plain YouTube URL in comment`() {
        val tags = mapOf("comment" to "https://youtu.be/dQw4w9WgXcQ")

        val result = YouTubeMetadataExtractor.extractFromTags(tags, "fallback")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
    }

    @Test
    fun `extractFromTags should handle URL with query params`() {
        val tags = mapOf("comment" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLxxx&t=30s")

        val result = YouTubeMetadataExtractor.extractFromTags(tags, "fallback")

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result.youtubeUrl)
    }

    @Test
    fun `extractFromCommentJson should handle missing authorName`() {
        val commentJson =
            """
            {
                "youtubeId": "dQw4w9WgXcQ",
                "youtubeUrl": "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "youtubeTitle": "Test Video"
            }
            """.trimIndent()

        val result = YouTubeMetadataExtractor.extractFromCommentJson(commentJson)

        assertNotNull(result)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        // authorName is nullable in CommentMetadata, should be null when missing
        assertNull(result.authorName)
    }
}
