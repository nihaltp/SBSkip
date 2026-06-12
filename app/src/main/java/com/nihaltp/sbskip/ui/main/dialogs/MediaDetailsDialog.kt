package com.nihaltp.sbskip.ui.main.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.ui.main.components.common.DetailRow
import com.nihaltp.sbskip.util.formatUriToPath

@Composable
fun MediaDetailsDialog(
    item: DownloadQueueItem?,
    onDismiss: () -> Unit,
) {
    if (item == null) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.media_details_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DetailRow(label = stringResource(id = R.string.label_title), value = item.title)
                DetailRow(label = stringResource(id = R.string.youtube_url_label), value = item.cleanUrl)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.media_type),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        Text(item.mediaType.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.duration_label),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        Text(item.displayDuration, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.status_label),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        Text(item.status.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.imported_path_label),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        SelectionContainer {
                            Text(
                                text = formatUriToPath(context, item.localFileUri),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    if (!item.outputPath.isNullOrBlank() && item.outputPath != item.localFileUri) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.saved_location_label),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                            )
                            SelectionContainer {
                                Text(
                                    text = formatUriToPath(context, item.outputPath),
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
                        clipboardManager.setText(AnnotatedString(item.cleanUrl))
                        Toast.makeText(context, context.getString(R.string.url_copied_toast), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(id = R.string.copy_url))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.close))
            }
        },
    )
}
