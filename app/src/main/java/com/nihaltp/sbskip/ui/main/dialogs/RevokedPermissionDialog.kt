package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nihaltp.sbskip.R

@Composable
fun RevokedPermissionDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onReauthorize: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.permission_revoked_title)) },
        text = {
            Text(stringResource(id = R.string.permission_revoked_message, folderName))
        },
        confirmButton = {
            TextButton(onClick = onReauthorize) {
                Text(stringResource(id = R.string.reauthorize))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
