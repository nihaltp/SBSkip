package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.PlaylistVideo

@Composable
fun PlaylistDuplicateDialog(
    show: Boolean,
    duplicateVideos: List<PlaylistVideo>,
    onConfirm: (Set<String>) -> Unit, // Set of videoIds to download
    onDismiss: () -> Unit,
) {
    if (!show) return

    var selectedVideoIds by remember { mutableStateOf(duplicateVideos.map { it.videoId }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(id = R.string.dialog_playlist_duplicate_title))
        },
        text = {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_playlist_duplicate_message, duplicateVideos.size),
                    fontSize = 16.sp,
                )
                Text(
                    text = stringResource(id = R.string.dialog_playlist_duplicate_select),
                    fontSize = 14.sp,
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(duplicateVideos) { video ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = video.title,
                                maxLines = 2,
                                modifier = Modifier.weight(1f).padding(end = 16.dp),
                            )
                            Checkbox(
                                checked = selectedVideoIds.contains(video.videoId),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedVideoIds += video.videoId
                                    } else {
                                        selectedVideoIds -= video.videoId
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedVideoIds) }) {
                Text(stringResource(id = R.string.dialog_playlist_duplicate_download_selected))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_playlist_duplicate_skip_all))
            }
        },
    )
}
