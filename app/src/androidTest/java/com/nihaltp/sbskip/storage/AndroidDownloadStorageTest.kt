package com.nihaltp.sbskip.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.di.TestEntryPoint
import com.nihaltp.sbskip.model.WatchlistFolder
import dagger.hilt.EntryPoints
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDownloadStorageTest {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadStorage: AndroidDownloadStorage
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entryPoint = EntryPoints.get(context, TestEntryPoint::class.java)
        settingsRepository = entryPoint.settingsRepository()
        downloadStorage = AndroidDownloadStorage(context, settingsRepository)
    }

    @Test
    fun testGetMatchedWatchlistFolder_withSubfolder() =
        runBlocking {
            // Setup watchlist folder
            val watchlistFolder =
                WatchlistFolder(
                    path = "Download/NewPipe",
                    uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FNewPipe",
                )

            settingsRepository.update {
                it.copy(watchlist = listOf(watchlistFolder))
            }

            // Test with a file uri in a subfolder
            val fileUri = "file:///storage/emulated/0/Download/NewPipe/Playlist/video.mp4"
            val matchedFolder = downloadStorage.getMatchedWatchlistFolder(fileUri)

            assertNotNull("Matched folder should not be null", matchedFolder)
            assertEquals("Should match the configured watchlist folder", watchlistFolder.path, matchedFolder?.folder?.path)
            assertEquals("Relative path should reflect the subfolder structure", "Playlist", matchedFolder?.relativePath)

            // Test with a file uri directly in the folder
            val fileUriDirect = "file:///storage/emulated/0/Download/NewPipe/video.mp4"
            val matchedFolderDirect = downloadStorage.getMatchedWatchlistFolder(fileUriDirect)

            assertNotNull("Matched folder should not be null for direct file", matchedFolderDirect)
            assertEquals("Relative path should be empty for direct file", "", matchedFolderDirect?.relativePath)

            // Test with a file uri outside the folder
            val fileUriOutside = "file:///storage/emulated/0/Download/OtherFolder/video.mp4"
            val matchedFolderOutside = downloadStorage.getMatchedWatchlistFolder(fileUriOutside)

            assertNull("Matched folder should be null for outside file", matchedFolderOutside)
        }
}
