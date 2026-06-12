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
import com.nihaltp.sbskip.model.PendingDownload
import com.nihaltp.sbskip.ui.main.components.common.DetailRow
import com.nihaltp.sbskip.util.formatUriToPath

@Composable
fun PendingDownloadDetailsDialog(
    item: PendingDownload?,
    onDismiss: () -> Unit,
) {
    if (item == null) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.waiting_for_download_title), fontWeight = FontWeight.Bold) },
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
                        Text(
                            text = stringResource(id = R.string.status_label),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        val statusText =
                            if (item.isDetectingFile) {
                                stringResource(id = R.string.detecting_recent_download)
                            } else if (item.detectedFile != null) {
                                stringResource(id = R.string.found_matching_file, item.detectedFile.score)
                            } else {
                                stringResource(id = R.string.no_pending_download)
                            }
                        Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (item.detectedFile != null) {
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
                                    text = formatUriToPath(context, item.detectedFile.uri),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (!item.detectedFileName.isNullOrBlank()) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.selected_file_label),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                )
                                SelectionContainer {
                                    Text(
                                        text = item.detectedFileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.close))
            }
        },
    )
}
