package com.nihaltp.sbskip.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.WatchlistFolder
import com.nihaltp.sbskip.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracks the state of the first-run permission onboarding flow.
 *
 * Steps (one screen per step):
 *   0 - Files & Media permission
 *   1 - Notification permission (skipped entirely on SDK < 33)
 *   2 - Watchlist folders (SAF picker)
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        data class State(
            val currentIndex: Int = 0,
            val filesDeniedPermanently: Boolean = false,
            val notificationsDeniedPermanently: Boolean = false,
            val watchlistAdded: Boolean = false,
            val isCompleting: Boolean = false,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        /** Watchlist folders from settings, exposed for display. */
        val watchlistFolders: Flow<List<WatchlistFolder>> =
            settingsRepository.settings.map { it.watchlist }

        /** Total number of steps shown on this device. */
        val stepCount: Int
            get() = if (PermissionHelper.hasNotificationPermissionTarget()) STEP_COUNT else STEP_COUNT - 1

        /** True when onboarding should run on first launch. */
        suspend fun shouldShowOnboarding(): Boolean = settingsRepository.settings.first().onboardingCompleted.not()

        fun completeOnboarding() {
            _state.value = _state.value.copy(isCompleting = true)
            viewModelScope.launch {
                settingsRepository.update { it.copy(onboardingCompleted = true) }
            }
        }

        fun markFilesDeniedPermanently() {
            _state.value = _state.value.copy(filesDeniedPermanently = true)
        }

        fun markNotificationsDeniedPermanently() {
            _state.value = _state.value.copy(notificationsDeniedPermanently = true)
        }

        fun addWatchlistFolder(
            resolvedPath: String,
            uri: Uri,
        ) {
            viewModelScope.launch {
                settingsRepository.update { settings ->
                    if (settings.watchlist.none { it.uri == uri.toString() }) {
                        settings.copy(
                            watchlist = settings.watchlist + WatchlistFolder(resolvedPath, uri.toString()),
                        )
                    } else {
                        settings
                    }
                }
                _state.value = _state.value.copy(watchlistAdded = true)
            }
        }

        fun removeWatchlistFolder(folder: WatchlistFolder) {
            viewModelScope.launch {
                settingsRepository.update { settings ->
                    settings.copy(watchlist = settings.watchlist.filter { it.uri != folder.uri })
                }
            }
        }

        companion object {
            const val STEP_COUNT = 3
        }
    }
