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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                title = { Text("Settings") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
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
                SettingsSection(title = "General") {
                    SettingToggleRow("Dynamic color", "Use system colors when available.", dynamicColor) { dynamicColor = it }
                    SettingToggleRow("Notifications", "Show foreground and completion updates.", notificationsEnabled) { notificationsEnabled = it }
                    SettingValueRow("Theme mode", ThemeMode.SYSTEM.name)
                }
            }

            item {
                SettingsSection(title = "Downloads") {
                    SettingValueRow("Video folder", "Movies/SB Skip/")
                    SettingValueRow("Audio folder", "Music/SB Skip/")
                    SettingValueRow("Temp folder", "App cache / SAF target")
                }
            }

            item {
                SettingsSection(title = "SponsorBlock") {
                    SettingValueRow("Global categories", "Sponsor, Intro, Outro, and more")
                    SettingValueRow("Per-download overrides", "Enabled")
                }
            }

            item {
                SettingsSection(title = "Advanced") {
                    SettingToggleRow("Keep temp files", "Useful while debugging FFmpeg and yt-dlp processing.", keepTempFiles) { keepTempFiles = it }
                    SettingToggleRow("Verbose logging", "Capture detailed queue and processor logs.", verboseLogging) { verboseLogging = it }
                    SettingValueRow("Concurrent queue limit", "1 (sequential)")
                }
            }

            item {
                SettingsSection(title = "About") {
                    SettingValueRow("App version", "0.1.0")
                    SettingValueRow("GitHub", "github.com/nihaltp/SBSkip")
                    SettingValueRow("Issues", "github.com/nihaltp/SBSkip/issues")
                    SettingValueRow("Licenses", "Open source components")
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
