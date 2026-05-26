package com.nihaltp.sbskip.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(themeMode = themeMode) }
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(dynamicColor = enabled) }
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun updateKeepTempFiles(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(keepTempFiles = enabled) }
        }
    }

    fun updateVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(verboseLogging = enabled) }
        }
    }

    fun updateSponsorBlockUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(sponsorBlockUrl = url) }
        }
    }

    fun updateOverwriteBehavior(overwrite: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(overwriteBehavior = overwrite) }
        }
    }

    fun updateAutoCleanSuffix(suffix: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(autoCleanSuffix = suffix) }
        }
    }

    fun updateVideoFolder(folder: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(videoFolder = folder) }
        }
    }

    fun updateAudioFolder(folder: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(audioFolder = folder) }
        }
    }

    fun toggleSponsorBlockCategory(category: SponsorBlockCategory) {
        viewModelScope.launch {
            settingsRepository.update { current ->
                val currentCategories = current.sponsorBlockSettings.categories
                val updatedCategories = if (category in currentCategories) {
                    currentCategories - category
                } else {
                    currentCategories + category
                }
                current.copy(
                    sponsorBlockSettings = current.sponsorBlockSettings.copy(
                        categories = updatedCategories
                    )
                )
            }
        }
    }
}
