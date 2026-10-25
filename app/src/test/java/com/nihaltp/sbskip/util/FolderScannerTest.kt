package com.nihaltp.sbskip.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderScannerTest {
    @Test
    fun `isMediaFile recognizes video extensions`() {
        assertEquals(true, FolderScanner.isMediaFile("video.mp4"))
        assertEquals(true, FolderScanner.isMediaFile("video.mkv"))
        assertEquals(true, FolderScanner.isMediaFile("video.mov"))
        assertEquals(true, FolderScanner.isMediaFile("video.avi"))
    }

    @Test
    fun `isMediaFile recognizes audio extensions`() {
        assertEquals(true, FolderScanner.isMediaFile("song.mp3"))
        assertEquals(true, FolderScanner.isMediaFile("song.m4a"))
        assertEquals(true, FolderScanner.isMediaFile("song.flac"))
        assertEquals(true, FolderScanner.isMediaFile("song.wav"))
    }

    @Test
    fun `isMediaFile rejects non-media extensions`() {
        assertEquals(false, FolderScanner.isMediaFile("document.pdf"))
        assertEquals(false, FolderScanner.isMediaFile("image.jpg"))
        assertEquals(false, FolderScanner.isMediaFile("text.txt"))
        assertEquals(false, FolderScanner.isMediaFile("noextension"))
    }

    @Test
    fun `isMediaFile is case insensitive`() {
        assertEquals(true, FolderScanner.isMediaFile("video.MP4"))
        assertEquals(true, FolderScanner.isMediaFile("song.MP3"))
    }

    @Test
    fun `isHiddenFolderName detects dot prefixed folder names`() {
        assertEquals(true, FolderScanner.isHiddenFolderName(".git"))
        assertEquals(true, FolderScanner.isHiddenFolderName(".cache"))
        assertEquals(true, FolderScanner.isHiddenFolderName("."))
    }

    @Test
    fun `isHiddenFolderName accepts regular folder names`() {
        assertEquals(false, FolderScanner.isHiddenFolderName("Movies"))
        assertEquals(false, FolderScanner.isHiddenFolderName("SB Skip"))
        assertEquals(false, FolderScanner.isHiddenFolderName("Trailer 2024"))
        assertEquals(false, FolderScanner.isHiddenFolderName(null))
    }
}
