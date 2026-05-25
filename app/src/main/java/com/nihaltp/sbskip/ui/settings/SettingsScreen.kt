package com.nihaltp.sbskip.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var verboseLogging by remember { mutableStateOf(false) }
    var keepTempFiles by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.settings_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = stringResource(id = R.string.settings_section_general)) {
                    SettingToggleRow(stringResource(id = R.string.settings_dynamic_color_title), stringResource(id = R.string.settings_dynamic_color_desc), dynamicColor) { dynamicColor = it }
                    SettingToggleRow(stringResource(id = R.string.settings_notifications_title), stringResource(id = R.string.settings_notifications_desc), notificationsEnabled) { notificationsEnabled = it }
                    SettingValueRow(stringResource(id = R.string.settings_theme_mode), ThemeMode.SYSTEM.name)
                }
            }

            item {
                SettingsSection(title = stringResource(id = R.string.settings_section_downloads)) {
                    SettingValueRow(stringResource(id = R.string.settings_video_folder), stringResource(id = R.string.default_video_folder))
                    SettingValueRow(stringResource(id = R.string.settings_audio_folder), stringResource(id = R.string.default_audio_folder))
                    SettingValueRow(stringResource(id = R.string.settings_temp_folder), stringResource(id = R.string.default_temp_folder))
                }
            }

            item {
                SettingsSection(title = stringResource(id = R.string.settings_section_sponsorblock)) {
                    SettingValueRow(stringResource(id = R.string.settings_global_categories), stringResource(id = R.string.settings_global_categories_value))
                    SettingValueRow(stringResource(id = R.string.settings_per_download_overrides), stringResource(id = R.string.settings_per_download_overrides_value))
                }
            }

            item {
                SettingsSection(title = stringResource(id = R.string.settings_section_advanced)) {
                    SettingToggleRow(stringResource(id = R.string.settings_keep_temp_files_title), stringResource(id = R.string.settings_keep_temp_files_desc), keepTempFiles) { keepTempFiles = it }
                    SettingToggleRow(stringResource(id = R.string.verbose_logging), stringResource(id = R.string.verbose_logging_desc), verboseLogging) { verboseLogging = it }
                    SettingValueRow(stringResource(id = R.string.settings_concurrent_limit), stringResource(id = R.string.concurrent_limit_value))
                }
            }

            item {
                SettingsSection(title = stringResource(id = R.string.about_section)) {
                    SettingValueRow(stringResource(id = R.string.app_version), stringResource(id = R.string.app_version_value))
                    SettingValueRow(stringResource(id = R.string.label_github), stringResource(id = R.string.app_github))
                    SettingValueRow(stringResource(id = R.string.label_issues), stringResource(id = R.string.app_issues))
                    SettingValueRow(stringResource(id = R.string.label_licenses), stringResource(id = R.string.app_licenses))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingValueRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value)
        }
        Spacer(modifier = Modifier.height(1.dp))
    }
}
