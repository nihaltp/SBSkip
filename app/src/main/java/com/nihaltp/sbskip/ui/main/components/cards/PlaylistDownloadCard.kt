package com.nihaltp.sbskip.ui.main.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.PlaylistDownloadState
import com.nihaltp.sbskip.ui.main.components.common.ThumbnailImage

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
    onExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalVideos = state.videos.size
    val currentVideo = state.videos.getOrNull(state.currentIndex)
    val isExpanded = state.expanded

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

            if (isExpanded) {
                // Expanded view: show all videos as separate cards
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.videos) { index, video ->
                        val isCurrent = index == state.currentIndex
                        VideoListItem(
                            video = video,
                            isCurrent = isCurrent,
                            totalVideos = totalVideos,
                            downloadedCount = downloadedCount,
                            processingCount = processingCount,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else if (currentVideo != null) {
                // Collapsed view: show current video
                CurrentVideoSection(
                    currentVideo = currentVideo,
                    state = state,
                    onConvertVideoToAudioChanged = onConvertVideoToAudioChanged,
                    onDeleteOriginalVideoChanged = onDeleteOriginalVideoChanged,
                    onDownload = onDownload,
                    onSkip = onSkip,
                    onCancel = onCancel,
                )
            } else {
                Text(stringResource(id = R.string.playlist_loading))
                TextButton(onClick = onCancel) {
                    Text(stringResource(id = R.string.playlist_close))
                }
            }

            // Expand/Collapse button (only show when there are multiple videos)
            if (totalVideos > 1) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onExpandedChanged(!isExpanded) }
                            .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (isExpanded) {
                                stringResource(id = R.string.show_less)
                            } else {
                                stringResource(id = R.string.show_all_videos, totalVideos)
                            },
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector =
                            if (isExpanded) {
                                Icons.Filled.KeyboardDoubleArrowUp
                            } else {
                                Icons.Filled.KeyboardDoubleArrowDown
                            },
                        contentDescription =
                            if (isExpanded) {
                                stringResource(
                                    id = R.string.playlist_collapse,
                                )
                            } else {
                                stringResource(id = R.string.playlist_expand)
                            },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentVideoSection(
    currentVideo: com.nihaltp.sbskip.model.PlaylistVideo,
    state: PlaylistDownloadState,
    onConvertVideoToAudioChanged: (Boolean) -> Unit,
    onDeleteOriginalVideoChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
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
                ThumbnailImage(
                    model = currentVideo.thumbnailUrl,
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
            Text(stringResource(id = R.string.playlist_download))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        ) {
            Text(stringResource(id = R.string.playlist_skip_video))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(id = R.string.playlist_cancel_download))
        }
    }
}

@Composable
private fun VideoListItem(
    video: com.nihaltp.sbskip.model.PlaylistVideo,
    isCurrent: Boolean,
    totalVideos: Int,
    downloadedCount: Int,
    processingCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                color = Color.Black.copy(alpha = 0.08f),
            ) {
                if (video.thumbnailUrl.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                } else {
                    ThumbnailImage(
                        model = video.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (isCurrent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            progress = { if (totalVideos > 0) downloadedCount.toFloat() / totalVideos else 0f },
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        CircularProgressIndicator(
                            progress = { if (totalVideos > 0) processingCount.toFloat() / totalVideos else 0f },
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            stringResource(id = R.string.playlist_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            if (isCurrent) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(id = R.string.playlist_currently_downloading),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
