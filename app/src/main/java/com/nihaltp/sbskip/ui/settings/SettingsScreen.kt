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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsState by viewModel.settings.collectAsState()
    val settings = settingsState

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
        if (settings == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_general)) {
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_dynamic_color_title),
                            description = stringResource(id = R.string.settings_dynamic_color_desc),
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::updateDynamicColor
                        )
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_notifications_title),
                            description = stringResource(id = R.string.settings_notifications_desc),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = viewModel::updateNotificationsEnabled
                        )
                        SettingValueRow(stringResource(id = R.string.settings_theme_mode), settings.themeMode.name)
                    }
                }

                item {
                    SettingsSection(title = "Cleaner Storage Settings") {
                        SettingToggleRow(
                            title = "Overwrite Cleaned Files",
                            description = "Overwrites files with the same name. If disabled, a unique suffix will be added.",
                            checked = settings.overwriteBehavior,
                            onCheckedChange = viewModel::updateOverwriteBehavior
                        )
                        SettingValueRow("Cleaned File Suffix", settings.autoCleanSuffix)
                        SettingValueRow(stringResource(id = R.string.settings_video_folder), settings.videoFolder)
                        SettingValueRow(stringResource(id = R.string.settings_audio_folder), settings.audioFolder)
                    }
                }

                item {
                    SettingsSection(title = "SponsorBlock Configuration") {
                        SettingValueRow("SponsorBlock Server API URL", settings.sponsorBlockUrl)
                        HorizontalDivider()
                        Text("Active Skip Categories", fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))

                        sponsorBlockCategories.forEach { categoryRow ->
                            val isChecked = categoryRow.category in settings.sponsorBlockSettings.categories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(id = categoryRow.labelResId))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.toggleSponsorBlockCategory(categoryRow.category) }
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_advanced)) {
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_keep_temp_files_title),
                            description = stringResource(id = R.string.settings_keep_temp_files_desc),
                            checked = settings.keepTempFiles,
                            onCheckedChange = viewModel::updateKeepTempFiles
                        )
                        SettingToggleRow(
                            title = stringResource(id = R.string.verbose_logging),
                            description = stringResource(id = R.string.verbose_logging_desc),
                            checked = settings.verboseLogging,
                            onCheckedChange = viewModel::updateVerboseLogging
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.about_section)) {
                        SettingValueRow(stringResource(id = R.string.app_version), "0.2.0")
                        SettingValueRow(stringResource(id = R.string.label_github), stringResource(id = R.string.app_github))
                        SettingValueRow(stringResource(id = R.string.label_issues), stringResource(id = R.string.app_issues))
                        SettingValueRow(stringResource(id = R.string.label_licenses), stringResource(id = R.string.app_licenses))
                    }
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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

private data class CategoryRow(val category: SponsorBlockCategory, val labelResId: Int)

private val sponsorBlockCategories = listOf(
    CategoryRow(SponsorBlockCategory.SPONSOR, R.string.category_sponsor),
    CategoryRow(SponsorBlockCategory.SELF_PROMOTION, R.string.category_self_promotion),
    CategoryRow(SponsorBlockCategory.INTERACTION_REMINDER, R.string.category_interaction_reminder),
    CategoryRow(SponsorBlockCategory.INTRO, R.string.category_intro),
    CategoryRow(SponsorBlockCategory.OUTRO, R.string.category_outro),
    CategoryRow(SponsorBlockCategory.PREVIEW_RECAP, R.string.category_preview_recap),
    CategoryRow(SponsorBlockCategory.HOOK, R.string.category_hook_greetings),
    CategoryRow(SponsorBlockCategory.FILLER_TANGENT, R.string.category_filler_tangent),
    CategoryRow(SponsorBlockCategory.MUSIC_OFFTOPIC, R.string.category_music_non_music_section),
)
