package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.nihaltp.sbskip.model.ProcessingPlan
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.storage.DownloadStorage
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaProcessingManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `process handles on-disk file paths without a content provider`() =
        runTest(testDispatcher) {
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "mpp-cache").apply { mkdirs() }
            val context = mockk<Context>(relaxed = true)
            io.mockk.every { context.cacheDir } returns cacheDir

            val sourceFile =
                File(System.getProperty("java.io.tmpdir"), "mpp-source.mp4").apply {
                    writeText("data")
                    deleteOnExit()
                }

            val downloadStorage = mockk<DownloadStorage>(relaxed = true)
            val mediaProcessor = mockk<MediaProcessor>(relaxed = true)

            val manager = MediaProcessingManager(context, downloadStorage, mediaProcessor)
            val plan = ProcessingPlan(keepRanges = emptyList(), convertVideoToAudio = false, outputExtension = "mp4")

            val result =
                manager.process(
                    queueItemId = 7L,
                    localFileUri = sourceFile.absolutePath,
                    localExtension = "mp4",
                    plan = plan,
                    onProgress = {},
                )

            assertTrue(result.exists())
            assertTrue(result.length() > 0)
        }
}
