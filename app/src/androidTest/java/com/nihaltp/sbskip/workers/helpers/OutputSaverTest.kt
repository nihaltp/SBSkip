package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.di.TestEntryPoint
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.storage.MediaFileMetadata
import dagger.hilt.EntryPoints
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OutputSaverTest {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadStorage: DownloadStorage
    private lateinit var context: Context
    private lateinit var outputSaver: OutputSaver

    private lateinit var originalFile: File
    private lateinit var tempOutputFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entryPoint = EntryPoints.get(context, TestEntryPoint::class.java)
        settingsRepository = entryPoint.settingsRepository()
        downloadStorage = entryPoint.downloadStorage()
        outputSaver = OutputSaver(context, downloadStorage, settingsRepository)

        // Setup files
        originalFile = File(context.cacheDir, "original.mp4")
        FileOutputStream(originalFile).use { it.write("original_content".toByteArray()) }

        tempOutputFile = File(context.cacheDir, "processed.mp4")
        FileOutputStream(tempOutputFile).use { it.write("processed_content".toByteArray()) }
    }

    @After
    fun tearDown() {
        if (originalFile.exists()) originalFile.delete()
        if (tempOutputFile.exists()) tempOutputFile.delete()
    }

    @Test
    fun testSafeOverwrite() =
        runBlocking {
            val item =
                DownloadQueueItem(
                    id = 1L,
                    url = "https://youtube.com/watch?v=123",
                    title = "Test",
                    localFileUri = originalFile.toURI().toString(),
                    mediaType = MediaType.VIDEO,
                    thumbnailUrl = null,
                    durationSeconds = 100,
                    status = DownloadQueueStatus.PROCESSING,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    errorMessage = null,
                    convertVideoToAudio = false, // Must be false for Case 2
                    deleteOriginalVideo = true,
                )

            // Force overwrite behavior
            settingsRepository.update { it.copy(overwriteBehavior = true) }

            val metadata =
                MediaFileMetadata(
                    title = "Test",
                    extension = "mp4",
                    durationSeconds = 100,
                )
            val plan =
                ProcessingPlan(
                    keepRanges = emptyList(),
                    convertVideoToAudio = false,
                    outputExtension = "mp4",
                )
            val processingContext =
                ProcessingContext(
                    queueItem = item,
                    localMetadata = metadata,
                    videoId = null,
                    oembedTitle = null,
                    authorName = null,
                    authorUrl = null,
                    thumbnailUrl = null,
                    sbSkipSegments = "",
                    categories = emptySet(),
                    segments = null,
                    plan = plan,
                )

            // Execute save
            val resultUri = outputSaver.save(tempOutputFile, processingContext)

            // Verify the original file has been overwritten with temp file content
            assertEquals(originalFile.toURI().toString(), resultUri)
            assertTrue(originalFile.exists())
            val finalContent = originalFile.readText()
            assertEquals("processed_content", finalContent)
        }
}
