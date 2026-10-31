package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.ExportDestination
import com.nihaltp.sbskip.model.ExportFormat
import com.nihaltp.sbskip.model.ImportSource

@Composable
fun ExportDialog(
    itemCount: Int,
    onDismissRequest: () -> Unit,
    onExport: (ExportFormat, ExportDestination) -> Unit,
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.VIDEO_IDS) }
    var selectedDestination by remember { mutableStateOf(ExportDestination.FILE) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.export_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text =
                        stringResource(id = R.string.export_dialog_message)
                            .format(itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Format selection
                Text(
                    text = stringResource(id = R.string.export_format_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { format ->
                        val isSelected = selectedFormat == format
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = format }
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(format.stringResId),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(format.descriptionResId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = format },
                            )
                        }
                    }
                }
                HorizontalDivider()

                // Destination selection
                Text(
                    text = stringResource(id = R.string.export_destination_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportDestination.entries.forEach { destination ->
                        val isSelected = selectedDestination == destination
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDestination = destination }
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(destination.stringResId),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = stringResource(destination.descriptionResId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedDestination = destination },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedFormat, selectedDestination) }) {
                Text(stringResource(id = R.string.export_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}

@Composable
fun ImportDialog(
    onDismissRequest: () -> Unit,
    onImport: (ImportSource, String?) -> Unit,
) {
    var selectedSource by remember { mutableStateOf(ImportSource.FILE) }
    val clipboardText = LocalClipboardManager.current.getText()?.text.orEmpty().trim()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.import_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.import_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(id = R.string.import_source_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportSource.entries.forEach { source ->
                        val isSelected = selectedSource == source
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSource = source }
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = source.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(source.stringResId),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = stringResource(source.descriptionResId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedSource = source },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val text =
                    if (selectedSource == ImportSource.CLIPBOARD) {
                        clipboardText
                    } else {
                        null
                    }
                onImport(selectedSource, text)
            }) {
                Text(stringResource(id = R.string.import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}

// Extension properties for enum display
private val ExportFormat.stringResId: Int
    get() =
        when (this) {
            ExportFormat.VIDEO_IDS -> R.string.export_format_video_ids
            ExportFormat.FULL_URLS -> R.string.export_format_full_urls
            ExportFormat.FULL_JSON -> R.string.export_format_full_json
        }

private val ExportFormat.descriptionResId: Int
    get() =
        when (this) {
            ExportFormat.VIDEO_IDS -> R.string.export_format_video_ids_desc
            ExportFormat.FULL_URLS -> R.string.export_format_full_urls_desc
            ExportFormat.FULL_JSON -> R.string.export_format_full_json_desc
        }

private val ExportDestination.icon
    get() =
        when (this) {
            ExportDestination.FILE -> Icons.Outlined.FileDownload
            ExportDestination.CLIPBOARD -> Icons.Outlined.ContentCopy
        }

private val ExportDestination.stringResId: Int
    get() =
        when (this) {
            ExportDestination.FILE -> R.string.export_dest_file
            ExportDestination.CLIPBOARD -> R.string.export_dest_clipboard
        }

private val ExportDestination.descriptionResId: Int
    get() =
        when (this) {
            ExportDestination.FILE -> R.string.export_dest_file_desc
            ExportDestination.CLIPBOARD -> R.string.export_dest_clipboard_desc
        }

private val ImportSource.icon
    get() =
        when (this) {
            ImportSource.FILE -> Icons.Outlined.FileDownload
            ImportSource.CLIPBOARD -> Icons.Outlined.ContentCopy
            ImportSource.FOLDER -> Icons.Outlined.FolderOpen
        }

private val ImportSource.stringResId: Int
    get() =
        when (this) {
            ImportSource.FILE -> R.string.import_source_file
            ImportSource.CLIPBOARD -> R.string.import_source_clipboard
            ImportSource.FOLDER -> R.string.import_source_folder
        }

private val ImportSource.descriptionResId: Int
    get() =
        when (this) {
            ImportSource.FILE -> R.string.import_source_file_desc
            ImportSource.CLIPBOARD -> R.string.import_source_clipboard_desc
            ImportSource.FOLDER -> R.string.import_source_folder_desc
        }
