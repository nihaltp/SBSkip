package com.nihaltp.sbskip.ui.main.components.cards

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.ui.main.components.common.StatusChip
import com.nihaltp.sbskip.util.ClipboardHelper
import kotlinx.coroutines.launch

@Composable
fun QueueItemCard(
    item: DownloadQueueItem,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onErrorClick: (DownloadQueueItem) -> Unit,
    onCardClick: (DownloadQueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCardClick(item) }
                        .testTag("queue-item-card"),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(104.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    color = Color(0xFF20242A),
                ) {
                    if (item.thumbnailUrl == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = Color.Gray)
                        }
                    } else {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(item.thumbnailUrl) {
                                        detectTapGestures(
                                            onLongPress = {
                                                val url = item.thumbnailUrl
                                                coroutineScope.launch {
                                                    val success = ClipboardHelper.copyImageToClipboard(context, url)
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

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.cleanUrl, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                        }
                        StatusChip(status = item.status)
                    }

                    Text(stringResource(id = R.string.duration_prefix, item.displayDuration))

                    item.errorMessage?.let {
                        Text(
                            text = stringResource(id = R.string.error_click_to_expand_prefix, it),
                            color = Color.Red,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onErrorClick(item) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.status == DownloadQueueStatus.FAILED) {
                    Button(onClick = { onRetry(item.id) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.retry))
                    }
                }
                ElevatedButton(onClick = { onRemove(item.id) }) {
                    Text(stringResource(id = R.string.remove))
                }
            }

            if (item.status == DownloadQueueStatus.FETCHING_SEGMENTS || item.status == DownloadQueueStatus.PROCESSING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
