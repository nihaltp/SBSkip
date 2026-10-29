package com.nihaltp.sbskip.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.R

private const val AUDIO_REGEX = "\\.(mp3|aac|opus|m4a|flac|wav)"

@Composable
fun MediaListItem(
    modifier: Modifier = Modifier,
    title: String = "",
    author: String = "",
    thumbnailUrl: String? = null,
    fileSize: Long? = null,
    mediaType: String = "video",
    isSelectEnabled: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    onSelect: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onShowContextMenu: () -> Unit = {},
) {
    val isAudio = mediaType == "audio"
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Box(
        modifier =
            with(modifier) {
                if (!isSelectEnabled()) {
                    clickable(
                        enabled = true,
                        onClick = { onClick() },
                    )
                } else {
                    selectable(selected = isSelected(), onClick = onSelect)
                }
            }.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (isSelectEnabled()) {
                androidx.compose.material3.Checkbox(
                    modifier = Modifier.padding(start = 4.dp, end = 16.dp),
                    checked = isSelected(),
                    onCheckedChange = null,
                )
            }
            MediaImage(
                modifier = Modifier,
                imageUrl = thumbnailUrl,
                isAudio = isAudio,
                hasThumbnail = thumbnailUrl != null && thumbnailUrl.isNotEmpty(),
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (author.isNotEmpty() && author != "null") {
                    Text(
                        modifier = Modifier.padding(top = 3.dp),
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = fileSize?.let { formatFileSize(it) } ?: context.getString(R.string.unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun MediaImage(
    modifier: Modifier = Modifier,
    imageUrl: String?,
    isAudio: Boolean = false,
    hasThumbnail: Boolean = false,
) {
    val imageModel = if (!hasThumbnail) null else imageUrl
    var showFallback by remember(imageModel) { mutableStateOf(true) }

    val aspectRatio = if (!isAudio) 16f / 9f else 1f
    Box(
        modifier =
            modifier.height(90.dp).aspectRatio(aspectRatio, matchHeightConstraintsFirst = true).clip(
                MaterialTheme.shapes.extraSmall,
            ),
    ) {
        if (hasThumbnail) {
            AsyncImage(
                model = imageModel,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onLoading = { showFallback = true },
                onSuccess = { showFallback = false },
                onError = { showFallback = true },
            )
        }
        if (showFallback) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return "%.1f ${units[unitIndex]}".format(size)
}
