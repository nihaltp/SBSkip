package com.nihaltp.sbskip.ui.settings

import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.DownloaderType
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
class SettingsViewModelYtDlpTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        downloadedVideoRepository = mockk(relaxed = true)

        coEvery { settingsRepository.settings } returns
            flowOf(
                AppSettings(
                    downloader = DownloaderType.YTDLP,
                    nativeQuality = "1080",
                    nativeAutoUpdate = false,
                ),
            )

        viewModel = SettingsViewModel(settingsRepository, downloadedVideoRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateYtDlpQuality_updatesSettings() =
        runTest(testDispatcher) {
            viewModel.updateYtDlpQuality("720")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { settingsRepository.update(any()) }
        }

    @Test
    fun updateYtDlpQuality_updatesToBest() =
        runTest(testDispatcher) {
            viewModel.updateYtDlpQuality("BEST")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { settingsRepository.update(any()) }
        }

    @Test
    fun updateYtDlpAutoUpdate_enablesAutoUpdate() =
        runTest(testDispatcher) {
            viewModel.updateYtDlpAutoUpdate(true)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { settingsRepository.update(any()) }
        }

    @Test
    fun updateYtDlpAutoUpdate_disablesAutoUpdate() =
        runTest(testDispatcher) {
            viewModel.updateYtDlpAutoUpdate(false)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { settingsRepository.update(any()) }
        }
}
