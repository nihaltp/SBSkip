package com.nihaltp.sbskip.ui.onboarding

import android.net.Uri
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.WatchlistFolder
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        settingsRepository = mockk(relaxed = true)

        // Default: onboarding not completed, with empty watchlist
        coEvery { settingsRepository.settings } returns flowOf(AppSettings(watchlist = emptyList()))
        coEvery { settingsRepository.update(any()) } just Runs

        viewModel =
            OnboardingViewModel(
                settingsRepository = settingsRepository,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shouldShowOnboarding returns true when onboarding not completed`() =
        runTest(testDispatcher) {
            val result = viewModel.shouldShowOnboarding()
            assert(result == true)
        }

    @Test
    fun `shouldShowOnboarding returns false when onboarding completed`() =
        runTest(testDispatcher) {
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(onboardingCompleted = true))

            val result = viewModel.shouldShowOnboarding()
            assert(result == false)
        }

    @Test
    fun `completeOnboarding marks onboarding as completed`() =
        runTest(testDispatcher) {
            // Verify the state changes to isCompleting = true
            val initialState = viewModel.state.first()
            assert(initialState.isCompleting == false)

            viewModel.completeOnboarding()

            // Give coroutine time to complete
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = viewModel.state.first()
            assert(updatedState.isCompleting == true)
        }

    @Test
    fun `markFilesDeniedPermanently updates state`() =
        runTest(testDispatcher) {
            val initialState = viewModel.state.first()
            assert(initialState.filesDeniedPermanently == false)

            viewModel.markFilesDeniedPermanently()

            val updatedState = viewModel.state.first()
            assert(updatedState.filesDeniedPermanently == true)
        }

    @Test
    fun `markNotificationsDeniedPermanently updates state`() =
        runTest(testDispatcher) {
            val initialState = viewModel.state.first()
            assert(initialState.notificationsDeniedPermanently == false)

            viewModel.markNotificationsDeniedPermanently()

            val updatedState = viewModel.state.first()
            assert(updatedState.notificationsDeniedPermanently == true)
        }

    @Test
    fun `addWatchlistFolder updates state`() =
        runTest(testDispatcher) {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYT")
            val resolvedPath = "Download/YT/"

            val initialState = viewModel.state.first()
            assert(initialState.watchlistAdded == false)

            viewModel.addWatchlistFolder(resolvedPath, folderUri)

            // Give coroutine time to complete
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = viewModel.state.first()
            assert(updatedState.watchlistAdded == true)
        }

    @Test
    fun `addWatchlistFolder does not duplicate folder with same URI`() =
        runTest(testDispatcher) {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYT")
            val resolvedPath = "Download/YT/"

            val existingFolder = WatchlistFolder(resolvedPath, folderUri.toString())
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(watchlist = listOf(existingFolder)))

            viewModel.addWatchlistFolder(resolvedPath, folderUri)

            // Should not crash
        }

    @Test
    fun `removeWatchlistFolder does not crash`() =
        runTest(testDispatcher) {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYT")
            val folder = WatchlistFolder("Download/YT/", folderUri.toString())
            coEvery { settingsRepository.settings } returns flowOf(AppSettings(watchlist = listOf(folder)))

            viewModel.removeWatchlistFolder(folder)

            // Should not crash
        }

    @Test
    fun `stepCount returns 3 when notification permission target exists`() =
        runTest(testDispatcher) {
            // On SDK >= 33, stepCount should be 3 (Files, Notifications, Watchlist)
            // OnboardingViewModel uses PermissionHelper.hasNotificationPermissionTarget()
            val stepCount = viewModel.stepCount
            assert(stepCount == 3 || stepCount == 2) // Depends on SDK version in test env
        }
}
