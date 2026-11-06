package com.nihaltp.sbskip.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_notification_channels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (channels.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No notification channels configured")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels) { channel ->
                    NotificationChannelCard(
                        channel = channel,
                        onToggleExpand = { viewModel.toggleExpand(channel.config.id) },
                        onToggleEnabled = { viewModel.toggleChannel(channel.config.id, it) },
                        onToggleNotificationId = { id, enabled -> viewModel.toggleNotificationId(id, enabled) },
                        onOpenSystemSettings = {
                            openSystemChannelSettings(context, channel.config.id)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationChannelCard(
    channel: NotificationSettingsViewModel.ChannelUiState,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleNotificationId: (Int, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val isEnabled = channel.isEnabled
    val canToggle = channel.canToggle
    val isExpanded = channel.isExpanded

    // Channel disabled state: M3 uses 0.38 alpha for disabled content
    val channelAlpha = if (isEnabled) 1f else 0.38f

    // Simple rotation state for expand icon
    val rotation = if (isExpanded) 180f else 0f

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = channelAlpha),
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        shape = RoundedCornerShape(16.dp),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // Channel header - clickable row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading: Switch
                Switch(
                    checked = isEnabled,
                    onCheckedChange = if (canToggle) onToggleEnabled else null,
                    enabled = canToggle,
                    modifier = Modifier.padding(PaddingValues(end = 12.dp)),
                )

                // Content: Title + Description (weight 1f)
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(id = channel.config.nameRes),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(id = channel.config.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                // Trailing: System settings button (when enabled) + Expand icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEnabled && canToggle) {
                        TextButton(onClick = onOpenSystemSettings) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(id = R.string.settings_manage_in_system),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else if (!canToggle) {
                        TextButton(onClick = onOpenSystemSettings) {
                            Text(
                                text = stringResource(id = R.string.settings_fix_notifications),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            }

            // Expandable notification IDs list
            if (isExpanded && channel.notificationIds.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp))
                            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = channelAlpha),
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(PaddingValues(vertical = 8.dp)),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            channel.notificationIds.forEach { notifId ->
                                NotificationIdRow(
                                    notifId = notifId,
                                    onToggle = onToggleNotificationId,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationIdRow(
    notifId: NotificationSettingsViewModel.NotificationIdUiState,
    onToggle: (Int, Boolean) -> Unit,
) {
    val isEnabled = notifId.isEnabled
    val canToggle = notifId.canToggle
    val alpha = if (isEnabled) 1f else 0.38f

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading: Switch
        Switch(
            checked = isEnabled,
            onCheckedChange = if (canToggle) { enabled -> onToggle(notifId.config.id, enabled) } else null,
            enabled = canToggle,
            modifier = Modifier.padding(PaddingValues(end = 12.dp)),
        )

        // Content: Name + Description
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(id = notifId.config.nameRes),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = stringResource(id = notifId.config.descriptionRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openSystemChannelSettings(
    context: android.content.Context,
    channelId: String,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent =
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to app notification settings
            val fallbackIntent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            context.startActivity(fallbackIntent)
        }
    } else {
        // Pre-O: app notification settings
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        context.startActivity(intent)
    }
}
