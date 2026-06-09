package com.nihaltp.sbskip.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.AudioSaveMode
import com.nihaltp.sbskip.model.DownloaderType
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings?> =
            settingsRepository.settings
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        fun updateThemeMode(themeMode: ThemeMode) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(themeMode = themeMode) }
            }
        }

        fun updateAudioSaveMode(audioSaveMode: AudioSaveMode) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(audioSaveMode = audioSaveMode) }
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

        fun updateDownloaderType(downloaderType: DownloaderType) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(downloader = downloaderType) }
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

        fun updateSponsorBlockStatusUrl(url: String) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(sponsorBlockStatusUrl = url) }
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

        fun updateDefaultConvertVideoToAudio(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(defaultConvertVideoToAudio = enabled) }
            }
        }

        fun updateDefaultDeleteOriginalVideo(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(defaultDeleteOriginalVideo = enabled) }
            }
        }

        fun updateVideoFolder(
            folder: String,
            uriString: String,
        ) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(videoFolder = folder, videoFolderUri = uriString) }
            }
        }

        fun updateAudioFolder(
            folder: String,
            uriString: String,
        ) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(audioFolder = folder, audioFolderUri = uriString) }
            }
        }

        fun updateNewPipeVideoFolder(
            folder: String,
            uriString: String,
        ) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(newPipeVideoFolder = folder, newPipeVideoFolderUri = uriString) }
            }
        }

        fun updateNewPipeAudioFolder(
            folder: String,
            uriString: String,
        ) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(newPipeAudioFolder = folder, newPipeAudioFolderUri = uriString) }
            }
        }

        fun toggleSponsorBlockCategory(category: SponsorBlockCategory) {
            viewModelScope.launch {
                settingsRepository.update { current ->
                    val currentCategories = current.sponsorBlockSettings.categories
                    val updatedCategories =
                        if (category in currentCategories) {
                            currentCategories - category
                        } else {
                            currentCategories + category
                        }
                    current.copy(
                        sponsorBlockSettings =
                            current.sponsorBlockSettings.copy(
                                categories = updatedCategories,
                            ),
                    )
                }
            }
        }

        fun setAllSponsorBlockCategories(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.update { current ->
                    val updatedCategories =
                        if (enabled) {
                            SponsorBlockCategory.entries.toSet()
                        } else {
                            emptySet()
                        }
                    current.copy(
                        sponsorBlockSettings =
                            current.sponsorBlockSettings.copy(
                                categories = updatedCategories,
                            ),
                    )
                }
            }
        }

        fun updateBypassSmallDurationDifference(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(bypassSmallDurationDifference = enabled) }
            }
        }

        fun updateMaxDurationDifferenceSeconds(seconds: Int) {
            viewModelScope.launch {
                settingsRepository.update { it.copy(maxDurationDifferenceSeconds = seconds) }
            }
        }

        fun getLogs(): String {
            return com.nihaltp.sbskip.util.AppLogger.getLogs()
        }

        fun clearLogs() {
            com.nihaltp.sbskip.util.AppLogger.clearLogs()
        }
    }
