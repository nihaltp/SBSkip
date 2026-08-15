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
    val outputDurationSeconds: Long? = null,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
    val audioOutputDirUri: String? = null,
    val videoOutputDirUri: String? = null,
    val relativePath: String? = null,
) {
    val displayDuration: String
        get() {
            val original = durationSeconds?.takeIf { it > 0 }?.let { formatSeconds(it) } ?: "--:--"
            val output = outputDurationSeconds?.takeIf { it > 0 }?.let { formatSeconds(it) }
            return if (output != null && output != original) {
                "$original ($output)"
            } else {
                original
            }
        }

    private fun formatSeconds(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        } else {
            "%d:%02d".format(minutes, remainingSeconds)
        }
    }

    val cleanUrl: String
        get() = if (url.startsWith("sbskip://")) "" else url.substringBefore("?bypassDurationCheck").substringBefore("&bypassDurationCheck")
}

data class PendingDownload(
    val videoId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val createdAtEpochMillis: Long,
    val detectedFile: DetectedFile? = null,
    val detectedFileName: String? = null,
    val isDetectingFile: Boolean = false,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
    // Non-null when we launched NewPipe and need to wait for the download to finish
    // before running auto-detect. Null means run auto-detect immediately.
    val estimatedReadyAtEpochMillis: Long? = null,
)

data class DetectedFile(
    val uri: String,
    val score: Int,
    val relativePath: String? = null,
    val folderUri: String? = null,
)

data class PlaylistVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
)

data class PlaylistDownloadState(
    val playlistId: String,
    val title: String,
    val videos: List<PlaylistVideo>,
    val currentIndex: Int = 0,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
)

data class PendingEnqueueData(
    val fileUri: String,
    val title: String,
    val youtubeUrl: String,
    val mediaType: MediaType,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
    val customFolderUri: String? = null,
    val relativePath: String? = null,
    val pendingDownload: PendingDownload? = null,
)

data class ToastMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val message: String,
    val actionLabel: String? = null,
    val itemToRestore: DownloadQueueItem? = null,
)

data class MainUiState(
    val urlInput: String = "",
    val selectedFileUri: String? = null,
    val selectedFileName: String = "",
    val selectedFileMediaType: MediaType? = null,
    val convertVideoToAudio: Boolean = false,
    val deleteOriginalVideo: Boolean = true,
    val isNewPipeInstalled: Boolean = false,
    val isFetchingMetadata: Boolean = false,
    val isDetectingFile: Boolean = false,
    val isVerifyingDuration: Boolean = false,
    val showDurationMismatchDialog: Boolean = false,
    val showConflictDialog: Boolean = false,
    val conflictFileName: String = "",
    val mismatchFileDuration: Long = 0L,
    val mismatchYoutubeDuration: Long = 0L,
    val pendingEnqueueData: PendingEnqueueData? = null,
    val pendingDownloads: List<PendingDownload> = emptyList(),
    val playlistDownloadState: PlaylistDownloadState? = null,
    val pendingDownloadForFilePicker: PendingDownload? = null,
    val queueItems: List<DownloadQueueItem> = emptyList(),
    val toastMessages: List<ToastMessage> = emptyList(),
    val showWatchlistPromptDialog: Boolean = false,
    val globalSponsorBlockCategories: Set<SponsorBlockCategory> = emptySet(),
    val customSponsorBlockCategories: Set<SponsorBlockCategory>? = null,
    // Download options dialog (shown before Download & Clean / Find File)
    val showPermissionRevokedDialog: Boolean = false,
    val revokedWatchlistFolder: com.nihaltp.sbskip.model.WatchlistFolder? = null,
)

data class QueueActionResult(
    val success: Boolean,
    val message: String,
)
