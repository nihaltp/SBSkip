package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.ProcessingContext
import com.nihaltp.sbskip.model.ProcessingPlan
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSegment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaTaggerTest {
    private lateinit var context: Context
    private lateinit var coverArtManager: CoverArtManager
    private lateinit var mediaTagger: MediaTagger
    private val capturedCommands = mutableListOf<String>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        coverArtManager = mockk(relaxed = true)
        mediaTagger = MediaTagger(context, coverArtManager)
        capturedCommands.clear()
    }

    @After
    fun teardown() {
        unmockkStatic(FFmpegKit::class)
    }

    private fun createDummyQueueItem(): DownloadQueueItem {
        return DownloadQueueItem(
            id = 1L,
            url = "https://youtube.com/watch?v=test",
            title = "Test",
            localFileUri = "",
            mediaType = MediaType.AUDIO,
            thumbnailUrl = null,
            durationSeconds = 0L,
            status = DownloadQueueStatus.PROCESSING,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            errorMessage = null,
        )
    }

    @Test
    fun `applyMetadata should write SBSKIP_* tags instead of comment JSON`() {
        runBlocking {
            // Arrange
            val tmpDir = System.getProperty("java.io.tmpdir")
            val inputFile = File(tmpDir, "test_input_${System.currentTimeMillis()}.mp4")
            inputFile.outputStream().use { it.write("dummy".toByteArray()) }

            mockkStatic(FFmpegKit::class)
            every { FFmpegKit.execute(any()) } answers {
                val cmd = args[0] as String
                capturedCommands.add(cmd)
                val session = mockk<FFmpegSession>(relaxed = true)
                every { session.returnCode } returns mockk<ReturnCode>(relaxed = true)
                session
            }

            every { coverArtManager.audioHasCoverImage(any()) } returns true

            val processingContext =
                ProcessingContext(
                    queueItem = createDummyQueueItem(),
                    localMetadata = MediaFileMetadata("Test", "mp4", null),
                    videoId = "dQw4w9WgXcQ",
                    oembedTitle = "Test Video",
                    authorName = "Test Author",
                    authorUrl = "https://youtube.com/@testauthor",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    sbSkipSegments = "0-10;20-30",
                    categories = setOf(SponsorBlockCategory.SPONSOR),
                    segments = listOf(SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 0.0, 10.0)),
                    plan = ProcessingPlan(emptyList(), false, "mp4"),
                )

            // Act
            mediaTagger.applyMetadata(inputFile, processingContext)

            // Assert
            assertTrue("Should have captured a command", capturedCommands.isNotEmpty())
            val command = capturedCommands.last()

            // Verify each SBSKIP tag includes its full key="value" assignment
            assertTrue(
                "Should contain SBSKIP_YOUTUBE_VIDEO_ID with value",
                command.contains("-metadata SBSKIP_YOUTUBE_VIDEO_ID=\"dQw4w9WgXcQ\""),
            )
            assertTrue(
                "Should contain SBSKIP_YOUTUBE_VIDEO_URL with value",
                command.contains("-metadata SBSKIP_YOUTUBE_VIDEO_URL=\"https://youtube.com/watch?v=test\""),
            )
            assertTrue("Should contain SBSKIP_YOUTUBE_TITLE with value", command.contains("-metadata SBSKIP_YOUTUBE_TITLE=\"Test Video\""))
            assertTrue("Should contain SBSKIP_AUTHOR_NAME with value", command.contains("-metadata SBSKIP_AUTHOR_NAME=\"Test Author\""))
            assertTrue(
                "Should contain SBSKIP_AUTHOR_URL with value",
                command.contains("-metadata SBSKIP_AUTHOR_URL=\"https://youtube.com/@testauthor\""),
            )
            assertTrue(
                "Should contain SBSKIP_THUMBNAIL_URL with value",
                command.contains("-metadata SBSKIP_THUMBNAIL_URL=\"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg\""),
            )
            assertTrue("Should contain SBSKIP_SEGMENTS with value", command.contains("-metadata SBSKIP_SEGMENTS=\"0-10;20-30\""))
            assertTrue("Should contain SBSKIP_VERSION tag", command.contains("-metadata SBSKIP_VERSION=\"") && command.contains("\""))
            assertTrue("Should contain SBSKIP_PROCESSED with value", command.contains("-metadata SBSKIP_PROCESSED=\"true\""))
            assertTrue(
                "Should contain SBSKIP_PROCESSED_AT with value",
                command.contains("-metadata SBSKIP_PROCESSED_AT=\"") && command.contains("\""),
            )
            assertTrue(
                "Should contain SBSKIP_REMOVED_CATEGORIES with value",
                command.contains("-metadata SBSKIP_REMOVED_CATEGORIES=\"SPONSOR\""),
            )
            assertTrue("Should NOT contain comment JSON tag", !command.contains("comment=\""))
            assertTrue("Should NOT contain old SB_ prefix", !command.contains("SB_VERSION"))

            inputFile.delete()
        }
    }

    @Test
    fun `resolveCategoriesForMetadata should return intersection of selected and detected categories`() {
        // Arrange
        val plan = ProcessingPlan(emptyList(), false, "mp3")
        val processingContext =
            ProcessingContext(
                queueItem = createDummyQueueItem(),
                localMetadata = MediaFileMetadata("Test", "mp3", null),
                videoId = "test",
                oembedTitle = "Test",
                authorName = "Author",
                authorUrl = "",
                thumbnailUrl = "",
                sbSkipSegments = "",
                categories = setOf(SponsorBlockCategory.SPONSOR, SponsorBlockCategory.INTRO, SponsorBlockCategory.OUTRO),
                segments =
                    listOf(
                        SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 0.0, 10.0),
                        SponsorBlockSegment(SponsorBlockCategory.INTRO, 10.0, 20.0),
                        // Not selected
                        SponsorBlockSegment(SponsorBlockCategory.MUSIC_OFFTOPIC, 20.0, 30.0),
                    ),
                plan = plan,
            )

        // Act
        val result = mediaTagger.resolveCategoriesForMetadata(processingContext)

        // Assert
        val expected = listOf(SponsorBlockCategory.SPONSOR.name, SponsorBlockCategory.INTRO.name)
        assertEquals(expected.sorted(), result.sorted())
    }

    @Test
    fun `resolveCategoriesForMetadata should return empty list if segments is null`() {
        // Arrange
        val plan = ProcessingPlan(emptyList(), false, "mp3")
        val processingContext =
            ProcessingContext(
                queueItem = createDummyQueueItem(),
                localMetadata = MediaFileMetadata("Test", "mp3", null),
                videoId = "test",
                oembedTitle = "Test",
                authorName = "Author",
                authorUrl = "",
                thumbnailUrl = "",
                sbSkipSegments = "",
                categories = setOf(SponsorBlockCategory.SPONSOR, SponsorBlockCategory.INTRO),
                segments = null,
                plan = plan,
            )

        // Act
        val result = mediaTagger.resolveCategoriesForMetadata(processingContext)

        // Assert
        assertEquals(emptyList<String>(), result)
    }
}
