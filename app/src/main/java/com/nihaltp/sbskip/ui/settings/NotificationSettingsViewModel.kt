package com.nihaltp.sbskip.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.NotificationChannelConfig
import com.nihaltp.sbskip.model.NotificationChannels
import com.nihaltp.sbskip.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _expandedChannels = MutableStateFlow<Set<String>>(emptySet())
        val expandedChannels = _expandedChannels

        val channels =
            combine(settingsRepository.settings, _expandedChannels) { settings, expanded ->
                val prefs = settings?.notificationChannelPrefs ?: emptyMap()
                val permissionGranted = PermissionHelper.hasNotificationPermission(context)
                val permissionTarget = PermissionHelper.hasNotificationPermissionTarget()
                // canToggle: true if no runtime permission needed (pre-13) OR permission granted (13+)
                val canToggle = !permissionTarget || permissionGranted
                NotificationChannels.ALL.map { config ->
                    ChannelUiState(
                        config = config,
                        isEnabled = prefs[config.id] ?: true,
                        isExpanded = expanded.contains(config.id),
                        canToggle = canToggle,
                    )
                }
            }
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

        fun toggleExpand(channelId: String) {
            viewModelScope.launch {
                _expandedChannels.update { expanded ->
                    if (expanded.contains(channelId)) expanded - channelId else expanded + channelId
                }
            }
        }

        fun toggleChannel(
            channelId: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                settingsRepository.update { current ->
                    val newPrefs =
                        if (enabled) {
                            current.notificationChannelPrefs - channelId
                        } else {
                            current.notificationChannelPrefs + (channelId to false)
                        }
                    current.copy(notificationChannelPrefs = newPrefs)
                }
            }
        }

        data class ChannelUiState(
            val config: NotificationChannelConfig,
            val isEnabled: Boolean,
            val isExpanded: Boolean,
            val canToggle: Boolean,
        )
    }
