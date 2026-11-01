package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SbskipMetadataReaderTest {
    private lateinit var contentResolver: ContentResolver
    private lateinit var cacheDir: File
    private lateinit var reader: SbskipMetadataReader

    @Before
    fun setup() {
        contentResolver = mockk(relaxed = true)
        cacheDir = File(System.getProperty("java.io.tmpdir"))
        reader = SbskipMetadataReader()
    }

    @After
    fun teardown() {
        unmockkStatic(FFprobeKit::class)
    }

    @Test
    fun `readSbskipMetadata should parse SBSKIP_* tags from ffprobe output`() {
        // Arrange
        val ffprobeOutput =
            """
            {
                "format": {
                    "tags": {
                        "title": "Test Video",
                        "SBSKIP_YOUTUBE_VIDEO_ID": "dQw4w9WgXcQ",
                        "SBSKIP_YOUTUBE_VIDEO_URL": "https://youtube.com/watch?v=dQw4w9WgXcQ",
                        "SBSKIP_YOUTUBE_TITLE": "Test Video",
                        "SBSKIP_AUTHOR_NAME": "Test Author",
                        "SBSKIP_AUTHOR_URL": "https://youtube.com/@testauthor",
                        "SBSKIP_THUMBNAIL_URL": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                        "SBSKIP_SEGMENTS": "0-10;20-30",
                        "SBSKIP_VERSION": "1.9.4",
                        "SBSKIP_PROCESSED": "true",
                        "SBSKIP_PROCESSED_AT": "1234567890"
                    },
                    "duration": "120.9"
                }
            }
            """.trimIndent()

        mockkStatic(FFprobeKit::class)
        every { FFprobeKit.execute(any()) } answers {
            val session = mockk<FFprobeSession>()
            every { session.output } returns ffprobeOutput
            session
        }
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream("dummy".toByteArray())

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, cacheDir)

        // Assert
        assertEquals("Test Video", result.title)
        assertEquals("mp4", result.extension)
        assertEquals(120L, result.durationSeconds)
        assertEquals("dQw4w9WgXcQ", result.youtubeId)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", result.youtubeUrl)
        assertEquals("Test Video", result.youtubeTitle)
        assertEquals("Test Author", result.authorName)
        assertEquals("https://youtube.com/@testauthor", result.authorUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", result.thumbnailUrl)
        assertEquals("0-10;20-30", result.sbskipSegments)
        assertEquals("1.9.4", result.sbskipVersion)
        assertEquals(true, result.sbskipProcessed)
        assertEquals(1234567890L, result.sbskipProcessedAt)
    }

    @Test
    fun `readSbskipMetadata should return default values when ffprobe returns empty output`() {
        // Arrange
        mockkStatic(FFprobeKit::class)
        every { FFprobeKit.execute(any()) } answers {
            val session = mockk<FFprobeSession>()
            every { session.output } returns ""
            session
        }
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream("dummy".toByteArray())

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, cacheDir)

        // Assert
        assertEquals("test.mp4", result.title)
        assertEquals("mp4", result.extension)
        assertNull(result.youtubeId)
        assertNull(result.youtubeUrl)
        assertNull(result.sbskipSegments)
        assertNull(result.sbskipVersion)
    }

    @Test
    fun `readSbskipMetadata should handle missing optional tags gracefully`() {
        // Arrange
        val ffprobeOutput =
            """
            {
                "format": {
                    "tags": {
                        "title": "Partial Video"
                    }
                }
            }
            """.trimIndent()

        mockkStatic(FFprobeKit::class)
        every { FFprobeKit.execute(any()) } answers {
            val session = mockk<FFprobeSession>()
            every { session.output } returns ffprobeOutput
            session
        }
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream("dummy".toByteArray())

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, cacheDir)

        // Assert
        assertEquals("Partial Video", result.title)
        assertNull(result.youtubeId)
        assertNull(result.authorName)
        assertNull(result.sbskipSegments)
        assertNull(result.sbskipProcessed)
    }
}
