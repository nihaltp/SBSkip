package com.nihaltp.sbskip.storage

import com.nihaltp.sbskip.model.MatchedWatchFolder
import com.nihaltp.sbskip.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadStorageTest {
    private class FakeDownloadStorage : DownloadStorage {
        val deletedUris = mutableListOf<String>()

        override suspend fun deleteTemporaryFile(path: String) {}

        override suspend fun copyUriToTempFile(
            uriString: String,
            tempFile: File,
        ) {}

        override suspend fun saveToPublicStorage(
            tempFile: File,
            title: String,
            extension: String,
            mediaType: MediaType,
            customFolderUri: String?,
            overwrite: Boolean,
            relativePath: String?,
        ): String {
            return ""
        }

        override suspend fun queryMetadata(uriString: String): MediaFileMetadata? {
            return null
        }

        override suspend fun deleteUri(uriString: String): Boolean {
            deletedUris.add(uriString)
            return true
        }

        override suspend fun checkFileExists(
            title: String,
            extension: String,
            mediaType: MediaType,
            customFolderUri: String?,
        ): Boolean {
            return false
        }

        override suspend fun getUniqueTitle(
            baseTitle: String,
            extension: String,
            mediaType: MediaType,
            customFolderUri: String?,
        ): String {
            return "${baseTitle}_1"
        }

        override suspend fun getMatchedWatchlistFolder(localFileUri: String): MatchedWatchFolder? {
            return null
        }

        override suspend fun hasPersistedPermission(uriString: String): Boolean {
            return true
        }
    }

    @Test
    fun testDeleteUriInvoked() =
        kotlinx.coroutines.runBlocking {
            val fakeStorage = FakeDownloadStorage()
            val uri = "content://media/external/video/1"
            val result = fakeStorage.deleteUri(uri)

            assertTrue(result)
            assertEquals(1, fakeStorage.deletedUris.size)
            assertEquals(uri, fakeStorage.deletedUris[0])
        }
}
