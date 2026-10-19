package com.nihaltp.sbskip.ui.history

import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportFromFolderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var ffmpegMetadataReader: FfmpegMetadataReader
    private lateinit var viewModel: ImportFromFolderViewModel

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        downloadedVideoRepository = mockk(relaxed = true)
        ffmpegMetadataReader = mockk(relaxed = true)
        viewModel = ImportFromFolderViewModel(downloadedVideoRepository, ffmpegMetadataReader, mockk())
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `toggleCandidate adds and removes from selection`() =
        runTest(testDispatcher) {
            val candidate = candidate("abc123", "Video 1")
            viewModel.candidatesState.value = listOf(candidate)

            viewModel.toggleCandidate(candidate)

            assertEquals(1, viewModel.selectedCount.first())
            assertTrue(viewModel.candidates.value.first { it.id == candidate.id }.isSelected)

            viewModel.toggleCandidate(candidate)

            assertEquals(0, viewModel.selectedCount.first())
            assertFalse(viewModel.candidates.value.first { it.id == candidate.id }.isSelected)
        }

    @Test
    fun `toggleSelectAll selects and deselects all`() =
        runTest(testDispatcher) {
            viewModel.candidatesState.value =
                listOf(
                    candidate("abc1", "Video 1"),
                    candidate("abc2", "Video 2"),
                )

            // Select all via toggleSelectAll
            viewModel.toggleSelectAll()

            assertEquals(2, viewModel.selectedCount.first())
            assertTrue(viewModel.candidates.value.all { it.isSelected })

            // Deselect all via toggleSelectAll
            viewModel.toggleSelectAll()

            assertEquals(0, viewModel.selectedCount.first())
            assertFalse(viewModel.candidates.value.any { it.isSelected })
        }

    @Test
    fun `importSelected inserts selected candidates into repository`() =
        runTest(testDispatcher) {
            val candidate1 = candidate("abc123", "Video 1")
            viewModel.candidatesState.value = listOf(candidate1)
            viewModel.toggleCandidate(candidate1)

            val insertedSlot = slot<DownloadedVideoEntity>()
            coEvery { downloadedVideoRepository.insert(capture(insertedSlot)) } returns 1L

            viewModel.importSelected()
            runCurrent()

            coVerify(exactly = 1) { downloadedVideoRepository.insert(capture(insertedSlot)) }
            assertEquals("abc123", insertedSlot.captured.videoId)
            assertEquals("https://img.youtube.com/vi/abc123/mqdefault.jpg", insertedSlot.captured.thumbnailUrl)
            assertEquals(0, viewModel.selectedCount.first())
        }

    @Test
    fun `importSelected does nothing when nothing selected`() =
        runTest(testDispatcher) {
            coEvery { downloadedVideoRepository.insert(any()) } returns 1L

            viewModel.importSelected()

            coVerify(exactly = 0) { downloadedVideoRepository.insert(any()) }
        }

    companion object {
        fun candidate(
            youtubeId: String,
            fileName: String,
            authorName: String? = null,
            fileSize: Long? = null,
            youtubeTitle: String? = null,
        ) = ImportCandidate(
            uri = android.net.Uri.parse("content://media/external/video/1"),
            fileName = fileName,
            youtubeId = youtubeId,
            youtubeUrl = null,
            youtubeTitle = youtubeTitle,
            authorName = authorName,
            fileSize = fileSize,
        )
    }
}
