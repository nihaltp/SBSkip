package com.nihaltp.sbskip.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.PendingDownload
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.ui.components.SponsorBlockCategoryPickerDialog
import com.nihaltp.sbskip.ui.main.components.cards.PendingDownloadCard
import com.nihaltp.sbskip.ui.main.components.cards.QueueItemCard
import com.nihaltp.sbskip.ui.main.components.common.EmptyStateCard
import com.nihaltp.sbskip.ui.main.components.common.SectionHeader
import com.nihaltp.sbskip.ui.main.dialogs.DownloadOptionsDialog
import com.nihaltp.sbskip.ui.main.dialogs.DurationMismatchDialog
import com.nihaltp.sbskip.ui.main.dialogs.ErrorDetailsDialog
import com.nihaltp.sbskip.ui.main.dialogs.MediaConflictDialog
import com.nihaltp.sbskip.ui.main.dialogs.MediaDetailsDialog
import com.nihaltp.sbskip.ui.main.dialogs.PendingDownloadDetailsDialog
import com.nihaltp.sbskip.ui.main.dialogs.WatchlistPromptDialog
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
    onDownloadOptionsConvertChanged: (Boolean) -> Unit,
    onDownloadOptionsDeleteChanged: (Boolean) -> Unit,
    onConfirmDownloadOptions: () -> Unit,
    onDismissDownloadOptions: () -> Unit,
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
    val isVideoFile = uiState.selectedFileMediaType == MediaType.VIDEO
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
                    AppLogger.error("MainScreen", e, "Failed to take persistable URI permission")
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

                        if (uiState.selectedFileUri != null && uiState.selectedFileMediaType == MediaType.VIDEO) {
                            val isUrlEmpty = uiState.urlInput.isBlank()
                            val isConvertChecked = isUrlEmpty || uiState.convertVideoToAudio
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
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

    ErrorDetailsDialog(
        item = errorDialogItem,
        onDismiss = { errorDialogItem = null },
        onRetryQueueItem = onRetryQueueItem,
    )

    MediaDetailsDialog(
        item = detailsDialogItem,
        onDismiss = { detailsDialogItem = null },
    )

    PendingDownloadDetailsDialog(
        item = detailsPendingDownloadItem,
        onDismiss = { detailsPendingDownloadItem = null },
    )

    WatchlistPromptDialog(
        show = uiState.showWatchlistPromptDialog,
        onOpenSettings = onOpenSettings,
        onDismissWatchlistPrompt = onDismissWatchlistPrompt,
    )

    DurationMismatchDialog(
        show = uiState.showDurationMismatchDialog,
        mismatchFileDuration = uiState.mismatchFileDuration,
        mismatchYoutubeDuration = uiState.mismatchYoutubeDuration,
        onProceedAnyway = onProceedAnyway,
        onCancelMismatchDialog = onCancelMismatchDialog,
    )

    MediaConflictDialog(
        show = uiState.showConflictDialog,
        conflictFileName = uiState.conflictFileName,
        onReplaceConflict = onReplaceConflict,
        onRenameConflict = onRenameConflict,
        onCancelConflictDialog = onCancelConflictDialog,
    )

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

    DownloadOptionsDialog(
        show = uiState.showDownloadOptionsDialog,
        convertToAudio = uiState.downloadOptionsConvertToAudio,
        deleteOriginal = uiState.downloadOptionsDeleteOriginal,
        onConvertToAudioChange = onDownloadOptionsConvertChanged,
        onDeleteOriginalChange = onDownloadOptionsDeleteChanged,
        onConfirm = onConfirmDownloadOptions,
        onDismiss = onDismissDownloadOptions,
    )
}
