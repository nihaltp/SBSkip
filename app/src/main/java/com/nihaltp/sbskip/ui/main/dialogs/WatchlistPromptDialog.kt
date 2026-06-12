package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nihaltp.sbskip.R

@Composable
fun WatchlistPromptDialog(
    show: Boolean,
    onOpenSettings: () -> Unit,
    onDismissWatchlistPrompt: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismissWatchlistPrompt,
        title = { Text(stringResource(id = R.string.dialog_watchlist_prompt_title)) },
        text = { Text(stringResource(id = R.string.dialog_watchlist_prompt_message)) },
        confirmButton = {
            Button(
                onClick = {
                    onDismissWatchlistPrompt()
                    onOpenSettings()
                },
            ) {
                Text(stringResource(id = R.string.dialog_watchlist_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissWatchlistPrompt) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
