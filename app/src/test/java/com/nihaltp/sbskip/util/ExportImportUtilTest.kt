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

class ExportImportUtilTest {
    private val videoId = "dQw4w9WgXcQ"
    private val watchUrl = "https://www.youtube.com/watch?v=$videoId"

    private fun downloadedVideoEntity(): DownloadedVideoEntity =
        DownloadedVideoEntity(
            videoId = videoId,
            url = watchUrl,
            title = "Test Title",
            author = "Test Author",
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            durationSeconds = null,
            outputPaths = listOf("/storage/emulated/0/Download/test.mp4"),
            fileSize = null,
            mediaType = "video",
        )

    @Test
    fun `full json export of downloaded videos contains seal compatible download history`() {
        val exportJson = ExportImportUtil.exportDownloadedVideos(listOf(downloadedVideoEntity()), ExportFormat.FULL_JSON)

        assertTrue(exportJson.contains("\"downloadHistory\""))
        assertTrue(exportJson.contains(watchUrl))
        assertTrue(exportJson.contains("\"videoTitle\": \"Test Title\""))
        assertTrue(exportJson.contains("\"videoPath\": \"/storage/emulated/0/Download/test.mp4\""))
    }

    @Test
    fun `full json export round trips through our own import`() {
        val exportJson = ExportImportUtil.exportDownloadedVideos(listOf(downloadedVideoEntity()), ExportFormat.FULL_JSON)

        val result = ExportImportUtil.parseImportedText(exportJson)

        assertTrue(result is ImportResult.JsonSuccess)
        val jsonResult = result as ImportResult.JsonSuccess
        assertEquals(listOf(videoId), jsonResult.exportData.items.map { it.videoId }.filter { it.isNotBlank() }.distinct())
    }

    @Test
    fun `full json export of queue items contains seal compatible download history`() {
        val queueItem =
            DownloadQueueItem(
                id = 1L,
                url = watchUrl,
                title = "Test Title",
                localFileUri = "",
                mediaType = MediaType.VIDEO,
                thumbnailUrl = null,
                durationSeconds = null,
                status = DownloadQueueStatus.QUEUED,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
                errorMessage = null,
                outputPath = null,
            )

        val exportJson = ExportImportUtil.exportQueue(listOf(queueItem), ExportFormat.FULL_JSON)

        assertTrue(exportJson.contains("\"downloadHistory\""))
        assertTrue(exportJson.contains(watchUrl))
    }

    @Test
    fun `parses seal full backup json into video ids`() {
        val sealBackupJson =
            """
            {
                "templates": null,
                "shortcuts": null,
                "downloadHistory": [
                    {
                        "id": 7,
                        "videoTitle": "Test Title",
                        "videoAuthor": "Test Author",
                        "videoUrl": "$watchUrl",
                        "thumbnailUrl": "https://example.com/thumbnail.jpg",
                        "videoPath": "/storage/emulated/0/Download/test.mp4",
                        "extractor": "YouTube"
                    }
                ]
            }
            """.trimIndent()

        val result = ExportImportUtil.parseImportedText(sealBackupJson)

        assertTrue(result is ImportResult.JsonSuccess)
        val jsonResult = result as ImportResult.JsonSuccess
        assertEquals(listOf(videoId), jsonResult.exportData.items.map { it.videoId }.filter { it.isNotBlank() }.distinct())
        assertEquals(watchUrl, jsonResult.exportData.items.first().url)
        assertEquals("Test Title", jsonResult.exportData.items.first().title)
    }

    @Test
    fun `parses plain text video ids`() {
        val result = ExportImportUtil.parseImportedText("dQw4w9WgXcQ\nhttps://www.youtube.com/watch?v=dQw4w9WgXcQ")

        val success = result as ImportResult.Success
        assertEquals(listOf(videoId), success.items)
    }

    @Test
    fun `returns failure for empty import text`() {
        val result = ExportImportUtil.parseImportedText("")

        val failure = result as ImportResult.Failure
        assertEquals("No valid lines found in import text", failure.error)
    }
}
