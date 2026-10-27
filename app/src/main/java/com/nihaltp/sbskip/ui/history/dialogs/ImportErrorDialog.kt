package com.nihaltp.sbskip.ui.history.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R

/**
 * Lists the videos that an import could not store, so the whole import is not reported as failed
 * when only a few entries are broken.
 *
 * There is no report button: a video that fails to import is a bad row in the backup, not a bug
 * worth sending anywhere.
 */
@Composable
fun ImportErrorDialog(
    videoIds: List<String>,
    onDismiss: () -> Unit,
) {
    if (videoIds.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.import_errors_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(id = R.string.import_errors_message))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        videoIds.take(MAX_LISTED_FAILURES).forEach { videoId ->
                            Text(
                                text = videoId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val hiddenCount = videoIds.size - MAX_LISTED_FAILURES
                        if (hiddenCount > 0) {
                            Text(
                                text = stringResource(id = R.string.import_errors_more, hiddenCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.close))
            }
        },
    )
}

/** Long lists are cut off, since the dialog only has to make the failures recognizable. */
private const val MAX_LISTED_FAILURES = 20
