package com.nihaltp.sbskip.model

enum class DownloadQueueStatus {
    QUEUED,
    FETCHING_INFO,
    READY,
    FAILED,
}

data class DownloadQueueItem(
    val id: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val status: DownloadQueueStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorMessage: String?,
) {
    val displayDuration: String
        get() = durationSeconds?.let { seconds ->
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60
            if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
            } else {
                "%d:%02d".format(minutes, remainingSeconds)
            }
        } ?: "--:--"
}

data class MainUiState(
    val urlInput: String = "",
    val queueItems: List<DownloadQueueItem> = emptyList(),
    val snackbarMessage: String? = null,
)

data class QueueActionResult(
    val success: Boolean,
    val message: String,
)

data class VideoMetadata(
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)
