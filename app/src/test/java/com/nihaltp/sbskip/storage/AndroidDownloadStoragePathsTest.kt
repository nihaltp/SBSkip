package com.nihaltp.sbskip.storage

import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidDownloadStoragePathsTest {
    @Test
    fun testComputeStoragePaths_withSAFCustomFolderAndOverlappingRelativePath() {
        val paths =
            computeStoragePaths(
                mediaType = MediaType.VIDEO,
                customFolderUri = "content://something/tree/primary%3ADownload%2FYT",
                resolvedCustomFolderName = "YT",
                videoFolderSetting = "Movies/SB Skip",
                audioFolderSetting = "Music/SB Skip",
                relativePath = "YT/MALAYALAM",
            )
        // effectiveRelativePath should strip "YT"
        assertEquals("MALAYALAM", paths.effectiveRelativePath)
        // MediaStore folder should still resolve properly (using YT as base, though custom folder resolution without tree puts it where?)
        // The first segment of "YT" is not in allowed dirs, so it falls back to Movies/SB Skip, and appends the stripped relative path.
        assertEquals("Movies/SB Skip/MALAYALAM", paths.mediaStoreFolder)
    }

    @Test
    fun testComputeStoragePaths_withMediaStoreSettingsAndOverlappingRelativePath() {
        val paths =
            computeStoragePaths(
                mediaType = MediaType.VIDEO,
                customFolderUri = null,
                resolvedCustomFolderName = null,
                videoFolderSetting = "Movies/SB Skip",
                audioFolderSetting = "Music/SB Skip",
                relativePath = "Movies/SB Skip/MALAYALAM",
            )
        // effectiveRelativePath should strip "Movies/SB Skip"
        assertEquals("MALAYALAM", paths.effectiveRelativePath)
        assertEquals("Movies/SB Skip/MALAYALAM", paths.mediaStoreFolder)
    }

    @Test
    fun testComputeStoragePaths_withNoRelativePath() {
        val paths =
            computeStoragePaths(
                mediaType = MediaType.VIDEO,
                customFolderUri = null,
                resolvedCustomFolderName = null,
                videoFolderSetting = "Movies/SB Skip",
                audioFolderSetting = "Music/SB Skip",
                relativePath = null,
            )
        assertNull(paths.effectiveRelativePath)
        assertEquals("Movies/SB Skip", paths.mediaStoreFolder)
    }

    @Test
    fun testComputeStoragePaths_withAudioAndDownloads() {
        val paths =
            computeStoragePaths(
                mediaType = MediaType.AUDIO,
                customFolderUri = null,
                resolvedCustomFolderName = null,
                videoFolderSetting = "Movies/SB Skip",
                audioFolderSetting = "Download/SB Audio",
                relativePath = "Download/SB Audio/Podcasts/Tech",
            )
        assertEquals("Podcasts/Tech", paths.effectiveRelativePath)
        assertEquals("Download/SB Audio/Podcasts/Tech", paths.mediaStoreFolder)
        assertEquals(true, paths.useDownloadsUri)
    }
}
