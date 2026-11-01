package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SbskipMetadataReaderTest {
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var reader: SbskipMetadataReader

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        reader = SbskipMetadataReader()
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.v(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(FFprobeKit::class)
        unmockkStatic(Log::class)
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

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, "fd://mock")

        // Assert
        assertNotNull(result)
        assertEquals("Test Video", result!!.title)
        assertEquals("mp4", result!!.extension)
        assertEquals(120L, result!!.durationSeconds)
        assertEquals("dQw4w9WgXcQ", result!!.youtubeId)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", result!!.youtubeUrl)
        assertEquals("Test Video", result!!.youtubeTitle)
        assertEquals("Test Author", result!!.authorName)
        assertEquals("https://youtube.com/@testauthor", result!!.authorUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", result!!.thumbnailUrl)
        assertEquals("0-10;20-30", result!!.sbskipSegments)
        assertEquals("1.9.4", result!!.sbskipVersion)
        assertEquals(true, result!!.sbskipProcessed)
        assertEquals(1234567890L, result!!.sbskipProcessedAt)
    }

    @Test
    fun `readSbskipMetadata should return null when ffprobe returns empty output`() {
        // Arrange
        mockkStatic(FFprobeKit::class)
        every { FFprobeKit.execute(any()) } answers {
            val session = mockk<FFprobeSession>()
            every { session.output } returns ""
            session
        }

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, "fd://mock")

        // Assert
        assertNull(result)
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

        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.lastPathSegment } returns "test.mp4"

        // Act
        val result = reader.readSbskipMetadata(contentResolver, fileUri, "fd://mock")

        // Assert
        assertNotNull(result)
        assertEquals("Partial Video", result!!.title)
        assertNull(result!!.youtubeId)
        assertNull(result!!.authorName)
        assertNull(result!!.sbskipSegments)
        assertNull(result!!.sbskipProcessed)
    }
}
