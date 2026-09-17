package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.ProcessingContext
import com.nihaltp.sbskip.model.ProcessingPlan
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSegment
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MediaTaggerTest {
    private lateinit var context: Context
    private lateinit var coverArtManager: CoverArtManager
    private lateinit var mediaTagger: MediaTagger

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        coverArtManager = mockk(relaxed = true)
        mediaTagger = MediaTagger(context, coverArtManager)
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
