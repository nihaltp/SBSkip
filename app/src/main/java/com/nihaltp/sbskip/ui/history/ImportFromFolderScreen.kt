package com.nihaltp.sbskip.ui.history

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFromFolderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportFromFolderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val folderUri by viewModel.folderUri.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()

    var showConfirmation by remember { mutableStateOf(false) }

    val openDocumentTreeLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                AppLogger.metadata("ImportFolder: Folder selected: $it")
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                viewModel.setFolderUri(it)
                viewModel.scanFolder(context, it)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.import_from_folder_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (folderUri == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(id = R.string.import_from_folder_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.import_from_folder_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = { openDocumentTreeLauncher.launch(null) },
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Text(stringResource(id = R.string.import_from_folder_select_folder))
                    }
                }
            } else if (isScanning) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = R.string.import_from_folder_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.import_from_folder_selected_folder_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (candidates.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(id = R.string.import_from_folder_no_sbskip_files),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Button(
                            onClick = {
                                viewModel.clearSelection()
                                openDocumentTreeLauncher.launch(null)
                            },
                        ) {
                            Text(stringResource(id = R.string.import_from_folder_choose_another))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.import_from_folder_found_count,
                                        candidates.size,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.toggleSelectAll() },
                                ) {
                                    Text(
                                        text =
                                            if (selectedCount == candidates.size) {
                                                stringResource(id = R.string.import_from_folder_deselect_all)
                                            } else {
                                                stringResource(id = R.string.import_from_folder_select_all)
                                            },
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = candidates,
                                key = { it.id },
                            ) { candidate ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .padding(12.dp)
                                                .clickable {
                                                    viewModel.toggleCandidate(candidate)
                                                },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = candidate.isSelected,
                                            onCheckedChange = { viewModel.toggleCandidate(candidate) },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = candidate.youtubeId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedCount > 0) {
                                showConfirmation = true
                            }
                        },
                        enabled = selectedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.import_from_folder_import_selected,
                                    selectedCount,
                                ),
                        )
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(stringResource(id = R.string.import_from_folder_confirm_title)) },
            text = {
                Text(
                    text =
                        stringResource(
                            id = R.string.import_from_folder_confirm_message,
                            selectedCount,
                        ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importSelected()
                        showConfirmation = false
                    },
                ) {
                    Text(stringResource(id = R.string.import_from_folder_confirm_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    }
}
