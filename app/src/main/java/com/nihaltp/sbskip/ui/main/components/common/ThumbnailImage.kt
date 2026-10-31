package com.nihaltp.sbskip.ui.main.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Thumbnail image with a graceful fallback.
 *
 * Coil fails silently when a thumbnail url is broken (e.g. a corrupted row in the database), so
 * the image would simply stay empty. This composable keeps a fallback icon in place of the
 * thumbnail whenever there is no url at all or the load failed.
 */
@Composable
fun ThumbnailImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Default.Album,
    fallbackIconSize: Dp = 48.dp,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
) {
    var loadFailed by remember(model) { mutableStateOf(false) }
    val showFallback = model == null || loadFailed

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { loadFailed = true },
            )
        }
        if (showFallback) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.size(fallbackIconSize),
                tint = fallbackTint,
            )
        }
    }
}
