package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R

@Composable
fun MediaConflictDialog(
    show: Boolean,
    conflictFileName: String,
    onReplaceConflict: () -> Unit,
    onRenameConflict: () -> Unit,
    onCancelConflictDialog: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onCancelConflictDialog,
        title = { Text(stringResource(id = R.string.dialog_conflict_title)) },
        text = {
            Text(stringResource(id = R.string.dialog_conflict_message, conflictFileName))
        },
        confirmButton = {
            Button(onClick = onReplaceConflict) {
                Text(stringResource(id = R.string.dialog_conflict_replace))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancelConflictDialog) {
                    Text(stringResource(id = R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRenameConflict) {
                    Text(stringResource(id = R.string.dialog_conflict_rename))
                }
            }
        },
    )
}
