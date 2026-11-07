package com.nihaltp.sbskip.ui.main

import android.content.Context
import android.content.pm.PackageManager
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.DownloadSource
import com.nihaltp.sbskip.model.DownloaderType
import com.nihaltp.sbskip.model.QueueActionResult
import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.fetcher.YouTubeOEmbedFetcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
class MainViewModelYtDlpRoutingTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var queueRepository: QueueRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var downloadStorage: DownloadStorage

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(YouTubeOEmbedFetcher)
        coEvery { YouTubeOEmbedFetcher.fetch(any()) } returns
            YouTubeMetadata(
                title = "Test Title",
                authorName = null,
                authorUrl = null,
                thumbnailUrl = null,
            )

        val packageManager = mockk<PackageManager>(relaxed = true)
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()

        context = mockk(relaxed = true)
        every { context.getString(any<Int>()) } returns ""
        every { context.packageManager } returns packageManager

        queueRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        downloadedVideoRepository = mockk(relaxed = true)
        downloadStorage = mockk(relaxed = true)

        coEvery { settingsRepository.settings } returns flowOf(AppSettings(downloader = DownloaderType.YTDLP))
        coEvery { queueRepository.observeQueue() } returns flowOf(emptyList())
        coEvery { downloadedVideoRepository.findByVideoId(any()) } returns null
        coEvery {
            queueRepository.enqueue(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns QueueActionResult(true, "ok")

        viewModel =
            MainViewModel(
                queueRepository = queueRepository,
                settingsRepository = settingsRepository,
                downloadedVideoRepository = downloadedVideoRepository,
                downloadStorage = downloadStorage,
                context = context,
            )
    }

    @After
    fun tearDown() {
        unmockkObject(YouTubeOEmbedFetcher)
        Dispatchers.resetMain()
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectUiState(transform: (com.nihaltp.sbskip.model.MainUiState) -> com.nihaltp.sbskip.model.MainUiState) {
        val field = MainViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.nihaltp.sbskip.model.MainUiState>
        stateFlow.value = transform(stateFlow.value)
    }

    @Test
    fun `startDownloadAndClean uses yt-dlp enqueue when downloader is YTDLP`() =
        runTest(testDispatcher) {
            injectUiState {
                it.copy(
                    isNewPipeInstalled = false,
                    urlInput = "https://www.youtube.com/watch?v=aaaaaaaaaaa",
                )
            }

            viewModel.startDownloadAndClean()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                queueRepository.enqueue(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    DownloadSource.YTDLP,
                )
            }
        }
}
