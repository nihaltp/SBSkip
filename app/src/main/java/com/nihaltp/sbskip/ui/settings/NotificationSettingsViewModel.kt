package com.nihaltp.sbskip.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.NotificationChannelConfig
import com.nihaltp.sbskip.model.NotificationChannels
import com.nihaltp.sbskip.model.NotificationIdConfig
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
                val channelPrefs = settings?.notificationChannelPrefs ?: emptyMap()
                val idPrefs = settings?.notificationIdPrefs ?: emptyMap()
                val permissionGranted = PermissionHelper.hasNotificationPermission(context)
                val permissionTarget = PermissionHelper.hasNotificationPermissionTarget()
                // canToggle: true if no runtime permission needed (pre-13) OR permission granted (13+)
                val canToggle = !permissionTarget || permissionGranted
                NotificationChannels.ALL.map { config ->
                    val channelEnabled = channelPrefs[config.id] ?: true
                    val notificationIds =
                        config.notificationIds.map { notifId ->
                            NotificationIdUiState(
                                config = notifId,
                                isEnabled = (channelEnabled && (idPrefs[notifId.id] ?: true)),
                                canToggle = canToggle && channelEnabled,
                            )
                        }
                    ChannelUiState(
                        config = config,
                        isEnabled = channelEnabled,
                        isExpanded = expanded.contains(config.id),
                        canToggle = canToggle,
                        notificationIds = notificationIds,
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
                    val newChannelPrefs =
                        if (enabled) {
                            current.notificationChannelPrefs - channelId
                        } else {
                            current.notificationChannelPrefs + (channelId to false)
                        }
                    val newIdPrefs =
                        if (!enabled) {
                            // When channel is disabled, disable all its notification IDs
                            val channel = NotificationChannels.getById(channelId)
                            channel?.notificationIds?.fold(current.notificationIdPrefs) { prefs, notifId ->
                                prefs + (notifId.id to false)
                            } ?: current.notificationIdPrefs
                        } else {
                            current.notificationIdPrefs
                        }
                    current.copy(
                        notificationChannelPrefs = newChannelPrefs,
                        notificationIdPrefs = newIdPrefs,
                    )
                }
            }
        }

        fun toggleNotificationId(
            notificationId: Int,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                settingsRepository.update { current ->
                    val newPrefs =
                        if (enabled) {
                            current.notificationIdPrefs - notificationId
                        } else {
                            current.notificationIdPrefs + (notificationId to false)
                        }
                    current.copy(notificationIdPrefs = newPrefs)
                }
            }
        }

        data class ChannelUiState(
            val config: NotificationChannelConfig,
            val isEnabled: Boolean,
            val isExpanded: Boolean,
            val canToggle: Boolean,
            val notificationIds: List<NotificationIdUiState>,
        )

        data class NotificationIdUiState(
            val config: NotificationIdConfig,
            val isEnabled: Boolean,
            val canToggle: Boolean,
        )
    }
