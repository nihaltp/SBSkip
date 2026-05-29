package com.nihaltp.sbskip.model

enum class DownloadQueueStatus {
    QUEUED,
    FETCHING_SEGMENTS,
    PROCESSING,
    COMPLETED,
    FAILED,
}

data class DownloadQueueItem(
    val id: Long,
    val url: String,
    val title: String,
    val localFileUri: String,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val status: DownloadQueueStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorMessage: String?,
    val outputPath: String? = null,
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

data class PendingDownload(
    val videoId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val createdAtEpochMillis: Long,
)

data class DetectedFile(
    val uri: String,
    val score: Int,
)

data class MainUiState(
    val urlInput: String = "",
    val selectedFileUri: String? = null,
    val selectedFileName: String = "",
    val isNewPipeInstalled: Boolean = false,
    val isFetchingMetadata: Boolean = false,
    val isDetectingFile: Boolean = false,
    val pendingDownload: PendingDownload? = null,
    val detectedFile: DetectedFile? = null,
    val detectedFileName: String? = null,
    val queueItems: List<DownloadQueueItem> = emptyList(),
    val snackbarMessage: String? = null,
)

data class QueueActionResult(
    val success: Boolean,
    val message: String,
)
