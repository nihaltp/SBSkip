package com.nihaltp.sbskip.ui.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.util.FolderImportStatus

/**
 * Maximum height of the import queue list. The list scrolls on its own, so a long queue never
 * pushes the downloaded video list off-screen.
 */
private val IMPORT_QUEUE_MAX_HEIGHT = 240.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadedHistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgressMessage by viewModel.importProgressMessage.collectAsState()
    val importQueue by viewModel.importQueue.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var announcedTerminalIds by remember { mutableStateOf(emptySet<Long>()) }

    // Announce finished queue items (COMPLETED/FAILED) with a snackbar and then drop them from
    // the active import queue. The queue is collected with a stable key so that purging an item
    // from the queue does not cancel an in-flight announcement.
    LaunchedEffect(Unit) {
        viewModel.importQueue.collect { queue ->
            // Forget ids that already left the queue, so the set cannot grow forever.
            announcedTerminalIds = announcedTerminalIds.intersect(queue.map { it.id }.toSet())
            queue.filter { it.isTerminal }.forEach { item ->
                if (item.id in announcedTerminalIds) return@forEach
                announcedTerminalIds = announcedTerminalIds + item.id
                // The snackbar is the record of the result: drop the row from the active queue
                // right away so only PENDING/SCANNING items stay visible.
                viewModel.dismissCompletedItem(item.id)
                snackbarHostState.showSnackbar(
                    message =
                        if (item.status == FolderImportStatus.COMPLETED) {
                            context.getString(
                                R.string.snackbar_folder_imported,
                                item.folderName ?: context.getString(R.string.unknown_folder),
                                item.importedCount,
                            )
                        } else {
                            context.getString(
                                R.string.snackbar_folder_import_failed,
                                item.folderName ?: context.getString(R.string.unknown_folder),
                                item.errorMessage ?: context.getString(R.string.unknown_error),
                            )
                        },
                )
            }
        }
    }

    val lazyListState = rememberLazyListState()

    var isSelectEnabled by remember { mutableStateOf(false) }

    val visibleItemCount = videos.size

    val isAllSelected by remember(selectedIds, visibleItemCount) {
        derivedStateOf {
            visibleItemCount > 0 && selectedIds.size == visibleItemCount
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                viewModel.scanAndImportFolder(context, it)
            }
        }

    BackHandler(isSelectEnabled) {
        if (isSelectEnabled) {
            isSelectEnabled = false
            viewModel.clearSelection()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(id = R.string.downloaded_history_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                        )
                    }
                },
                actions = {
                    Row {
                        if (videos.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    isSelectEnabled = !isSelectEnabled
                                    if (!isSelectEnabled) viewModel.clearSelection()
                                },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            }
                        }
                        IconButton(
                            onClick = { importLauncher.launch(null) },
                            modifier = Modifier,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inbox,
                                contentDescription = stringResource(id = R.string.import_from_folder_title),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isSelectEnabled) {
                BottomAppBar(modifier = Modifier) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = {
                            viewModel.toggleSelectAll(videos.map { it.id })
                        },
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text =
                            stringResource(
                                id = R.string.multiselect_item_count,
                                selectedCount,
                            ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            viewModel.deleteSelectedVideos()
                            isSelectEnabled = false
                        },
                        enabled = selectedCount > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(id = R.string.remove),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isImporting) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.import_from_folder_scanning),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                importProgressMessage?.let { filename ->
                                    Text(
                                        text = filename,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (importQueue.isNotEmpty()) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.import_queue_section),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // The queue list is bounded and scrolls on its own, so a long queue can
                        // never push the video list below it off-screen.
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = IMPORT_QUEUE_MAX_HEIGHT)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            importQueue.forEach { queueItem ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text =
                                                queueItem.folderName
                                                    ?: context.getString(R.string.unknown_folder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = queueStatusText(queueItem.status),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = queueStatusColor(queueItem.status),
                                            maxLines = 1,
                                        )
                                    }
                                    Column(
                                        modifier =
                                            Modifier
                                                .padding(start = 8.dp)
                                                .weight(1f, fill = false),
                                        horizontalAlignment = Alignment.End,
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    id = R.string.imported_count_format,
                                                    queueItem.importedCount,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (queueItem.status == FolderImportStatus.FAILED) {
                                            Text(
                                                text = queueItem.errorMessage ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (queueItem.status == FolderImportStatus.PENDING) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.removeQueueItem(queueItem.id)
                                                    announcedTerminalIds -= queueItem.id
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(id = R.string.remove),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (videos.isEmpty() && !isImporting) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(id = R.string.downloaded_history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.downloaded_history_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = videos,
                        key = { it.id },
                    ) { video ->
                        with(video) {
                            MediaListItem(
                                modifier = Modifier,
                                title = title,
                                author = author ?: "",
                                thumbnailUrl = thumbnailUrl,
                                videoPath = outputPath ?: "",
                                fileSize = fileSize,
                                mediaType = mediaType,
                                isSelectEnabled = { isSelectEnabled },
                                isSelected = { selectedIds.contains(id) },
                                onSelect = {
                                    viewModel.toggleSelection(id)
                                },
                                onClick = {
                                    if (isSelectEnabled) {
                                        viewModel.toggleSelection(id)
                                    }
                                    // Outside of selection mode a tap does nothing
                                },
                                onLongClick = {
                                    isSelectEnabled = true
                                    viewModel.toggleSelection(id)
                                },
                                onShowContextMenu = {
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun queueStatusText(status: FolderImportStatus): String =
    when (status) {
        FolderImportStatus.PENDING -> "Pending"
        FolderImportStatus.SCANNING -> "Scanning"
        FolderImportStatus.COMPLETED -> "Completed"
        FolderImportStatus.FAILED -> "Failed"
    }

@Composable
private fun queueStatusColor(status: FolderImportStatus): Color =
    when (status) {
        FolderImportStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        FolderImportStatus.SCANNING -> MaterialTheme.colorScheme.primary
        FolderImportStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        FolderImportStatus.FAILED -> MaterialTheme.colorScheme.error
    }
