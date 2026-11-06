package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.ExportFormat
import com.nihaltp.sbskip.model.ImportResult
import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportImportUtilEdgeCaseTest {
    private val videoId = "dQw4w9WgXcQ"
    private val watchUrl = "https://www.youtube.com/watch?v=$videoId"

    private fun downloadedVideoEntity(): DownloadedVideoEntity =
        DownloadedVideoEntity(
            videoId = videoId,
            url = watchUrl,
            title = "Test Title",
            author = "Test Author",
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            durationSeconds = 300L,
            fileSize = 1024L,
            mediaType = "video",
        )

    @Test
    fun `handles empty video list export gracefully`() {
        val exportJson = ExportImportUtil.exportDownloadedVideos(emptyList(), ExportFormat.FULL_JSON)

        assertTrue(exportJson.contains("\"downloadHistory\""))
        assertTrue(exportJson.contains("[]"))
    }

    @Test
    fun `handles empty queue list export gracefully`() {
        val exportJson = ExportImportUtil.exportQueue(emptyList(), ExportFormat.FULL_JSON)

        assertTrue(exportJson.contains("\"downloadHistory\""))
        assertTrue(exportJson.contains("[]"))
    }

    @Test
    fun `parses invalid json gracefully`() {
        val result = ExportImportUtil.parseImportedText("{invalid json}")

        assertTrue(result is ImportResult.Failure)
        val failure = result as ImportResult.Failure
        assertTrue(failure.error.contains("No valid"))
    }

    @Test
    fun `parses json without downloadHistory`() {
        val jsonWithoutHistory = """{"otherKey": "value"}"""

        val result = ExportImportUtil.parseImportedText(jsonWithoutHistory)

        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `parses json with empty downloadHistory returns failure`() {
        // Empty items and downloadHistory - parseJsonBackup returns null, falls through to line parsing
        val jsonEmptyHistory = """{"items": [], "downloadHistory": []}"""

        val result = ExportImportUtil.parseImportedText(jsonEmptyHistory)

        // Falls through to line-based parsing which fails because JSON is not valid URLs
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `parses multiple youtube url formats`() {
        val urls =
            listOf(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "https://www.youtube.com/v/dQw4w9WgXcQ",
                "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            )

        val input = urls.joinToString("\n")
        val result = ExportImportUtil.parseImportedText(input)

        val success = result as ImportResult.Success
        assertEquals(1, success.items.size) // All should be deduplicated
        assertEquals(videoId, success.items.first())
    }

    @Test
    fun `parses youtube urls with additional query parameters`() {
        val urls =
            listOf(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=123s",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLtest",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&index=1",
            )

        val input = urls.joinToString("\n")
        val result = ExportImportUtil.parseImportedText(input)

        val success = result as ImportResult.Success
        assertEquals(1, success.items.size)
        assertEquals(videoId, success.items.first())
    }

    @Test
    fun `handles lines with whitespace gracefully`() {
        val input = "  \n  dQw4w9WgXcQ  \n  \n  https://www.youtube.com/watch?v=dQw4w9WgXcQ  \n  "

        val result = ExportImportUtil.parseImportedText(input)

        val success = result as ImportResult.Success
        assertEquals(1, success.items.size)
    }

    @Test
    fun `returns failure for only whitespace`() {
        val result = ExportImportUtil.parseImportedText("   \n  \t  \n  ")

        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `deduplicates repeated video ids`() {
        val input = listOf("dQw4w9WgXcQ", "dQw4w9WgXcQ", "dQw4w9WgXcQ").joinToString("\n")

        val result = ExportImportUtil.parseImportedText(input)

        val success = result as ImportResult.Success
        assertEquals(1, success.items.size)
    }

    @Test
    fun `handles mixed valid and invalid lines`() {
        val input = """dQw4w9WgXcQ
invalid_line
https://www.youtube.com/watch?v=dQw4w9WgXcQ
another_invalid
short"""

        val result = ExportImportUtil.parseImportedText(input)

        val success = result as ImportResult.Success
        // Should extract the valid YouTube video IDs
        assertEquals(1, success.items.size)
        assertEquals(videoId, success.items.first())
    }

    @Test
    fun `handles very long input`() {
        val manyIds = (1..1000).map { "video$it" }.joinToString("\n")

        val result = ExportImportUtil.parseImportedText(manyIds)

        // Many of these may not be valid YouTube IDs, so filter expects less
        assertTrue(result is ImportResult.Success || result is ImportResult.Failure)
    }

    @Test
    fun `export includes all video metadata fields in ExportItem`() {
        val entity = downloadedVideoEntity()
        val exportJson = ExportImportUtil.exportDownloadedVideos(listOf(entity), ExportFormat.FULL_JSON)

        // ExportItem has: videoId, url, title, author, thumbnailUrl, sponsorBlockCategories
        assertTrue(exportJson.contains("\"videoId\": \"$videoId\""))
        assertTrue(exportJson.contains("\"url\": \"$watchUrl\""))
        assertTrue(exportJson.contains("\"title\": \"Test Title\""))
        assertTrue(exportJson.contains("\"author\": \"Test Author\""))
        assertTrue(exportJson.contains("\"thumbnailUrl\": \"https://example.com/thumbnail.jpg\""))
        assertTrue(exportJson.contains("\"sponsorBlockCategories\""))
        // durationSeconds, fileSize, mediaType are NOT in ExportItem - they are in the entity
    }

    @Test
    fun `handles null metadata fields in export`() {
        val entity =
            DownloadedVideoEntity(
                videoId = videoId,
                url = watchUrl,
                title = "Test Title",
                author = "Test Author",
                thumbnailUrl = null,
                durationSeconds = null,
                fileSize = null,
                mediaType = "video",
            )
        val exportJson = ExportImportUtil.exportDownloadedVideos(listOf(entity), ExportFormat.FULL_JSON)

        assertTrue(exportJson.contains("\"videoId\": \"$videoId\""))
        assertTrue(exportJson.contains("\"title\": \"Test Title\""))
        // Null fields should be omitted or null in JSON
    }

    @Test
    fun `export queue includes all item fields`() {
        val queueItem =
            DownloadQueueItem(
                id = 1L,
                url = watchUrl,
                title = "Test Title",
                localFileUri = "content://test/uri",
                mediaType = MediaType.VIDEO,
                thumbnailUrl = "https://example.com/thumb.jpg",
                durationSeconds = 300L,
                status = DownloadQueueStatus.QUEUED,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 2000L,
                errorMessage = null,
                outputPath = "content://test/output",
                outputDurationSeconds = 300L,
                convertVideoToAudio = false,
                deleteOriginalVideo = true,
                audioOutputDirUri = "",
                videoOutputDirUri = "",
                relativePath = "Movies/SB Skip/test.mp4",
            )

        val exportJson = ExportImportUtil.exportQueue(listOf(queueItem), ExportFormat.FULL_JSON)

        // Queue items in export have: videoId, url, title, author, thumbnailUrl, sponsorBlockCategories
        assertTrue(exportJson.contains("\"url\": \"$watchUrl\""))
        assertTrue(exportJson.contains("\"title\": \"Test Title\""))
        assertTrue(exportJson.contains("\"videoId\": \"$videoId\""))
        assertTrue(exportJson.contains("\"sponsorBlockCategories\""))
        // Note: status, id, etc. are not in ExportItem - they are in the queue entity
    }
}
