package com.nihaltp.sbskip.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DetectedFile
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.model.PendingDownload
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.ui.components.SponsorBlockCategoryPickerDialog
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onFileSelected: (Uri) -> Unit,
    onClearSelectedFile: () -> Unit,
    onAudioFolderPicked: (Uri?) -> Unit,
    onSubmit: () -> Unit,
    onAutoDetectPending: (PendingDownload) -> Unit,
    onCancelPending: (PendingDownload) -> Unit,
    onConfirmPending: (PendingDownload) -> Unit,
    onStartManualPickForPending: (PendingDownload) -> Unit,
    onConvertVideoToAudioChange: (Boolean) -> Unit,
    onDeleteOriginalVideoChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onRetryQueueItem: (Long, Boolean) -> Unit,
    onSnackbarShown: () -> Unit,
    onProceedAnyway: () -> Unit,
    onCancelMismatchDialog: () -> Unit,
    onFindFile: () -> Unit,
    onCancelConflictDialog: () -> Unit,
    onReplaceConflict: () -> Unit,
    onRenameConflict: () -> Unit,
    onDismissWatchlistPrompt: () -> Unit,
    onCustomCategoriesChanged: (Set<SponsorBlockCategory>?) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var filesPermissionGranted by remember {
        mutableStateOf(PermissionHelper.hasFilesPermission(context))
    }
    val filesPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            filesPermissionGranted = PermissionHelper.hasFilesPermission(context)
            AppLogger.metadata("Permissions: Files permission result from MainScreen: $results, hasPermission=$filesPermissionGranted")
        }
    val allPermissionsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            filesPermissionGranted = PermissionHelper.hasFilesPermission(context)
            AppLogger.metadata(
                "Permissions: All permissions result from MainScreen startup: $results, hasFilesPermission=$filesPermissionGranted",
            )
        }
    val requestFilesPermissionWithRationale = {
        AppLogger.metadata("Permissions: Showing files permission rationale Toast and launching picker request...")
        Toast.makeText(context, context.getString(R.string.permission_files_rationale), Toast.LENGTH_LONG).show()
        filesPermissionLauncher.launch(PermissionHelper.getRequiredFilesPermissions())
    }
    val coroutineScope = rememberCoroutineScope()
    val clipboardEmptyText = stringResource(id = R.string.clipboard_empty)

    var errorDialogItem by remember { mutableStateOf<DownloadQueueItem?>(null) }
    var detailsDialogItem by remember { mutableStateOf<DownloadQueueItem?>(null) }
    var detailsPendingDownloadItem by remember { mutableStateOf<PendingDownload?>(null) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    val showLocalCleanButton = uiState.selectedFileUri != null && uiState.urlInput.isNotBlank()
    val showDownloadAndCleanButton = uiState.selectedFileUri == null && uiState.isNewPipeInstalled && uiState.urlInput.isNotBlank()
    val isVideoFile = uiState.selectedFileMediaType == com.nihaltp.sbskip.model.MediaType.VIDEO
    val showConvertOnlyButton = uiState.selectedFileUri != null && uiState.urlInput.isBlank() && isVideoFile

    // Document picker for MP4, M4A, and MP3
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let(onFileSelected)
            },
        )

    val runtimeAudioFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    com.nihaltp.sbskip.util.AppLogger.error("MainScreen", e, "Failed to take persistable URI permission")
                }
            }
            onAudioFolderPicked(uri)
        }

    LaunchedEffect(uiState.pendingAudioFolderPick) {
        if (uiState.pendingAudioFolderPick != null) {
            runtimeAudioFolderLauncher.launch(null)
        }
    }

    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasAllRequiredPermissions(context)) {
            AppLogger.metadata("Permissions: App launched. Requesting files and notifications permissions...")
            allPermissionsLauncher.launch(PermissionHelper.getAllRequiredPermissions())
        } else {
            AppLogger.metadata("Permissions: App launched. All required permissions are already granted.")
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_main)) },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.settings_title),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(id = R.string.paste_youtube_link_prompt), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(id = R.string.paste_youtube_prompt)) },
                            singleLine = true,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ElevatedButton(onClick = {
                                val pasted = clipboardManager.getText()?.text.orEmpty().trim()
                                if (pasted.isBlank()) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(clipboardEmptyText) }
                                } else {
                                    onUrlChange(pasted)
                                }
                            }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.paste_button))
                            }

                            if (uiState.urlInput.isNotBlank()) {
                                val isCustom = uiState.customSponsorBlockCategories != null
                                val count = uiState.customSponsorBlockCategories?.size ?: uiState.globalSponsorBlockCategories.size
                                val labelText =
                                    if (isCustom) {
                                        stringResource(id = R.string.categories_btn_custom, count)
                                    } else {
                                        stringResource(id = R.string.categories_btn_global, count)
                                    }

                                ElevatedButton(
                                    onClick = { showCategoriesDialog = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(labelText)
                                }
                            }
                        }

                        if (showLocalCleanButton || showDownloadAndCleanButton || showConvertOnlyButton) {
                            val isLoading = uiState.isFetchingMetadata || uiState.isVerifyingDuration
                            Button(
                                onClick = onSubmit,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text =
                                        if (uiState.isVerifyingDuration) {
                                            stringResource(id = R.string.dialog_verifying_duration)
                                        } else if (showDownloadAndCleanButton) {
                                            stringResource(id = R.string.download_and_clean_button)
                                        } else if (showConvertOnlyButton) {
                                            stringResource(id = R.string.convert_to_audio_button)
                                        } else {
                                            stringResource(id = R.string.clean_media_button)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(id = R.string.select_local_file_title), fontWeight = FontWeight.SemiBold)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text =
                                            if (uiState.selectedFileUri != null) {
                                                stringResource(
                                                    id = R.string.selected_file_label,
                                                )
                                            } else {
                                                stringResource(id = R.string.import_media_file_label)
                                            },
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray,
                                    )
                                    Text(
                                        text = uiState.selectedFileName.ifBlank { stringResource(id = R.string.no_media_selected) },
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (uiState.selectedFileUri == null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ElevatedButton(
                                            onClick = {
                                                if (PermissionHelper.hasFilesPermission(context)) {
                                                    filePickerLauncher.launch(
                                                        arrayOf("video/mp4", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4"),
                                                    )
                                                } else {
                                                    requestFilesPermissionWithRationale()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(id = R.string.pick_file_button))
                                        }

                                        if (uiState.urlInput.isNotBlank()) {
                                            val isLoading = uiState.isFetchingMetadata || uiState.isDetectingFile
                                            OutlinedButton(
                                                onClick = {
                                                    if (PermissionHelper.hasFilesPermission(context)) {
                                                        onFindFile()
                                                    } else {
                                                        requestFilesPermissionWithRationale()
                                                    }
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                if (isLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.height(18.dp).width(18.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else {
                                                    Icon(Icons.Filled.Search, contentDescription = null)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(id = R.string.find_file_button))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMediaType == com.nihaltp.sbskip.model.MediaType.VIDEO) {
                            val isUrlEmpty = uiState.urlInput.isBlank()
                            val isConvertChecked = isUrlEmpty || uiState.convertVideoToAudio
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider()

                                if (isUrlEmpty) {
                                    Text(
                                        text = stringResource(id = R.string.convert_to_audio_only_notice),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(id = R.string.convert_video_to_audio_title),
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = stringResource(id = R.string.convert_video_to_audio_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                            )
                                        }
                                        Switch(
                                            checked = uiState.convertVideoToAudio,
                                            onCheckedChange = onConvertVideoToAudioChange,
                                        )
                                    }
                                }

                                if (isConvertChecked) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(id = R.string.delete_original_video_title),
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = stringResource(id = R.string.delete_original_video_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                            )
                                        }
                                        Switch(
                                            checked = uiState.deleteOriginalVideo,
                                            onCheckedChange = onDeleteOriginalVideoChange,
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFileUri != null) {
                            TextButton(onClick = onClearSelectedFile) {
                                Text(stringResource(id = R.string.remove_picked_file_button))
                            }
                        }
                    }
                }
            }

            if (uiState.pendingDownloads.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(id = R.string.pending_downloads_title))
                }
                items(uiState.pendingDownloads, key = { it.videoId }) { pending ->
                    PendingDownloadCard(
                        pendingDownloadTitle = pending.title,
                        thumbnailUrl = pending.thumbnailUrl,
                        detectedFileName = pending.detectedFileName,
                        detectedFile = pending.detectedFile,
                        isDetecting = pending.isDetectingFile,
                        onAutoDetect = {
                            if (PermissionHelper.hasFilesPermission(context)) {
                                onAutoDetectPending(pending)
                            } else {
                                requestFilesPermissionWithRationale()
                            }
                        },
                        onPickFileManually = {
                            if (PermissionHelper.hasFilesPermission(context)) {
                                onStartManualPickForPending(pending)
                                filePickerLauncher.launch(arrayOf("video/mp4", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4"))
                            } else {
                                requestFilesPermissionWithRationale()
                            }
                        },
                        onCancel = { onCancelPending(pending) },
                        onConfirmDetectedFile = { onConfirmPending(pending) },
                        onCardClick = { detailsPendingDownloadItem = pending },
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.media_queue_title))
            }

            if (uiState.queueItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(id = R.string.no_jobs_title),
                        body = stringResource(id = R.string.no_jobs_body),
                    )
                }
            } else {
                items(uiState.queueItems, key = { it.id }) { queueItem ->
                    QueueItemCard(
                        item = queueItem,
                        onRetry = { id -> onRetryQueueItem(id, false) },
                        onRemove = onRemoveQueueItem,
                        onErrorClick = { errorDialogItem = it },
                        onCardClick = { detailsDialogItem = it },
                    )
                }
            }
        }
    }

    // Full error details popup with formatted GitHub bug report integration
    errorDialogItem?.let { item ->
        val errorMessage = item.errorMessage.orEmpty()
        val isDurationMismatch = errorMessage.startsWith("Picked file duration")

        if (isDurationMismatch) {
            val regex = """Picked file duration \((\d+)\s*s\) does not match YouTube video duration \((\d+)\s*s\)""".toRegex()
            val matchResult = regex.find(errorMessage)
            val fileDurationStr =
                matchResult?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds ->
                    val minutes = seconds / 60
                    val remaining = seconds % 60
                    "%d:%02d".format(minutes, remaining)
                } ?: "--:--"
            val youtubeDurationStr =
                matchResult?.groupValues?.getOrNull(2)?.toLongOrNull()?.let { seconds ->
                    val minutes = seconds / 60
                    val remaining = seconds % 60
                    "%d:%02d".format(minutes, remaining)
                } ?: "--:--"

            AlertDialog(
                onDismissRequest = { errorDialogItem = null },
                title = { Text(stringResource(id = R.string.dialog_duration_mismatch_title)) },
                text = {
                    Text(stringResource(id = R.string.dialog_duration_mismatch_message, fileDurationStr, youtubeDurationStr))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRetryQueueItem(item.id, true)
                            errorDialogItem = null
                        },
                    ) {
                        Text(stringResource(id = R.string.dialog_duration_mismatch_proceed))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { errorDialogItem = null }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { errorDialogItem = null },
                title = { Text(stringResource(id = R.string.error_details_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = R.string.error_details_intro), fontWeight = FontWeight.Medium)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = item.errorMessage.orEmpty(),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(id = R.string.error_report_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val titleParam = Uri.encode("[Bug] Pipeline clean failed: ${item.errorMessage?.take(45)}")
                            val bodyTemplate =
                                """
                                |### SB Skip Pipeline Error Report
                                |
                                |* **App Version**: ${BuildConfig.VERSION_NAME}
                                |* **Video Title**: ${item.title}
                                |* **Video URL**: ${item.cleanUrl}
                                |* **Media Type**: ${item.mediaType.name}
                                |
                                |#### Exception Stack Trace
                                |```text
                                |${item.errorMessage}
                                |```
                                """.trimMargin()
                            val bodyParam = Uri.encode(bodyTemplate)
                            val githubUrl = "https://github.com/nihaltp/SBSkip/issues/new?title=$titleParam&body=$bodyParam"

                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                            context.startActivity(intent)
                            errorDialogItem = null
                        },
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.report_to_github))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { errorDialogItem = null }) {
                        Text(stringResource(id = R.string.dismiss))
                    }
                },
            )
        }
    }

    // Full media details popup for card clicks
    detailsDialogItem?.let { item ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { detailsDialogItem = null },
            title = { Text(stringResource(id = R.string.media_details_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DetailRow(label = stringResource(id = R.string.label_title), value = item.title)
                    DetailRow(label = stringResource(id = R.string.youtube_url_label), value = item.cleanUrl)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(id = R.string.media_type),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            Text(item.mediaType.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(id = R.string.duration_label),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            Text(item.displayDuration, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(id = R.string.status_label),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            Text(item.status.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(
                                stringResource(id = R.string.imported_path_label),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            SelectionContainer {
                                Text(
                                    text = formatUriToPath(context, item.localFileUri),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        if (!item.outputPath.isNullOrBlank() && item.outputPath != item.localFileUri) {
                            Column {
                                Text(
                                    stringResource(id = R.string.saved_location_label),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                )
                                SelectionContainer {
                                    Text(
                                        text = formatUriToPath(context, item.outputPath),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.title))
                            Toast.makeText(context, context.getString(R.string.title_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(id = R.string.copy_title))
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.cleanUrl))
                            Toast.makeText(context, context.getString(R.string.url_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(id = R.string.copy_url))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detailsDialogItem = null }) {
                    Text(stringResource(id = R.string.close))
                }
            },
        )
    }

    // Full pending download details popup for card clicks
    detailsPendingDownloadItem?.let { item ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { detailsPendingDownloadItem = null },
            title = { Text(stringResource(id = R.string.waiting_for_download_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DetailRow(label = stringResource(id = R.string.label_title), value = item.title)
                    DetailRow(label = stringResource(id = R.string.youtube_url_label), value = item.url)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(id = R.string.status_label),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            val statusText =
                                if (item.isDetectingFile) {
                                    stringResource(id = R.string.detecting_recent_download)
                                } else if (item.detectedFile != null) {
                                    stringResource(id = R.string.found_matching_file, item.detectedFile.score)
                                } else {
                                    stringResource(id = R.string.no_pending_download)
                                }
                            Text(statusText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (item.detectedFile != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column {
                                Text(
                                    stringResource(id = R.string.imported_path_label),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                )
                                SelectionContainer {
                                    Text(
                                        text = formatUriToPath(context, item.detectedFile.uri),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (!item.detectedFileName.isNullOrBlank()) {
                                Column {
                                    Text(
                                        stringResource(id = R.string.selected_file_label),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Gray,
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = item.detectedFileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.title))
                            Toast.makeText(context, context.getString(R.string.title_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(id = R.string.copy_title))
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.url))
                            Toast.makeText(context, context.getString(R.string.url_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(id = R.string.copy_url))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detailsPendingDownloadItem = null }) {
                    Text(stringResource(id = R.string.close))
                }
            },
        )
    }

    if (uiState.showWatchlistPromptDialog) {
        AlertDialog(
            onDismissRequest = onDismissWatchlistPrompt,
            title = { Text(stringResource(id = R.string.dialog_watchlist_prompt_title)) },
            text = { Text(stringResource(id = R.string.dialog_watchlist_prompt_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissWatchlistPrompt()
                        onOpenSettings()
                    },
                ) {
                    Text(stringResource(id = R.string.dialog_watchlist_prompt_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissWatchlistPrompt) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    }

    if (uiState.showDurationMismatchDialog) {
        val fileDurationStr =
            uiState.mismatchFileDuration.let { seconds ->
                val minutes = seconds / 60
                val remaining = seconds % 60
                "%d:%02d".format(minutes, remaining)
            }
        val youtubeDurationStr =
            uiState.mismatchYoutubeDuration.let { seconds ->
                val minutes = seconds / 60
                val remaining = seconds % 60
                "%d:%02d".format(minutes, remaining)
            }
        AlertDialog(
            onDismissRequest = onCancelMismatchDialog,
            title = { Text(stringResource(id = R.string.dialog_duration_mismatch_title)) },
            text = {
                Text(stringResource(id = R.string.dialog_duration_mismatch_message, fileDurationStr, youtubeDurationStr))
            },
            confirmButton = {
                Button(onClick = onProceedAnyway) {
                    Text(stringResource(id = R.string.dialog_duration_mismatch_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelMismatchDialog) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    }

    if (uiState.showConflictDialog) {
        AlertDialog(
            onDismissRequest = onCancelConflictDialog,
            title = { Text(stringResource(id = R.string.dialog_conflict_title)) },
            text = {
                Text(stringResource(id = R.string.dialog_conflict_message, uiState.conflictFileName))
            },
            confirmButton = {
                Button(onClick = onReplaceConflict) {
                    Text(stringResource(id = R.string.dialog_conflict_replace))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onCancelConflictDialog) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onRenameConflict) {
                        Text(stringResource(id = R.string.dialog_conflict_rename))
                    }
                }
            },
        )
    }

    if (showCategoriesDialog) {
        val title = stringResource(id = R.string.categories_dialog_title_runtime)
        val initial = uiState.customSponsorBlockCategories ?: uiState.globalSponsorBlockCategories
        SponsorBlockCategoryPickerDialog(
            title = title,
            initialCategories = initial,
            onCategoriesChanged = onCustomCategoriesChanged,
            onDismissRequest = { showCategoriesDialog = false },
            showResetButton = uiState.customSponsorBlockCategories != null,
            onReset = { onCustomCategoriesChanged(null) },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PendingDownloadCard(
    pendingDownloadTitle: String,
    thumbnailUrl: String?,
    detectedFileName: String?,
    detectedFile: com.nihaltp.sbskip.model.DetectedFile?,
    isDetecting: Boolean,
    onAutoDetect: () -> Unit,
    onPickFileManually: () -> Unit,
    onCancel: () -> Unit,
    onConfirmDetectedFile: () -> Unit,
    onCardClick: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier =
                        Modifier
                            .width(88.dp)
                            .height(88.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    color = Color.Black.copy(alpha = 0.08f),
                ) {
                    if (thumbnailUrl.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    } else {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(thumbnailUrl) {
                                        detectTapGestures(
                                            onLongPress = {
                                                coroutineScope.launch {
                                                    val success =
                                                        com.nihaltp.sbskip.util.ClipboardHelper.copyImageToClipboard(
                                                            context,
                                                            thumbnailUrl,
                                                        )
                                                    if (success) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.thumbnail_copied_toast),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to copy thumbnail image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        )
                                    },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(id = R.string.waiting_for_download_title), fontWeight = FontWeight.Bold)
                    Text(pendingDownloadTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (isDetecting) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(id = R.string.detecting_recent_download))
                        }
                    }
                }
            }

            if (detectedFile != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = R.string.found_matching_file, detectedFile.score), fontWeight = FontWeight.Bold)
                        Text(
                            detectedFileName ?: stringResource(id = R.string.no_media_selected),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(id = R.string.confirm_this_file_prompt))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onConfirmDetectedFile,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.clean_this_file_button))
                            }
                            OutlinedButton(
                                onClick = onAutoDetect,
                                enabled = !isDetecting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.auto_detect_again_button))
                            }
                            OutlinedButton(
                                onClick = onPickFileManually,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.pick_file_manually_button))
                            }
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAutoDetect,
                        enabled = !isDetecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.auto_detect_recent_download))
                    }
                    OutlinedButton(
                        onClick = onPickFileManually,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.pick_file_manually_button))
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        HorizontalDivider()
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body)
        }
    }
}

@Composable
private fun QueueItemCard(
    item: DownloadQueueItem,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onErrorClick: (DownloadQueueItem) -> Unit,
    onCardClick: (DownloadQueueItem) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick(item) }
                        .testTag("queue-item-card"),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(104.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    color = Color(0xFF20242A),
                ) {
                    if (item.thumbnailUrl == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = Color.Gray)
                        }
                    } else {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(item.thumbnailUrl) {
                                        detectTapGestures(
                                            onLongPress = {
                                                val url = item.thumbnailUrl
                                                coroutineScope.launch {
                                                    val success = com.nihaltp.sbskip.util.ClipboardHelper.copyImageToClipboard(context, url)
                                                    if (success) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.thumbnail_copied_toast),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to copy thumbnail image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        )
                                    },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.cleanUrl, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                        }
                        StatusChip(status = item.status)
                    }

                    Text(stringResource(id = R.string.duration_prefix, item.displayDuration))

                    item.errorMessage?.let {
                        Text(
                            text = stringResource(id = R.string.error_click_to_expand_prefix, it),
                            color = Color.Red,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onErrorClick(item) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.status == DownloadQueueStatus.FAILED) {
                    Button(onClick = { onRetry(item.id) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.retry))
                    }
                }
                ElevatedButton(onClick = { onRemove(item.id) }) {
                    Text(stringResource(id = R.string.remove))
                }
            }

            if (item.status == DownloadQueueStatus.FETCHING_SEGMENTS || item.status == DownloadQueueStatus.PROCESSING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadQueueStatus) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                when (status) {
                    DownloadQueueStatus.QUEUED -> stringResource(id = R.string.status_queued)
                    DownloadQueueStatus.FETCHING_SEGMENTS -> stringResource(id = R.string.status_fetching_api)
                    DownloadQueueStatus.PROCESSING -> stringResource(id = R.string.status_cleaning)
                    DownloadQueueStatus.COMPLETED -> stringResource(id = R.string.status_completed)
                    DownloadQueueStatus.FAILED -> stringResource(id = R.string.status_failed)
                },
            )
        },
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatUriToPath(
    context: android.content.Context,
    uriString: String?,
): String {
    if (uriString.isNullOrBlank()) return "N/A"
    val decoded =
        try {
            android.net.Uri.decode(uriString)
        } catch (e: Exception) {
            uriString
        }

    try {
        val uri = android.net.Uri.parse(uriString)
        val resolver = context.contentResolver

        if (uri.scheme == "content") {
            var docId: String? = null
            try {
                if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                    docId = android.provider.DocumentsContract.getDocumentId(uri)
                }
            } catch (e: Exception) {
                // Not a document Uri
            }

            var targetUri = uri
            if (docId != null && docId.startsWith("msf:")) {
                val mediaId = docId.substringAfter("msf:").toLongOrNull()
                if (mediaId != null) {
                    targetUri =
                        android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Files.getContentUri("external"),
                            mediaId,
                        )
                }
            }

            try {
                resolver.query(targetUri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (dataIndex != -1) {
                            val path = cursor.getString(dataIndex)
                            if (!path.isNullOrBlank()) {
                                return formatPrettyAbsolutePath(path)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }

            try {
                resolver.query(
                    targetUri,
                    arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use {
                        cursor ->
                    if (cursor.moveToFirst()) {
                        val relativePathIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                        val displayNameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val relPath = if (relativePathIndex != -1) cursor.getString(relativePathIndex) else null
                        val dispName = if (displayNameIndex != -1) cursor.getString(displayNameIndex) else null
                        if (!relPath.isNullOrBlank() || !dispName.isNullOrBlank()) {
                            val folder = relPath?.trim('/')?.takeIf { it.isNotBlank() }
                            return if (folder != null && dispName != null) "$folder/$dispName" else dispName ?: "$folder/"
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }

            try {
                resolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                        cursor ->
                    if (cursor.moveToFirst()) {
                        val dispIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (dispIndex != -1) {
                            val dispName = cursor.getString(dispIndex)
                            if (!dispName.isNullOrBlank()) {
                                return dispName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                return formatPrettyAbsolutePath(path)
            }
        }
    } catch (e: Exception) {
        // Fallback
    }

    if (decoded.contains("primary:")) {
        val path = decoded.substringAfterLast("primary:").trim('/')
        return if (path.isEmpty()) "SB Skip/" else "$path/"
    }
    if (decoded.contains("raw:")) {
        return formatPrettyAbsolutePath(decoded.substringAfterLast("raw:"))
    }
    if (decoded.contains("/document/")) {
        val docPart = decoded.substringAfterLast("/document/")
        if (docPart.isNotBlank()) {
            return docPart
        }
    }
    if (decoded.contains("/tree/")) {
        val treePart = decoded.substringAfterLast("/tree/")
        if (treePart.isNotBlank()) {
            return treePart
        }
    }

    return decoded
}

private fun formatPrettyAbsolutePath(path: String): String {
    val prefixes = listOf("/storage/emulated/0/", "storage/emulated/0/", "/sdcard/", "sdcard/")
    var pretty = path
    for (prefix in prefixes) {
        if (pretty.startsWith(prefix, ignoreCase = true)) {
            pretty = pretty.substring(prefix.length)
            break
        }
    }
    return pretty.trim('/')
}
