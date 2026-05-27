package com.nihaltp.sbskip.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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

    val context = LocalContext.current
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var logRefreshKey by remember { mutableStateOf(0) }

    val videoFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                com.nihaltp.sbskip.util.AppLogger.error("Settings", e, "Failed to take persistable URI permission")
            }
            val resolvedPath = resolveRelativePathFromUri(it)
            viewModel.updateVideoFolder(resolvedPath, it.toString())
        }
    }

    val audioFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                com.nihaltp.sbskip.util.AppLogger.error("Settings", e, "Failed to take persistable URI permission")
            }
            val resolvedPath = resolveRelativePathFromUri(it)
            viewModel.updateAudioFolder(resolvedPath, it.toString())
        }
    }

    var activeDialogType by remember { mutableStateOf<SettingsDialogType?>(null) }
    var textInputState by remember { mutableStateOf("") }

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
                horizontalAlignment = Alignment.CenterHorizontally,
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
                            onCheckedChange = viewModel::updateDynamicColor,
                        )
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_notifications_title),
                            description = stringResource(id = R.string.settings_notifications_desc),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = viewModel::updateNotificationsEnabled,
                        )
                        val themeModeLabel = when (settings.themeMode) {
                            ThemeMode.LIGHT -> stringResource(id = R.string.theme_mode_light)
                            ThemeMode.DARK -> stringResource(id = R.string.theme_mode_dark)
                            ThemeMode.SYSTEM -> stringResource(id = R.string.theme_mode_system)
                        }
                        SettingValueRow(
                            title = stringResource(id = R.string.settings_theme_mode),
                            value = themeModeLabel,
                            onClick = { activeDialogType = SettingsDialogType.THEME },
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_cleaner_storage_title)) {
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_overwrite_files_title),
                            description = stringResource(id = R.string.settings_overwrite_files_desc),
                            checked = settings.overwriteBehavior,
                            onCheckedChange = viewModel::updateOverwriteBehavior,
                        )
                        if (!settings.overwriteBehavior) {
                            SettingValueRow(
                                title = stringResource(id = R.string.settings_suffix_title),
                                value = settings.autoCleanSuffix,
                                onClick = {
                                    activeDialogType = SettingsDialogType.SUFFIX
                                    textInputState = settings.autoCleanSuffix
                                },
                            )
                            SettingValueRow(
                                title = stringResource(id = R.string.settings_video_folder),
                                value = settings.videoFolder,
                                onClick = {
                                    videoFolderLauncher.launch(null)
                                },
                            )
                            SettingValueRow(
                                title = stringResource(id = R.string.settings_audio_folder),
                                value = settings.audioFolder,
                                onClick = {
                                    audioFolderLauncher.launch(null)
                                },
                            )
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_sponsorblock_config)) {
                        SettingValueRow(
                            title = stringResource(id = R.string.settings_sb_url_title),
                            value = settings.sponsorBlockUrl,
                            onClick = {
                                activeDialogType = SettingsDialogType.SB_URL
                                textInputState = settings.sponsorBlockUrl
                            },
                        )
                        SettingValueRow(
                            title = stringResource(id = R.string.settings_sb_categories_title),
                            value = stringResource(
                                id = R.string.settings_categories_selected_format,
                                settings.sponsorBlockSettings.categories.size,
                                sponsorBlockCategories.size
                            ),
                            onClick = {
                                activeDialogType = SettingsDialogType.SB_CATEGORIES
                            },
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_advanced)) {
                        SettingToggleRow(
                            title = stringResource(id = R.string.settings_keep_temp_files_title),
                            description = stringResource(id = R.string.settings_keep_temp_files_desc),
                            checked = settings.keepTempFiles,
                            onCheckedChange = viewModel::updateKeepTempFiles,
                        )
                        SettingToggleRow(
                            title = stringResource(id = R.string.verbose_logging),
                            description = stringResource(id = R.string.verbose_logging_desc),
                            checked = settings.verboseLogging,
                            onCheckedChange = viewModel::updateVerboseLogging,
                        )
                        if (settings.verboseLogging) {
                            OutlinedButton(
                                onClick = { showLogsDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.settings_view_logs_button))
                            }
                        }
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.about_section)) {
                        SettingValueRow(stringResource(id = R.string.app_version), "0.2.0")
                        SettingValueRow(
                            title = stringResource(id = R.string.label_github),
                            value = stringResource(id = R.string.app_github),
                            onClick = {
                                val url = "https://" + context.getString(R.string.app_github)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                        )
                        SettingValueRow(
                            title = stringResource(id = R.string.label_issues),
                            value = stringResource(id = R.string.app_issues),
                            onClick = {
                                val url = "https://" + context.getString(R.string.app_issues)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                        )
                        SettingValueRow(
                            title = stringResource(id = R.string.label_licenses),
                            value = stringResource(id = R.string.app_licenses),
                            onClick = {
                                showLicensesDialog = true
                            },
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(id = R.string.settings_section_translation)) {
                        SettingValueRow(
                            title = stringResource(id = R.string.settings_request_translation),
                            value = stringResource(id = R.string.settings_request_translation_desc),
                            onClick = {
                                val url = "https://github.com/nihaltp/SBSkip/issues/new?title=" + Uri.encode("Translation Request") + "&body=" + Uri.encode("I would like to help translate SBSkip into [LANGUAGE].")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                        )
                    }
                }
            }
        }
    }

    // Active Interactive Settings Dialog
    settings?.let { nonNullSettings ->
        activeDialogType?.let { dialogType ->
            when (dialogType) {
                SettingsDialogType.THEME -> {
                    AlertDialog(
                        onDismissRequest = { activeDialogType = null },
                        title = { Text(stringResource(id = R.string.settings_theme_mode)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    val label = when (mode) {
                                        ThemeMode.LIGHT -> stringResource(id = R.string.theme_mode_light)
                                        ThemeMode.DARK -> stringResource(id = R.string.theme_mode_dark)
                                        ThemeMode.SYSTEM -> stringResource(id = R.string.theme_mode_system)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateThemeMode(mode)
                                                activeDialogType = null
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = nonNullSettings.themeMode == mode,
                                            onClick = {
                                                viewModel.updateThemeMode(mode)
                                                activeDialogType = null
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { activeDialogType = null }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        },
                    )
                }
                SettingsDialogType.SB_CATEGORIES -> {
                    AlertDialog(
                        onDismissRequest = { activeDialogType = null },
                        title = { Text(stringResource(id = R.string.settings_sb_categories_dialog_title)) },
                        text = {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(350.dp).fillMaxWidth()
                            ) {
                                val allSelected = nonNullSettings.sponsorBlockSettings.categories.size == sponsorBlockCategories.size
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setAllSponsorBlockCategories(!allSelected) }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(id = R.string.select_all), fontWeight = FontWeight.Bold)
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = { viewModel.setAllSponsorBlockCategories(!allSelected) }
                                        )
                                    }
                                    HorizontalDivider()
                                }

                                sponsorBlockCategories.forEach { categoryRow ->
                                    item {
                                        val isChecked = categoryRow.category in nonNullSettings.sponsorBlockSettings.categories
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.toggleSponsorBlockCategory(categoryRow.category) }
                                                .padding(vertical = 8.dp),
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
                        },
                        confirmButton = {
                            Button(onClick = { activeDialogType = null }) {
                                Text(stringResource(id = R.string.done))
                            }
                        }
                    )
                }
                else -> {
                    val dialogTitle = when (dialogType) {
                        SettingsDialogType.SUFFIX -> stringResource(id = R.string.settings_suffix_title)
                        SettingsDialogType.SB_URL -> stringResource(id = R.string.settings_sb_url_title)
                        else -> ""
                    }
                    val dialogLabel = when (dialogType) {
                        SettingsDialogType.SUFFIX -> stringResource(id = R.string.settings_suffix_field_label)
                        SettingsDialogType.SB_URL -> stringResource(id = R.string.settings_sb_url_field_label)
                        else -> ""
                    }
                    AlertDialog(
                        onDismissRequest = { activeDialogType = null },
                        title = { Text(dialogTitle) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = textInputState,
                                    onValueChange = { textInputState = it },
                                    label = { Text(dialogLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    when (dialogType) {
                                        SettingsDialogType.SUFFIX -> viewModel.updateAutoCleanSuffix(textInputState)
                                        SettingsDialogType.SB_URL -> viewModel.updateSponsorBlockUrl(textInputState)
                                        else -> {}
                                    }
                                    activeDialogType = null
                                },
                            ) {
                                Text(stringResource(id = R.string.save))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialogType = null }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(id = R.string.licenses_title)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(300.dp),
                ) {
                    item {
                        Column {
                            Text("FFmpeg Kit (min-gpl / LTS 16kb)", fontWeight = FontWeight.Bold)
                            Text("License: LGPL 3.0 / GPLv3", style = MaterialTheme.typography.bodySmall)
                            Text("An actively maintained FFmpeg compilation for Android.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column {
                            Text("Jetpack Compose & AndroidX Libraries", fontWeight = FontWeight.Bold)
                            Text("License: Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                            Text("Core UI frameworks, WorkManager orchestration, Room database, and Jetpack DataStore preferences.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column {
                            Text("Kotlin & Kotlinx Serialization / Coroutines", fontWeight = FontWeight.Bold)
                            Text("License: Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                            Text("Modern, reactive language runtime, JSON serializers, and asynchronous flows.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column {
                            Text("Dagger Hilt Dependency Injection", fontWeight = FontWeight.Bold)
                            Text("License: Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                            Text("Google's compiled dependency injection standard for Android.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column {
                            Text("OkHttp & Retrofit", fontWeight = FontWeight.Bold)
                            Text("License: Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                            Text("Square's robust, fast http client engine and type-safe HTTP clients.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column {
                            Text("Coil Image Loader", fontWeight = FontWeight.Bold)
                            Text("License: Apache License 2.0", style = MaterialTheme.typography.bodySmall)
                            Text("Kotlin-first, fast image loading library for Jetpack Compose.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLicensesDialog = false }) {
                    Text(stringResource(id = R.string.close))
                }
            },
        )
    }

    if (showLogsDialog) {
        val clipboardManager = LocalClipboardManager.current
        val logs = remember(logRefreshKey) { viewModel.getLogs() }
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text(stringResource(id = R.string.logs_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(8.dp),
                        ) {
                            item {
                                Text(
                                    text = logs.ifEmpty { stringResource(id = R.string.logs_empty) },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (logs.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(logs))
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = logs.isNotEmpty(),
                ) {
                    Text(stringResource(id = R.string.copy))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.clearLogs()
                            logRefreshKey++
                            Toast.makeText(context, context.getString(R.string.logs_cleared_toast), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(id = R.string.clear))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showLogsDialog = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            },
        )
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
private fun SettingValueRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

private enum class SettingsDialogType {
    THEME,
    SUFFIX,
    SB_URL,
    SB_CATEGORIES,
}

private fun resolveRelativePathFromUri(uri: Uri): String {
    val docId = try {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    } catch (e: Exception) {
        uri.path
    } ?: ""
    val split = docId.split(":")
    val rawPath = if (split.size > 1) split[1] else docId
    val trimmedPath = rawPath.trim('/')
    return if (trimmedPath.isEmpty()) "SB Skip/" else "$trimmedPath/"
}
