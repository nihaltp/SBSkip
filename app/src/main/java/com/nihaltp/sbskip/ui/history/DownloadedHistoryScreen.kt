package com.nihaltp.sbskip.ui.history

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R

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
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
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
                            enabled = !isImporting,
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
        snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) },
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

            if (videos.isEmpty() && !isImporting) {
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
                                    } else {
                                        viewModel.deleteVideo(id)
                                    }
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
