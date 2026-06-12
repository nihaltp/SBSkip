package com.nihaltp.sbskip.ui.main.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.util.buildGithubBugReportUrl

@Composable
fun ErrorDetailsDialog(
    item: DownloadQueueItem?,
    onDismiss: () -> Unit,
    onRetryQueueItem: (Long, Boolean) -> Unit,
) {
    if (item == null) return

    val context = LocalContext.current
    val errorMessage = item.errorMessage.orEmpty()
    val isDurationMismatch = errorMessage.startsWith("Picked file duration")

    if (isDurationMismatch) {
        val regex = """Picked file duration \((\d+)\s*s\) does not match YouTube video duration \((\d+)\s*s\)""".toRegex()
        val matchResult = regex.find(errorMessage)
        val fileDurationStr =
            matchResult?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds ->
                val minutes = seconds / 60
                val remaining = seconds % 60
                "%d:%02d".format(minutes, remaining)
            } ?: "--:--"
        val youtubeDurationStr =
            matchResult?.groupValues?.getOrNull(2)?.toLongOrNull()?.let { seconds ->
                val minutes = seconds / 60
                val remaining = seconds % 60
                "%d:%02d".format(minutes, remaining)
            } ?: "--:--"

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(id = R.string.dialog_duration_mismatch_title)) },
            text = {
                Text(stringResource(id = R.string.dialog_duration_mismatch_message, fileDurationStr, youtubeDurationStr))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRetryQueueItem(item.id, true)
                        onDismiss()
                    },
                ) {
                    Text(stringResource(id = R.string.dialog_duration_mismatch_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(id = R.string.error_details_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(id = R.string.error_details_intro), fontWeight = FontWeight.Medium)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = item.errorMessage.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.error_report_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val githubUrl = buildGithubBugReportUrl(BuildConfig.VERSION_NAME, item)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.report_to_github))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.dismiss))
                }
            },
        )
    }
}
