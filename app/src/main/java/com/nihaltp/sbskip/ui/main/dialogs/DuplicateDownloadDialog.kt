package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nihaltp.sbskip.R

@Composable
fun DuplicateDownloadDialog(
    show: Boolean,
    videoTitle: String,
    onDownloadAgain: () -> Unit,
    onSkip: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(id = R.string.dialog_duplicate_download_title)) },
        text = {
            Text(
                text =
                    if (videoTitle.isBlank()) {
                        stringResource(id = R.string.dialog_duplicate_download_message)
                    } else {
                        stringResource(id = R.string.dialog_duplicate_download_message_named, videoTitle)
                    },
            )
        },
        confirmButton = {
            Button(onClick = onDownloadAgain) {
                Text(stringResource(id = R.string.dialog_duplicate_download_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(id = R.string.dialog_duplicate_download_skip))
            }
        },
    )
}
