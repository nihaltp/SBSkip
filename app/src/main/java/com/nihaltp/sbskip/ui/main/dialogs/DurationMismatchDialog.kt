package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nihaltp.sbskip.R

@Composable
fun DurationMismatchDialog(
    show: Boolean,
    mismatchFileDuration: Long,
    mismatchYoutubeDuration: Long,
    onProceedAnyway: () -> Unit,
    onCancelMismatchDialog: () -> Unit,
) {
    if (!show) return

    val fileDurationStr =
        mismatchFileDuration.let { seconds ->
            val minutes = seconds / 60
            val remaining = seconds % 60
            "%d:%02d".format(minutes, remaining)
        }
    val youtubeDurationStr =
        mismatchYoutubeDuration.let { seconds ->
            val minutes = seconds / 60
            val remaining = seconds % 60
            "%d:%02d".format(minutes, remaining)
        }
    AlertDialog(
        onDismissRequest = onCancelMismatchDialog,
        title = { Text(stringResource(id = R.string.dialog_duration_mismatch_title)) },
        text = {
            Text(stringResource(id = R.string.dialog_duration_mismatch_message, fileDurationStr, youtubeDurationStr))
        },
        confirmButton = {
            Button(onClick = onProceedAnyway) {
                Text(stringResource(id = R.string.dialog_duration_mismatch_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelMismatchDialog) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
