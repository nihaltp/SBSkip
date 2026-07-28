package com.nihaltp.sbskip.ui.main.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.model.ToastMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackedToastHost(
    toastMessages: List<ToastMessage>,
    modifier: Modifier = Modifier,
    onActionClick: (ToastMessage) -> Unit = {},
    onDismissToast: (ToastMessage) -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(300)),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toastMessages.forEach { toast ->
                key(toast.id) {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.animateContentSize(),
                    ) {
                        ToastPill(
                            toast = toast,
                            onActionClick = { onActionClick(toast) },
                            onDismiss = { onDismissToast(toast) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToastPill(
    toast: ToastMessage,
    onActionClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    // We use 'inverse' colors (e.g., dark in light mode, light in dark mode)
    // to match Material 3's default high-contrast Snackbar behavior.
    val backgroundColor = MaterialTheme.colorScheme.inverseSurface
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    val actionColor = MaterialTheme.colorScheme.inversePrimary

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            Surface(
                modifier =
                    Modifier
                        .widthIn(min = 320.dp, max = 340.dp)
                        .clip(CircleShape)
                        .background(backgroundColor),
                color = backgroundColor,
                contentColor = contentColor,
                shadowElevation = 6.dp,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = toast.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )

                    if (toast.actionLabel != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = toast.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = actionColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onActionClick() },
                        )
                    }
                }
            }
        },
    )
}
