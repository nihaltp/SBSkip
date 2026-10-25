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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.ExportDestination
import com.nihaltp.sbskip.model.ExportFormat
import com.nihaltp.sbskip.model.ImportResult
import com.nihaltp.sbskip.model.ImportSource
import com.nihaltp.sbskip.ui.main.dialogs.ExportDialog
import com.nihaltp.sbskip.ui.main.dialogs.ImportDialog
import com.nihaltp.sbskip.util.ExportImportUtil
import com.nihaltp.sbskip.util.FolderImportStatus
import kotlinx.coroutines.launch

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
    val filteredVideos by viewModel.filteredVideos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgressMessage by viewModel.importProgressMessage.collectAsState()
    val importQueue by viewModel.importQueue.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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

    // Search state: the text itself lives in the ViewModel; this flag only toggles the
    // expanded search field in the top bar.
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocusRequester.requestFocus()
    }

    fun closeSearch() {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    // Export/Import dialog states
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Export content for file saving
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val visibleItemCount = filteredVideos.size

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

    // State to hold text to import
    var textToImport by remember { mutableStateOf<String?>(null) }

    // File picker for import
    val importFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let { fileUri ->
                coroutineScope.launch {
                    val importedText = ExportImportUtil.readTextFromUri(context, fileUri)
                    if (importedText != null) {
                        textToImport = importedText
                    }
                }
            }
        }

    // Process imported text when textToImport changes
    LaunchedEffect(textToImport) {
        textToImport?.let { text ->
            val result = ExportImportUtil.parseImportedText(text)
            when (result) {
                is ImportResult.Success -> {
                    val videoIds = result.items
                    viewModel.enqueueVideoIds(videoIds, viewModel.settings.value)
                }
                is ImportResult.JsonSuccess -> {
                    val videoIds = result.exportData.items.map { it.videoId }.filter { it.isNotBlank() }.distinct()
                    viewModel.enqueueVideoIds(videoIds, viewModel.settings.value)
                }
                is ImportResult.Failure -> {
                    ExportImportUtil.showToast(context, context.getString(R.string.import_failed, result.error))
                }
            }
            textToImport = null
        }
    }

    val exportFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            uri?.let { fileUri ->
                pendingExportContent?.let { content ->
                    coroutineScope.launch {
                        if (!ExportImportUtil.writeTextToUri(context, fileUri, content)) {
                            ExportImportUtil.showToast(context, context.getString(R.string.export_file_failed))
                        }
                    }
                }
            }
        }

    // File saver for JSON export
    val exportJsonFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let { fileUri ->
                pendingExportContent?.let { content ->
                    coroutineScope.launch {
                        if (!ExportImportUtil.writeTextToUri(context, fileUri, content)) {
                            ExportImportUtil.showToast(context, context.getString(R.string.export_file_failed))
                        }
                    }
                }
            }
        }

    BackHandler(isSearchActive || isSelectEnabled) {
        when {
            isSearchActive -> closeSearch()
            isSelectEnabled -> {
                isSelectEnabled = false
                viewModel.clearSelection()
            }
            else -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = {
                                Text(stringResource(id = R.string.downloaded_history_search_hint))
                            },
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::closeSearch) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(id = R.string.downloaded_history_search_close),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            enabled = searchQuery.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(id = R.string.downloaded_history_search_clear),
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(id = R.string.downloaded_history_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                            // Search button
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(id = R.string.downloaded_history_search),
                                )
                            }
                            // Export button
                            IconButton(
                                onClick = {
                                    showExportDialog = true
                                },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DriveFileMove,
                                    contentDescription = stringResource(id = R.string.export_queue),
                                )
                            }
                            // Import button
                            IconButton(
                                onClick = {
                                    showImportDialog = true
                                },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = stringResource(id = R.string.import_queue),
                                )
                            }
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
            }
        },
        bottomBar = {
            if (isSelectEnabled) {
                BottomAppBar(modifier = Modifier) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = {
                            viewModel.toggleSelectAll(filteredVideos.map { it.id })
                        },
                    )
                    Text(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
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
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                            ) {
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
                                    Column(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp),
                                    ) {
                                        Text(
                                            text =
                                                queueItem.folderName
                                                    ?: context.getString(R.string.unknown_folder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(id = queueStatusTextRes(queueItem.status)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = queueStatusColor(queueItem.status),
                                            maxLines = 1,
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(start = 8.dp),
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
                                        } else if (queueItem.status == FolderImportStatus.PENDING ||
                                            queueItem.status == FolderImportStatus.SCANNING
                                        ) {
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
            if (searchQuery.isNotBlank() && filteredVideos.isEmpty()) {
                // A non-blank query matched nothing, even with an empty store: the search
                // no-results state takes precedence over the regular empty state.
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(id = R.string.downloaded_history_search_no_results_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.downloaded_history_search_no_results_body,
                                searchQuery,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else if (videos.isEmpty() && !isImporting) {
                // Blank query but no videos in the store: the regular empty state.
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
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
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filteredVideos,
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

    // Export Dialog
    if (showExportDialog) {
        val queueItems = videos
        ExportDialog(
            onDismissRequest = { showExportDialog = false },
            itemCount = queueItems.size,
            onExport = { format, destination ->
                val content = ExportImportUtil.exportDownloadedVideos(queueItems, format)
                when (destination) {
                    ExportDestination.CLIPBOARD -> {
                        val success = ExportImportUtil.copyToClipboard(context, content)
                        ExportImportUtil.showToast(
                            context,
                            if (success) {
                                context.getString(R.string.export_copied_to_clipboard, queueItems.size)
                            } else {
                                context.getString(R.string.export_clipboard_failed)
                            },
                        )
                    }
                    ExportDestination.FILE -> {
                        pendingExportContent = content
                        // CreateDocument.launch() receives the suggested filename, not the MIME type.
                        when (format) {
                            ExportFormat.FULL_JSON -> exportJsonFileLauncher.launch(ExportImportUtil.generateExportFilename(format))
                            else -> exportFileLauncher.launch(ExportImportUtil.generateExportFilename(format))
                        }
                    }
                }
            },
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ImportDialog(
            onDismissRequest = { showImportDialog = false },
            onImport = { source, clipboardText ->
                when (source) {
                    ImportSource.CLIPBOARD -> {
                        clipboardText?.let { text ->
                            if (text.isNotBlank()) {
                                textToImport = text
                            }
                        }
                    }
                    ImportSource.FILE -> {
                        importFileLauncher.launch("text/plain")
                    }
                }
            },
        )
    }
}

private fun queueStatusTextRes(status: FolderImportStatus): Int =
    when (status) {
        FolderImportStatus.PENDING -> R.string.downloaded_history_import_status_pending
        FolderImportStatus.SCANNING -> R.string.downloaded_history_import_status_scanning
        FolderImportStatus.COMPLETED -> R.string.downloaded_history_import_status_completed
        FolderImportStatus.FAILED -> R.string.downloaded_history_import_status_failed
    }

@Composable
private fun queueStatusColor(status: FolderImportStatus): Color =
    when (status) {
        FolderImportStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        FolderImportStatus.SCANNING -> MaterialTheme.colorScheme.primary
        // primaryContainer is a background color; using it for TEXT on the surfaceVariant
        // card fails contrast, so completions use primary.
        FolderImportStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        FolderImportStatus.FAILED -> MaterialTheme.colorScheme.error
    }
