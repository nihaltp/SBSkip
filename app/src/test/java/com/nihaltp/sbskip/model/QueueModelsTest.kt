package com.nihaltp.sbskip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelsTest {

    @Test
    fun testPendingDownloadDefaultState() {
        val pending = PendingDownload(
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
        assertNull(pending.detectedFile)
        assertNull(pending.detectedFileName)
        assertFalse(pending.isDetectingFile)
    }

    @Test
    fun testPendingDownloadStateUpdate() {
        val pending = PendingDownload(
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
        val detected = detecting.copy(
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

        val pending1 = PendingDownload(
            videoId = "video1",
            url = "url1",
            title = "Title 1",
            thumbnailUrl = "thumb1",
            createdAtEpochMillis = 1000L,
        )

        val pending2 = PendingDownload(
            videoId = "video2",
            url = "url2",
            title = "Title 2",
            thumbnailUrl = "thumb2",
            createdAtEpochMillis = 2000L,
        )

        // Add pending downloads
        val stateWithDownloads = initialUiState.copy(
            pendingDownloads = listOf(pending1, pending2),
        )

        assertEquals(2, stateWithDownloads.pendingDownloads.size)
        assertEquals("video1", stateWithDownloads.pendingDownloads[0].videoId)
        assertEquals("video2", stateWithDownloads.pendingDownloads[1].videoId)

        // Update detection state of a specific item in the list
        val updatedDownloads = stateWithDownloads.pendingDownloads.map {
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
        val stateAfterCancel = stateWithUpdatedDownload.copy(
            pendingDownloads = stateWithUpdatedDownload.pendingDownloads.filter { it.videoId != "video1" },
        )

        assertEquals(1, stateAfterCancel.pendingDownloads.size)
        assertEquals("video2", stateAfterCancel.pendingDownloads[0].videoId)
    }

    @Test
    fun testManualPickerCoordinatorState() {
        val initialUiState = MainUiState()
        assertNull(initialUiState.pendingDownloadForFilePicker)

        val pending = PendingDownload(
            videoId = "dQw4w9WgXcQ",
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
            createdAtEpochMillis = 123456789L,
        )

        // Set pending download for file picker
        val stateWithPicker = initialUiState.copy(
            pendingDownloadForFilePicker = pending,
        )

        assertEquals("dQw4w9WgXcQ", stateWithPicker.pendingDownloadForFilePicker?.videoId)

        // Clear manual picker coordinator after selection is done
        val stateCleared = stateWithPicker.copy(
            pendingDownloadForFilePicker = null,
        )

        assertNull(stateCleared.pendingDownloadForFilePicker)
    }

    @Test
    fun testDownloadQueueItemAudioOutputDirUri() {
        val item = DownloadQueueItem(
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
        )

        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AMusic", item.audioOutputDirUri)
        assertTrue(item.convertVideoToAudio)
    }

    @Test
    fun testMainUiStatePendingAudioFolderPick() {
        val initialState = MainUiState()
        assertNull(initialState.pendingAudioFolderPick)

        val pendingPick = PendingAudioFolderPick(
            target = AudioFolderPickTarget.SUBMIT,
            force = true,
            fileUri = "content://media/external/video/1",
            displayName = "Custom Title.mp4",
        )

        val stateWithPick = initialState.copy(
            pendingAudioFolderPick = pendingPick,
        )

        assertEquals(AudioFolderPickTarget.SUBMIT, stateWithPick.pendingAudioFolderPick?.target)
        assertTrue(stateWithPick.pendingAudioFolderPick?.force ?: false)
        assertEquals("content://media/external/video/1", stateWithPick.pendingAudioFolderPick?.fileUri)
        assertEquals("Custom Title.mp4", stateWithPick.pendingAudioFolderPick?.displayName)

        val stateCleared = stateWithPick.copy(pendingAudioFolderPick = null)
        assertNull(stateCleared.pendingAudioFolderPick)
    }

    @Test
    fun testCleanUrlWithSbskipScheme() {
        val item = DownloadQueueItem(
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
}
