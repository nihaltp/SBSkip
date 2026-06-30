package com.nihaltp.sbskip.ui.main.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihaltp.sbskip.R

@Composable
fun DownloadOptionsDialog(
    show: Boolean,
    convertToAudio: Boolean,
    deleteOriginal: Boolean,
    onConvertToAudioChange: (Boolean) -> Unit,
    onDeleteOriginalChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_download_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Convert to Audio toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.convert_video_to_audio_title),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(id = R.string.convert_video_to_audio_desc),
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                    }
                    Switch(
                        checked = convertToAudio,
                        onCheckedChange = onConvertToAudioChange,
                    )
                }

                // Delete Original toggle — only shown when converting to audio
                if (convertToAudio) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.delete_original_video_title),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(id = R.string.delete_original_video_desc),
                                fontSize = 12.sp,
                                color = Color.Gray,
                            )
                        }
                        Switch(
                            checked = deleteOriginal,
                            onCheckedChange = onDeleteOriginalChange,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(id = R.string.dialog_download_options_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
