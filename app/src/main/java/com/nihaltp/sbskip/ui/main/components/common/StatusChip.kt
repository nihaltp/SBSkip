package com.nihaltp.sbskip.ui.main.components.common

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.DownloadQueueStatus

@Composable
fun StatusChip(
    status: DownloadQueueStatus,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(
                when (status) {
                    DownloadQueueStatus.QUEUED -> stringResource(id = R.string.status_queued)
                    DownloadQueueStatus.FETCHING_SEGMENTS -> stringResource(id = R.string.status_fetching_api)
                    DownloadQueueStatus.PROCESSING -> stringResource(id = R.string.status_cleaning)
                    DownloadQueueStatus.COMPLETED -> stringResource(id = R.string.status_completed)
                    DownloadQueueStatus.FAILED -> stringResource(id = R.string.status_failed)
                },
            )
        },
    )
}
