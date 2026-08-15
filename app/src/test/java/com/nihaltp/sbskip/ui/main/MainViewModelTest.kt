package com.nihaltp.sbskip.ui.main

import android.content.Context
import android.content.pm.PackageManager
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.QueueActionResult
import com.nihaltp.sbskip.storage.DownloadStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var queueRepository: QueueRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadStorage: DownloadStorage

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Use a mock context so context.getString() never throws Resources$NotFoundException.
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()

        context = mockk(relaxed = true)
        every { context.getString(any<Int>()) } returns ""
        every { context.packageManager } returns packageManager

        queueRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        downloadStorage = mockk(relaxed = true)

        coEvery { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { queueRepository.observeQueue() } returns flowOf(emptyList())

        viewModel =
            MainViewModel(
                queueRepository = queueRepository,
                settingsRepository = settingsRepository,
                downloadStorage = downloadStorage,
                context = context,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper to inject state ─────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun injectUiState(transform: (com.nihaltp.sbskip.model.MainUiState) -> com.nihaltp.sbskip.model.MainUiState) {
        val field = MainViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        val stateFlow =
            field.get(viewModel) as
                kotlinx.coroutines.flow.MutableStateFlow<com.nihaltp.sbskip.model.MainUiState>
        stateFlow.value = transform(stateFlow.value)
    }

    @Test
    fun `retryQueueItem rescans folders if not bypassed`() =
        runTest(testDispatcher) {
            val itemId = 1L
            val item =
                DownloadQueueItem(
                    id = itemId,
                    url = "https://youtube.com/watch?v=123",
                    title = "Test",
                    localFileUri = "old_uri",
                    mediaType = MediaType.VIDEO,
                    thumbnailUrl = null,
                    durationSeconds = 100L,
                    status = DownloadQueueStatus.FAILED,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    errorMessage = "Picked file duration mismatch",
                )

            coEvery { queueRepository.findItemById(itemId) } returns item
            coEvery { queueRepository.retry(itemId, false) } returns QueueActionResult(true, "Retrying")

            viewModel.retryQueueItem(itemId, bypassDurationCheck = false)
            // First pass: run the coroutine up to withContext(Dispatchers.IO) inside
            // collectRecentCandidates, which dispatches work to a real IO thread.
            testDispatcher.scheduler.advanceUntilIdle()
            // Give the real Dispatchers.IO thread time to finish (work is trivial: empty watchlist).
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(100)
            // Second pass: process the IO-completion resumption so retry() is called.
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify it queries the DB to find the item to rescan
            coVerify { queueRepository.findItemById(itemId) }
            // Verify it retries
            coVerify { queueRepository.retry(itemId, false) }
        }

    @Test
    fun `retryQueueItem skips rescan if bypassed`() =
        runTest(testDispatcher) {
            val itemId = 1L
            coEvery { queueRepository.retry(itemId, true) } returns QueueActionResult(true, "Retrying")

            viewModel.retryQueueItem(itemId, bypassDurationCheck = true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify it does NOT query the DB to rescan
            coVerify(exactly = 0) { queueRepository.findItemById(itemId) }
            // Verify it retries with bypass = true
            coVerify { queueRepository.retry(itemId, true) }
        }

    // ── feat: add download functionality for queue items via NewPipe (f870657) ─

    @Test
    fun `downloadQueueItemViaNewPipe shows toast when NewPipe is not installed`() =
        runTest(testDispatcher) {
            // NewPipe is NOT installed (isNewPipeInstalled defaults to false)
            val item =
                DownloadQueueItem(
                    id = 1L,
                    url = "https://youtube.com/watch?v=abc",
                    title = "Test",
                    localFileUri = "content://uri",
                    mediaType = MediaType.VIDEO,
                    thumbnailUrl = null,
                    durationSeconds = null,
                    status = DownloadQueueStatus.FAILED,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    errorMessage = null,
                )

            viewModel.downloadQueueItemViaNewPipe(item)

            // Check state immediately — downloadQueueItemViaNewPipe is synchronous in the
            // "not installed" path. Calling advanceUntilIdle() would run scheduleToastDismiss
            // and remove the toast before we can observe it.
            val state = viewModel.uiState.value
            assert(state.toastMessages.isNotEmpty()) {
                "Expected a toast message when NewPipe is not installed"
            }
        }

    // ── feat: implement playlist download functionality (c705a604) ─────────────

    @Test
    fun `skipPlaylistVideo advances currentIndex to next video`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    com.nihaltp.sbskip.model.PlaylistVideo("v1", "Title 1", null),
                    com.nihaltp.sbskip.model.PlaylistVideo("v2", "Title 2", null),
                )
            injectUiState {
                it.copy(
                    playlistDownloadState =
                        com.nihaltp.sbskip.model.PlaylistDownloadState(
                            playlistId = "PL123",
                            title = "My Playlist",
                            videos = videos,
                            currentIndex = 0,
                        ),
                )
            }

            viewModel.skipPlaylistVideo()

            val newState = viewModel.uiState.value.playlistDownloadState
            assert(newState != null) { "Playlist state should not be null after skipping first video" }
            assert(newState!!.currentIndex == 1) {
                "Expected currentIndex=1 after skipping, got ${newState.currentIndex}"
            }
        }

    @Test
    fun `skipPlaylistVideo clears playlistDownloadState when last video is skipped`() =
        runTest(testDispatcher) {
            val videos = listOf(com.nihaltp.sbskip.model.PlaylistVideo("v1", "Only Video", null))
            injectUiState {
                it.copy(
                    playlistDownloadState =
                        com.nihaltp.sbskip.model.PlaylistDownloadState(
                            playlistId = "PL123",
                            title = "My Playlist",
                            videos = videos,
                            currentIndex = 0,
                        ),
                )
            }

            viewModel.skipPlaylistVideo()

            assert(viewModel.uiState.value.playlistDownloadState == null) {
                "Playlist state should be null after skipping the last video"
            }
        }

    @Test
    fun `cancelPlaylistDownload clears playlistDownloadState`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    playlistDownloadState =
                        com.nihaltp.sbskip.model.PlaylistDownloadState(
                            playlistId = "PL123",
                            title = "My Playlist",
                            videos = listOf(com.nihaltp.sbskip.model.PlaylistVideo("v1", "T", null)),
                        ),
                )
            }

            viewModel.cancelPlaylistDownload()

            assert(viewModel.uiState.value.playlistDownloadState == null) {
                "Playlist state should be null after cancel"
            }
        }

    @Test
    fun `setPlaylistConvertVideoToAudio updates playlist convert flag`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    playlistDownloadState =
                        com.nihaltp.sbskip.model.PlaylistDownloadState(
                            playlistId = "PL123",
                            title = "My Playlist",
                            videos = listOf(com.nihaltp.sbskip.model.PlaylistVideo("v1", "T", null)),
                            convertVideoToAudio = false,
                        ),
                )
            }

            viewModel.setPlaylistConvertVideoToAudio(true)

            val updated = viewModel.uiState.value.playlistDownloadState
            assert(updated?.convertVideoToAudio == true) {
                "Expected convertVideoToAudio=true, got ${updated?.convertVideoToAudio}"
            }
        }

    @Test
    fun `setPlaylistDeleteOriginalVideo updates playlist delete flag`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    playlistDownloadState =
                        com.nihaltp.sbskip.model.PlaylistDownloadState(
                            playlistId = "PL123",
                            title = "My Playlist",
                            videos = listOf(com.nihaltp.sbskip.model.PlaylistVideo("v1", "T", null)),
                            deleteOriginalVideo = true,
                        ),
                )
            }

            viewModel.setPlaylistDeleteOriginalVideo(false)

            val updated = viewModel.uiState.value.playlistDownloadState
            assert(updated?.deleteOriginalVideo == false) {
                "Expected deleteOriginalVideo=false, got ${updated?.deleteOriginalVideo}"
            }
        }

    // ── feat: countdown/auto-detect — triggerAutoDetectNow (b3190779) ──────────

    @Test
    fun `triggerAutoDetectNow clears estimatedReadyAtEpochMillis for target pendingDownload`() =
        runTest(testDispatcher) {
            val pending =
                com.nihaltp.sbskip.model.PendingDownload(
                    videoId = "vid1",
                    url = "https://youtube.com/watch?v=vid1",
                    title = "Test",
                    thumbnailUrl = null,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    estimatedReadyAtEpochMillis = System.currentTimeMillis() + 60_000L,
                )
            injectUiState { it.copy(pendingDownloads = listOf(pending)) }

            viewModel.triggerAutoDetectNow(pending)
            testDispatcher.scheduler.advanceUntilIdle()

            // Either the item is removed (auto-detect ran) OR its timer is cleared
            val updatedPending = viewModel.uiState.value.pendingDownloads.find { it.videoId == "vid1" }
            if (updatedPending != null) {
                assert(updatedPending.estimatedReadyAtEpochMillis == null) {
                    "Expected estimatedReadyAtEpochMillis=null after triggerAutoDetectNow, got ${updatedPending.estimatedReadyAtEpochMillis}"
                }
            }
        }

    // ── refactor: streamline toast handling (13124c04) ────────────────────────

    @Test
    fun `consumeSnackbarMessage clears all toast messages`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    toastMessages =
                        listOf(
                            com.nihaltp.sbskip.model.ToastMessage(message = "Toast 1"),
                            com.nihaltp.sbskip.model.ToastMessage(message = "Toast 2"),
                        ),
                )
            }

            viewModel.consumeSnackbarMessage()

            assert(viewModel.uiState.value.toastMessages.isEmpty()) {
                "Expected toastMessages to be empty after consumeSnackbarMessage"
            }
        }

    // ── feat: add updateLocalFileUri (6f3361) — VM integration ────────────────

    @Test
    fun `retryQueueItem always calls repository retry even when no better candidate is found`() =
        runTest(testDispatcher) {
            val itemId = 42L
            val item =
                DownloadQueueItem(
                    id = itemId,
                    url = "https://youtube.com/watch?v=XYZ",
                    title = "Song",
                    localFileUri = "content://old/uri",
                    mediaType = MediaType.VIDEO,
                    thumbnailUrl = null,
                    durationSeconds = 200L,
                    status = DownloadQueueStatus.FAILED,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    errorMessage = "Picked file duration mismatch",
                )

            coEvery { queueRepository.findItemById(itemId) } returns item
            coEvery { queueRepository.retry(itemId, false) } returns QueueActionResult(true, "Retrying")
            // downloadStorage is relaxed mock — no matching watchlist folders means no better candidate

            viewModel.retryQueueItem(itemId, bypassDurationCheck = false)
            testDispatcher.scheduler.advanceUntilIdle()
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(100)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { queueRepository.retry(itemId, false) }
        }

    // ── UI state helpers ───────────────────────────────────────────────────────

    @Test
    fun `onUrlChanged updates urlInput in uiState`() =
        runTest(testDispatcher) {
            viewModel.onUrlChanged("https://youtube.com/watch?v=abc")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.urlInput == "https://youtube.com/watch?v=abc") {
                "Expected urlInput to be updated"
            }
        }

    @Test
    fun `cancelPendingDownload removes the target download from pendingDownloads`() =
        runTest(testDispatcher) {
            val pending =
                com.nihaltp.sbskip.model.PendingDownload(
                    videoId = "removeme",
                    url = "https://youtube.com/watch?v=removeme",
                    title = "Remove Me",
                    thumbnailUrl = null,
                    createdAtEpochMillis = 0L,
                )
            injectUiState { it.copy(pendingDownloads = listOf(pending)) }

            viewModel.cancelPendingDownload(pending)

            assert(viewModel.uiState.value.pendingDownloads.none { it.videoId == "removeme" }) {
                "Expected pending download to be removed after cancel"
            }
        }

    @Test
    fun `cancelConflictDialog clears showConflictDialog and pendingEnqueueData`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    showConflictDialog = true,
                    pendingEnqueueData =
                        com.nihaltp.sbskip.model.PendingEnqueueData(
                            fileUri = "content://file",
                            title = "Test",
                            youtubeUrl = "https://youtube.com/watch?v=abc",
                            mediaType = MediaType.VIDEO,
                        ),
                )
            }

            viewModel.cancelConflictDialog()

            val state = viewModel.uiState.value
            assert(!state.showConflictDialog) { "showConflictDialog should be false after cancel" }
            assert(state.pendingEnqueueData == null) { "pendingEnqueueData should be null after cancel" }
        }

    @Test
    fun `dismissDurationMismatchDialog clears dialog flag and pendingEnqueueData`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    showDurationMismatchDialog = true,
                    pendingEnqueueData =
                        com.nihaltp.sbskip.model.PendingEnqueueData(
                            fileUri = "content://file",
                            title = "Test",
                            youtubeUrl = "https://youtube.com/watch?v=abc",
                            mediaType = MediaType.VIDEO,
                        ),
                )
            }

            viewModel.dismissDurationMismatchDialog()

            val state = viewModel.uiState.value
            assert(!state.showDurationMismatchDialog) { "showDurationMismatchDialog should be false after dismiss" }
            assert(state.pendingEnqueueData == null) { "pendingEnqueueData should be null after dismiss" }
        }
}
