package com.nihaltp.sbskip.ui.main

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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                        Text(stringResource(id = R.string.paste_youtube_link_prompt), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(id = R.string.placeholder_url)) },
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
                            Button(onClick = onSubmit) {
                                Text(stringResource(id = R.string.add_to_queue))
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.current_queue))
            }

            if (uiState.queueItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(id = R.string.no_downloads_yet_title),
                        body = stringResource(id = R.string.no_downloads_yet_body),
                    )
                }
            } else {
                items(uiState.queueItems, key = { it.id }) { queueItem ->
                    QueueItemCard(
                        item = queueItem,
                        onRetry = onRetryQueueItem,
                        onRemove = onRemoveQueueItem,
                    )
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
                        Box(modifier = Modifier.fillMaxSize())
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
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusChip(item.status)
                    }

                    Text(stringResource(id = R.string.duration_prefix, item.displayDuration))

                    item.errorMessage?.let { Text(it) }
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

            if (item.status == DownloadQueueStatus.FETCHING_INFO) {
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
                    DownloadQueueStatus.FETCHING_INFO -> stringResource(id = R.string.status_fetching_info)
                    DownloadQueueStatus.READY -> stringResource(id = R.string.status_ready)
                    DownloadQueueStatus.FAILED -> stringResource(id = R.string.status_failed)
                },
            )
        },
    )
}
