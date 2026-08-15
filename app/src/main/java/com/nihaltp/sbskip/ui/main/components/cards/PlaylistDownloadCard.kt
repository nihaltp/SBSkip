package com.nihaltp.sbskip.ui.main.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.PlaylistDownloadState

@Composable
fun PlaylistDownloadCard(
    state: PlaylistDownloadState,
    downloadedCount: Int,
    processingCount: Int,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    onConvertVideoToAudioChanged: (Boolean) -> Unit,
    onDeleteOriginalVideoChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalVideos = state.videos.size
    val currentVideo = state.videos.getOrNull(state.currentIndex)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header with overall playlist progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playlist: ${state.currentIndex + 1} of $totalVideos",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )

                // The two circles progress indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val downloadedProgress = if (totalVideos > 0) downloadedCount.toFloat() / totalVideos else 0f
                    val processingProgress = if (totalVideos > 0) processingCount.toFloat() / totalVideos else 0f

                    CircularProgressIndicator(
                        progress = { downloadedProgress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round,
                    )

                    CircularProgressIndicator(
                        progress = { processingProgress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            if (currentVideo != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier =
                            Modifier
                                .width(88.dp)
                                .height(88.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        color = Color.Black.copy(alpha = 0.08f),
                    ) {
                        if (currentVideo.thumbnailUrl.isNullOrBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        } else {
                            AsyncImage(
                                model = currentVideo.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(currentVideo.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Settings toggles
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(id = R.string.convert_video_to_audio_title))
                            Switch(
                                checked = state.convertVideoToAudio,
                                onCheckedChange = onConvertVideoToAudioChanged,
                            )
                        }
                        if (state.convertVideoToAudio) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(id = R.string.delete_original_video_title))
                                Switch(
                                    checked = state.deleteOriginalVideo,
                                    onCheckedChange = onDeleteOriginalVideoChanged,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Download via NewPipe")
                    }
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Text("Skip Video")
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel Playlist Download")
                    }
                }
            } else {
                Text("Playlist loading or finished.")
                TextButton(onClick = onCancel) {
                    Text("Close")
                }
            }
        }
    }
}
