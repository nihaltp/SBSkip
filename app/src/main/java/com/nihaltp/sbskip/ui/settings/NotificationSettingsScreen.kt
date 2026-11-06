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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
            // Should not happen, but handle gracefully
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(channels) { channel ->
                    NotificationChannelCard(
                        channel = channel,
                        onToggleExpand = { viewModel.toggleExpand(channel.config.id) },
                        onToggleEnabled = { viewModel.toggleChannel(channel.config.id, it) },
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
    onOpenSystemSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(),
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Channel header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = channel.config.nameRes),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(id = channel.config.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = channel.isEnabled,
                        onCheckedChange = if (channel.canToggle) onToggleEnabled else null,
                        enabled = channel.canToggle,
                    )
                    if (!channel.canToggle) {
                        TextButton(onClick = onOpenSystemSettings) {
                            Text(stringResource(id = R.string.settings_fix_notifications))
                        }
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = if (channel.isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Expandable notification IDs list
            if (channel.isExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    channel.config.notificationIds.forEach { notifId ->
                        NotificationIdRow(
                            name = stringResource(id = notifId.nameRes),
                            description = stringResource(id = notifId.descriptionRes),
                            enabled = channel.isEnabled && channel.canToggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationIdRow(
    name: String,
    description: String,
    enabled: Boolean,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (enabled) {
            Text(
                text = stringResource(id = R.string.notification_channel_enabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        } else {
            Text(
                text = stringResource(id = R.string.notification_channel_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
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
