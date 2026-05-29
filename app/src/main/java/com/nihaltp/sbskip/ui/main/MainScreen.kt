package com.nihaltp.sbskip.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MainUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onFileSelected: (Uri) -> Unit,
    onClearSelectedFile: () -> Unit,
    onSubmit: () -> Unit,
    onAutoDetect: () -> Unit,
    onCancelPendingDownload: () -> Unit,
    onConfirmDetectedFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onRetryQueueItem: (Long) -> Unit,
    onSnackbarShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardEmptyText = stringResource(id = R.string.clipboard_empty)

    var errorDialogItem by remember { mutableStateOf<DownloadQueueItem?>(null) }
    var detailsDialogItem by remember { mutableStateOf<DownloadQueueItem?>(null) }
    val showLocalCleanButton = uiState.selectedFileUri != null && uiState.urlInput.isNotBlank()
    val showDownloadAndCleanButton = uiState.selectedFileUri == null && uiState.pendingDownload == null && uiState.isNewPipeInstalled && uiState.urlInput.isNotBlank()

    // Document picker for MP4, M4A, and MP3
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let(onFileSelected)
        },
    )

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
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
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
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.paste_button))
                            }

                            if (showLocalCleanButton || showDownloadAndCleanButton) {
                                Button(onClick = onSubmit) {
                                    if (uiState.isFetchingMetadata) {
                                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (showDownloadAndCleanButton) {
                                            stringResource(id = R.string.download_and_clean_button)
                                        } else {
                                            stringResource(id = R.string.clean_media_button)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.pendingDownload != null) {
                item {
                    PendingDownloadCard(
                        pendingDownloadTitle = uiState.pendingDownload.title,
                        thumbnailUrl = uiState.pendingDownload.thumbnailUrl,
                        detectedFileName = uiState.detectedFileName,
                        detectedFile = uiState.detectedFile,
                        isDetecting = uiState.isDetectingFile,
                        onAutoDetect = onAutoDetect,
                        onPickFileManually = {
                            filePickerLauncher.launch(arrayOf("video/mp4", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4"))
                        },
                        onCancel = onCancelPendingDownload,
                        onConfirmDetectedFile = onConfirmDetectedFile,
                    )
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (uiState.selectedFileUri != null) stringResource(id = R.string.selected_file_label) else stringResource(id = R.string.import_media_file_label),
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray,
                                    )
                                    Text(
                                        text = uiState.selectedFileName.ifBlank { stringResource(id = R.string.no_media_selected) },
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (uiState.selectedFileUri == null) {
                                    ElevatedButton(
                                        onClick = {
                                            filePickerLauncher.launch(arrayOf("video/mp4", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4"))
                                        },
                                    ) {
                                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(id = R.string.pick_file_button))
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
                        onRetry = onRetryQueueItem,
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
        val context = LocalContext.current
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
                    Text(stringResource(id = R.string.error_report_explanation), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val titleParam = Uri.encode("[Bug] Pipeline clean failed: ${item.errorMessage?.take(45)}")
                        val bodyTemplate = """
                            |### SB Skip Pipeline Error Report
                            |
                            |* **App Version**: ${BuildConfig.VERSION_NAME}
                            |* **Video Title**: ${item.title}
                            |* **Video URL**: ${item.url}
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
                    Icon(Icons.Outlined.BugReport, contentDescription = null)
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
                    DetailRow(label = stringResource(id = R.string.youtube_url_label), value = item.url)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.media_type), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(item.mediaType.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.duration_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(item.displayDuration, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.status_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(item.status.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(stringResource(id = R.string.imported_path_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            SelectionContainer {
                                Text(
                                    text = formatUriToPath(item.localFileUri),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        if (!item.outputPath.isNullOrBlank() && item.outputPath != item.localFileUri) {
                            Column {
                                Text(stringResource(id = R.string.saved_location_label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                SelectionContainer {
                                    Text(
                                        text = formatUriToPath(item.outputPath),
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
                            clipboardManager.setText(AnnotatedString(item.url))
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
}

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
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
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
                            modifier = Modifier.fillMaxSize(),
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
                        Text(detectedFileName ?: stringResource(id = R.string.no_media_selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(id = R.string.confirm_this_file_prompt))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConfirmDetectedFile) {
                                Text(stringResource(id = R.string.clean_this_file_button))
                            }
                            OutlinedButton(onClick = onPickFileManually) {
                                Text(stringResource(id = R.string.pick_file_manually_button))
                            }
                            TextButton(onClick = onCancel) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAutoDetect, enabled = !isDetecting) {
                        Text(stringResource(id = R.string.auto_detect_recent_download))
                    }
                    OutlinedButton(onClick = onPickFileManually) {
                        Text(stringResource(id = R.string.pick_file_manually_button))
                    }
                    TextButton(onClick = onCancel) {
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
private fun EmptyStateCard(title: String, body: String) {
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
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCardClick(item) },
            ) {
                Surface(
                    modifier = Modifier
                        .width(104.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    color = Color(0xFF20242A),
                ) {
                    if (item.thumbnailUrl == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = Color.Gray)
                        }
                    } else {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
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
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
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
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatUriToPath(uriString: String?): String {
    if (uriString.isNullOrBlank()) return "N/A"
    try {
        val decoded = android.net.Uri.decode(uriString)

        if (decoded.contains("primary:")) {
            return decoded.substringAfterLast("primary:")
        }

        if (decoded.contains("raw:")) {
            return decoded.substringAfterLast("raw:")
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

        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme == "file") {
            return uri.path ?: decoded
        }

        return decoded
    } catch (e: Exception) {
        return uriString
    }
}
