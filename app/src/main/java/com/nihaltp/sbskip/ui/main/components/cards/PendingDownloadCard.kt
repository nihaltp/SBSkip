package com.nihaltp.sbskip.ui.main.components.cards

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DetectedFile
import com.nihaltp.sbskip.util.ClipboardHelper
import kotlinx.coroutines.launch

@Composable
fun PendingDownloadCard(
    pendingDownloadTitle: String,
    thumbnailUrl: String?,
    detectedFileName: String?,
    detectedFile: DetectedFile?,
    isDetecting: Boolean,
    onAutoDetect: () -> Unit,
    onPickFileManually: () -> Unit,
    onCancel: () -> Unit,
    onConfirmDetectedFile: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier =
                        Modifier
                            .width(88.dp)
                            .height(88.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    color = Color.Black.copy(alpha = 0.08f),
                ) {
                    if (thumbnailUrl.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    } else {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(thumbnailUrl) {
                                        detectTapGestures(
                                            onLongPress = {
                                                coroutineScope.launch {
                                                    val success =
                                                        ClipboardHelper.copyImageToClipboard(
                                                            context,
                                                            thumbnailUrl,
                                                        )
                                                    if (success) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.thumbnail_copied_toast),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to copy thumbnail image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        )
                                    },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(id = R.string.waiting_for_download_title), fontWeight = FontWeight.Bold)
                    Text(pendingDownloadTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (isDetecting) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(id = R.string.detecting_recent_download))
                        }
                    }
                }
            }

            if (detectedFile != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = R.string.found_matching_file, detectedFile.score), fontWeight = FontWeight.Bold)
                        Text(
                            detectedFileName ?: stringResource(id = R.string.no_media_selected),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(id = R.string.confirm_this_file_prompt))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onConfirmDetectedFile,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.clean_this_file_button))
                            }
                            OutlinedButton(
                                onClick = onAutoDetect,
                                enabled = !isDetecting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.auto_detect_again_button))
                            }
                            OutlinedButton(
                                onClick = onPickFileManually,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.pick_file_manually_button))
                            }
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAutoDetect,
                        enabled = !isDetecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.auto_detect_recent_download))
                    }
                    OutlinedButton(
                        onClick = onPickFileManually,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.pick_file_manually_button))
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            }
        }
    }
}
