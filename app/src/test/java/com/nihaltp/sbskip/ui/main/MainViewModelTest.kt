package com.nihaltp.sbskip.ui.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

        context = ApplicationProvider.getApplicationContext()
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
}
