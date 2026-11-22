package com.nihaltp.sbskip.ui.settings

import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.util.AppLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelLogsTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var downloadedVideoRepository: DownloadedVideoRepository
    private lateinit var viewModel: SettingsViewModel
    private lateinit var logFile: java.io.File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        downloadedVideoRepository = mockk(relaxed = true)

        coEvery { settingsRepository.settings } returns
            flowOf(
                AppSettings(),
            )

        viewModel = SettingsViewModel(settingsRepository, downloadedVideoRepository)

        // Set up AppLogger for testing
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "sbskip_test_logs")
        tempDir.mkdirs()
        logFile = java.io.File(tempDir, "sbskip.log")
        AppLogger.initForTesting(logFile)
        AppLogger.isVerboseLoggingEnabled = true
        AppLogger.clearLogs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppLogger.clearLogs()
    }

    @Test
    fun getLogs_returnsEmptyStringWhenNoLogs() =
        runTest(testDispatcher) {
            val logs = viewModel.getLogs()
            assertEquals("", logs)
        }

    @Test
    fun getLogs_returnsLogsFromAppLogger() =
        runTest(testDispatcher) {
            AppLogger.queue("Test queue log")
            AppLogger.worker("Test worker log")

            val logs = viewModel.getLogs()

            assertTrue(logs.contains("Test queue log"))
            assertTrue(logs.contains("Test worker log"))
        }

    @Test
    fun clearLogs_clearsAppLogger() =
        runTest(testDispatcher) {
            AppLogger.queue("Test log to clear")
            assertTrue(viewModel.getLogs().contains("Test log to clear"))

            viewModel.clearLogs()

            assertEquals("", viewModel.getLogs())
        }

    @Test
    fun clearLogs_callsAppLoggerClearLogs() =
        runTest(testDispatcher) {
            // Just verify that clearLogs doesn't crash and clears the logs
            viewModel.clearLogs()
            assertEquals("", viewModel.getLogs())
        }
}
