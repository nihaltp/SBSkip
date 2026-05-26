package com.nihaltp.sbskip.ui.main

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    onSubmit: () -> Unit,
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

    // Document picker for MP4, M4A, and MP3
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let(onFileSelected)
        }
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
                    AssistChip(
                        onClick = onOpenSettings,
                        label = { Text(stringResource(id = R.string.label_settings)) },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    )
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
                        Text("Select Local File & Provide Link", fontWeight = FontWeight.SemiBold)

                        // 1. File picker section
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (uiState.selectedFileUri != null) "Selected File" else "Import Media File",
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = uiState.selectedFileName.ifBlank { "No media file selected" },
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ElevatedButton(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("video/mp4", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4"))
                                    }
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick File")
                                }
                            }
                        }

                        // 2. YouTube URL input
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste YouTube URL or video ID") },
                            singleLine = true,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

                            Button(
                                onClick = onSubmit,
                                enabled = uiState.selectedFileUri != null && uiState.urlInput.isNotBlank()
                            ) {
                                Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clean Media")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Media Cleaning Queue")
            }

            if (uiState.queueItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No cleaning jobs yet",
                        body = "Import a local video/audio file and paste its corresponding YouTube link to start cleaning SponsorBlock segments.",
                    )
                }
            } else {
                items(uiState.queueItems, key = { it.id }) { queueItem ->
                    QueueItemCard(
                        item = queueItem,
                        onRetry = onRetryQueueItem,
                        onRemove = onRemoveQueueItem,
                        onErrorClick = { errorDialogItem = it }
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
            title = { Text("Pipeline Error Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The cleaning pipeline encountered a failure:", fontWeight = FontWeight.Medium)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.errorMessage.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text("You can report this bug to the GitHub issue page. A formatted report containing the stack trace and file details will be pre-populated for you.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val titleParam = Uri.encode("[Bug] Pipeline clean failed: ${item.errorMessage?.take(45)}")
                        val bodyTemplate = """
                            |### SB Skip Pipeline Error Report
                            |
                            |* **App Version**: 0.2.0
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
                    }
                ) {
                    Icon(Icons.Outlined.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report to GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { errorDialogItem = null }) {
                    Text("Dismiss")
                }
            }
        )
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
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            text = "Error (click to expand): $it",
                            color = Color.Red,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onErrorClick(item) }
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
                    DownloadQueueStatus.FETCHING_SEGMENTS -> "Fetching API"
                    DownloadQueueStatus.PROCESSING -> "Cleaning..."
                    DownloadQueueStatus.COMPLETED -> stringResource(id = R.string.status_completed)
                    DownloadQueueStatus.FAILED -> stringResource(id = R.string.status_failed)
                },
            )
        },
    )
}
