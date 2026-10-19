package com.nihaltp.sbskip.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedHistoryScreen(
    onNavigateBack: () -> Unit,
    onImportFromFolder: () -> Unit,
    viewModel: DownloadedHistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()

    val lazyListState = rememberLazyListState()

    var isSelectEnabled by remember { mutableStateOf(false) }

    val selectedItemIds = remember(videos) { mutableStateListOf<Long>() }

    Scaffold(
        topBar = {
            LargeTopAppBar(
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
                                    if (!isSelectEnabled) selectedItemIds.clear()
                                },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                )
                            }
                        }
                        IconButton(
                            onClick = onImportFromFolder,
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
                androidx.compose.material3.BottomAppBar(modifier = Modifier) {
                    Checkbox(
                        checked = selectedItemIds.size == videos.size && selectedItemIds.isNotEmpty(),
                        onCheckedChange = { checked ->
                            if (checked) {
                                for (item in videos) selectedItemIds.add(item.id)
                            } else {
                                selectedItemIds.clear()
                            }
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
                        },
                        enabled = selectedItemIds.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(id = R.string.remove),
                        )
                    }
                }
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = androidx.compose.material3.SnackbarHostState()) },
    ) { paddingValues ->
        if (videos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
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
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                state = lazyListState,
                contentPadding = PaddingValues(0.dp),
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
                            isSelected = { viewModel.isSelected(id) },
                            onSelect = {
                                viewModel.toggleSelection(id)
                                if (selectedItemIds.contains(id)) {
                                    selectedItemIds.remove(id)
                                } else {
                                    selectedItemIds.add(id)
                                }
                            },
                            onClick = {
                                if (isSelectEnabled) {
                                    viewModel.toggleSelection(id)
                                    if (selectedItemIds.contains(id)) {
                                        selectedItemIds.remove(id)
                                    } else {
                                        selectedItemIds.add(id)
                                    }
                                }
                            },
                            onLongClick = {
                                isSelectEnabled = true
                                selectedItemIds.add(id)
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
