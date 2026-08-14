package com.nihaltp.sbskip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelsTest {
    @Test
    fun testPendingDownloadDefaultState() {
        val pending =
            PendingDownload(
                videoId = "dQw4w9WgXcQ",
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
                createdAtEpochMillis = 123456789L,
            )

        assertEquals("dQw4w9WgXcQ", pending.videoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", pending.url)
        assertEquals("Never Gonna Give You Up", pending.title)
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg", pending.thumbnailUrl)
        assertEquals(123456789L, pending.createdAtEpochMillis)
    }

    @Test
    fun testDetectedFileRelativePathAndFolderUri() {
        val detected =
            DetectedFile(
                uri = "content://dummy/uri",
                score = 100,
                relativePath = "yt/ROMH/",
                folderUri = "content://folder/uri",
            )

        assertEquals("content://dummy/uri", detected.uri)
        assertEquals(100, detected.score)
        assertEquals("yt/ROMH/", detected.relativePath)
        assertEquals("content://folder/uri", detected.folderUri)
    }

    @Test
    fun testPendingEnqueueDataRelativePath() {
        val enqueueData =
            PendingEnqueueData(
                fileUri = "content://dummy/file",
                title = "Dummy Title",
                youtubeUrl = "https://youtube.com/watch",
                mediaType = MediaType.AUDIO,
                relativePath = "yt/ROMH/",
                customFolderUri = "content://folder/uri",
            )

        assertEquals("yt/ROMH/", enqueueData.relativePath)
        assertEquals("content://folder/uri", enqueueData.customFolderUri)
    }

    @Test
    fun testPendingDownloadStateUpdate() {
        val pending =
            PendingDownload(
                videoId = "dQw4w9WgXcQ",
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
                createdAtEpochMillis = 123456789L,
            )

        // Simulate auto-detect start
        val detecting = pending.copy(isDetectingFile = true)
        assertTrue(detecting.isDetectingFile)
        assertNull(detecting.detectedFile)

        // Simulate auto-detect completion
        val detected =
            detecting.copy(
                isDetectingFile = false,
                detectedFile = DetectedFile(uri = "content://media/external/downloads/1", score = 95),
                detectedFileName = "RickAstley - Never Gonna Give You Up.mp4",
            )

        assertFalse(detected.isDetectingFile)
        assertEquals("content://media/external/downloads/1", detected.detectedFile?.uri)
        assertEquals(95, detected.detectedFile?.score)
        assertEquals("RickAstley - Never Gonna Give You Up.mp4", detected.detectedFileName)
    }

    @Test
    fun testMainUiStateWithPendingDownloadsQueue() {
        val initialUiState = MainUiState()
        assertTrue(initialUiState.pendingDownloads.isEmpty())
        assertNull(initialUiState.pendingDownloadForFilePicker)

        val pending1 =
            PendingDownload(
                videoId = "video1",
                url = "url1",
                title = "Title 1",
                thumbnailUrl = "thumb1",
                createdAtEpochMillis = 1000L,
            )

        val pending2 =
            PendingDownload(
                videoId = "video2",
                url = "url2",
                title = "Title 2",
                thumbnailUrl = "thumb2",
                createdAtEpochMillis = 2000L,
            )

        // Add pending downloads
        val stateWithDownloads =
            initialUiState.copy(
                pendingDownloads = listOf(pending1, pending2),
            )

        assertEquals(2, stateWithDownloads.pendingDownloads.size)
        assertEquals("video1", stateWithDownloads.pendingDownloads[0].videoId)
        assertEquals("video2", stateWithDownloads.pendingDownloads[1].videoId)

        // Update detection state of a specific item in the list
        val updatedDownloads =
            stateWithDownloads.pendingDownloads.map {
                if (it.videoId == "video2") {
                    it.copy(isDetectingFile = true)
                } else {
                    it
                }
            }
        val stateWithUpdatedDownload = stateWithDownloads.copy(pendingDownloads = updatedDownloads)

        assertFalse(stateWithUpdatedDownload.pendingDownloads[0].isDetectingFile)
        assertTrue(stateWithUpdatedDownload.pendingDownloads[1].isDetectingFile)

        // Cancel / remove a specific download from the list
        val stateAfterCancel =
            stateWithUpdatedDownload.copy(
                pendingDownloads = stateWithUpdatedDownload.pendingDownloads.filter { it.videoId != "video1" },
            )

        assertEquals(1, stateAfterCancel.pendingDownloads.size)
        assertEquals("video2", stateAfterCancel.pendingDownloads[0].videoId)
    }

    @Test
    fun testManualPickerCoordinatorState() {
        val initialUiState = MainUiState()
        assertNull(initialUiState.pendingDownloadForFilePicker)

        val pending =
            PendingDownload(
                videoId = "dQw4w9WgXcQ",
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
                createdAtEpochMillis = 123456789L,
            )

        // Set pending download for file picker
        val stateWithPicker =
            initialUiState.copy(
                pendingDownloadForFilePicker = pending,
            )

        assertEquals("dQw4w9WgXcQ", stateWithPicker.pendingDownloadForFilePicker?.videoId)

        // Clear manual picker coordinator after selection is done
        val stateCleared =
            stateWithPicker.copy(
                pendingDownloadForFilePicker = null,
            )

        assertNull(stateCleared.pendingDownloadForFilePicker)
    }

    @Test
    fun testDownloadQueueItemAudioOutputDirUri() {
        val item =
            DownloadQueueItem(
                id = 1L,
                url = "https://youtube.com/watch?v=123",
                title = "Test Clean",
                localFileUri = "content://media/external/video/1",
                mediaType = MediaType.AUDIO,
                thumbnailUrl = null,
                durationSeconds = 120L,
                status = DownloadQueueStatus.QUEUED,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                errorMessage = null,
                outputPath = null,
                convertVideoToAudio = true,
                deleteOriginalVideo = true,
                audioOutputDirUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                videoOutputDirUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
            )

        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AMusic", item.audioOutputDirUri)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AMovies", item.videoOutputDirUri)
        assertTrue(item.convertVideoToAudio)
    }

    @Test
    fun testCleanUrlWithSbskipScheme() {
        val item =
            DownloadQueueItem(
                id = 1L,
                url = "sbskip://local?overwrite=true",
                title = "Test Clean",
                localFileUri = "content://media/external/video/1",
                mediaType = MediaType.AUDIO,
                thumbnailUrl = null,
                durationSeconds = 120L,
                status = DownloadQueueStatus.QUEUED,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                errorMessage = null,
                outputPath = null,
                convertVideoToAudio = true,
                deleteOriginalVideo = true,
                audioOutputDirUri = null,
            )

        assertEquals("", item.cleanUrl)
    }

    @Test
    fun testDisplayDuration() {
        val baseItem =
            DownloadQueueItem(
                id = 1L,
                url = "https://youtube.com/watch?v=123",
                title = "Test",
                localFileUri = "content://media/external/video/1",
                mediaType = MediaType.VIDEO,
                thumbnailUrl = null,
                durationSeconds = null,
                status = DownloadQueueStatus.QUEUED,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                errorMessage = null,
                outputPath = null,
                outputDurationSeconds = null,
                convertVideoToAudio = false,
                deleteOriginalVideo = false,
            )

        // 1. Both null -> "--:--"
        assertEquals("--:--", baseItem.copy(durationSeconds = null, outputDurationSeconds = null).displayDuration)

        // 2. Both zero -> "--:--"
        assertEquals("--:--", baseItem.copy(durationSeconds = 0L, outputDurationSeconds = 0L).displayDuration)

        // 3. Original only -> "3:50"
        assertEquals("3:50", baseItem.copy(durationSeconds = 230L, outputDurationSeconds = null).displayDuration)

        // 4. Original and Output same -> "3:50"
        assertEquals("3:50", baseItem.copy(durationSeconds = 230L, outputDurationSeconds = 230L).displayDuration)

        // 5. Output less than original -> "3:50 (2:50)"
        assertEquals("3:50 (2:50)", baseItem.copy(durationSeconds = 230L, outputDurationSeconds = 170L).displayDuration)

        // 6. Original with 0 output duration -> "3:50"
        assertEquals("3:50", baseItem.copy(durationSeconds = 230L, outputDurationSeconds = 0L).displayDuration)

        // 7. Hours format
        assertEquals("1:00:50 (0:50)", baseItem.copy(durationSeconds = 3650L, outputDurationSeconds = 50L).displayDuration)
    }
}
