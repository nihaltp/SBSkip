package com.nihaltp.sbskip.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DetectedFile
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.PendingDownload
import com.nihaltp.sbskip.model.PendingEnqueueData
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val queueRepository: QueueRepository,
        private val settingsRepository: SettingsRepository,
        private val downloadStorage: DownloadStorage,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val httpClient = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }

        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        // Tracks the delayed auto-detect coroutine for each pending download (keyed by videoId).
        // Cancelling the job allows the user to trigger detection early via "Search now".
        private val pendingDetectJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

        init {
            viewModelScope.launch {
                queueRepository.observeQueue().collect { queueItems ->
                    _uiState.update { it.copy(queueItems = queueItems) }
                }
            }
            viewModelScope.launch {
                queueRepository.resumeStuckItems()
                val initialSettings = settingsRepository.settings.first()
                _uiState.update {
                    it.copy(
                        convertVideoToAudio = initialSettings.defaultConvertVideoToAudio,
                        deleteOriginalVideo = initialSettings.defaultDeleteOriginalVideo,
                    )
                }
                settingsRepository.settings.collect { settings ->
                    _uiState.update {
                        it.copy(globalSponsorBlockCategories = settings.sponsorBlockSettings.categories)
                    }
                }
            }
            checkNewPipeInstalled()
        }

        fun checkNewPipeInstalled() {
            val installed =
                runCatching {
                    context.packageManager.getPackageInfo(NEWPIPE_PACKAGE_NAME, 0)
                }.isSuccess
            _uiState.update { it.copy(isNewPipeInstalled = installed) }
        }

        fun onUrlChanged(value: String) {
            _uiState.update {
                it.copy(
                    urlInput = value,
                    customSponsorBlockCategories = if (value.isBlank()) null else it.customSponsorBlockCategories,
                )
            }
        }

        fun onCustomCategoriesChanged(categories: Set<SponsorBlockCategory>?) {
            _uiState.update { it.copy(customSponsorBlockCategories = categories) }
        }

        fun onFileSelected(uri: Uri) {
            viewModelScope.launch {
                val state = uiState.value
                val pendingPicker = state.pendingDownloadForFilePicker
                if (pendingPicker != null) {
                    val metadata = downloadStorage.queryMetadata(uri.toString())
                    val name = metadata?.let { "${it.title}.${it.extension}" } ?: context.getString(R.string.imported_file_fallback)
                    _uiState.update { it.copy(pendingDownloadForFilePicker = null) }
                    enqueuePendingDownload(pendingPicker, uri.toString(), name)
                    return@launch
                }

                val metadata = downloadStorage.queryMetadata(uri.toString())
                val name = metadata?.let { "${it.title}.${it.extension}" } ?: context.getString(R.string.imported_file_fallback)
                val mediaType =
                    if (metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "aac") {
                        MediaType.AUDIO
                    } else {
                        MediaType.VIDEO
                    }
                _uiState.update {
                    it.copy(
                        selectedFileUri = uri.toString(),
                        selectedFileName = name,
                        selectedFileMediaType = mediaType,
                    )
                }
            }
        }

        fun clearSelectedFile() {
            _uiState.update {
                it.copy(
                    selectedFileUri = null,
                    selectedFileName = "",
                    selectedFileMediaType = null,
                    convertVideoToAudio = false,
                    deleteOriginalVideo = true,
                )
            }
        }

        fun onConvertVideoToAudioChanged(value: Boolean) {
            _uiState.update { it.copy(convertVideoToAudio = value) }
        }

        fun onDeleteOriginalVideoChanged(value: Boolean) {
            _uiState.update { it.copy(deleteOriginalVideo = value) }
        }

        fun queueCurrentItem() {
            viewModelScope.launch {
                queueCurrentItemInternal()
            }
        }

        fun startDownloadAndClean() {
            viewModelScope.launch {
                val state = uiState.value
                startDownloadAndCleanInternal(state.convertVideoToAudio, state.deleteOriginalVideo)
            }
        }

        fun findFileForUrl() {
            viewModelScope.launch {
                val state = uiState.value
                findFileForUrlInternal(state.convertVideoToAudio, state.deleteOriginalVideo)
            }
        }

        fun dismissPermissionRevokedDialog() {
            _uiState.update { it.copy(showPermissionRevokedDialog = false, revokedWatchlistFolder = null) }
        }

        fun autoDetectAndClean(pendingDownload: PendingDownload) {
            viewModelScope.launch {
                autoDetectAndCleanInternal(pendingDownload)
            }
        }

        /**
         * Called when the user taps "Search now" on a PendingDownloadCard.
         * Cancels the ongoing delay (if any) and runs auto-detect immediately.
         */
        fun triggerAutoDetectNow(pendingDownload: PendingDownload) {
            // Cancel the scheduled delay job, if it exists
            pendingDetectJobs.remove(pendingDownload.videoId)?.cancel()
            // Clear the countdown so the card stops showing the timer
            _uiState.update { state ->
                state.copy(
                    pendingDownloads =
                        state.pendingDownloads.map {
                            if (it.videoId == pendingDownload.videoId) {
                                it.copy(estimatedReadyAtEpochMillis = null)
                            } else {
                                it
                            }
                        },
                )
            }
            viewModelScope.launch {
                autoDetectAndCleanInternal(pendingDownload.copy(estimatedReadyAtEpochMillis = null))
            }
        }

        fun confirmDetectedFile(pendingDownload: PendingDownload) {
            viewModelScope.launch {
                val detected = pendingDownload.detectedFile ?: return@launch
                val detectedName = pendingDownload.detectedFileName ?: context.getString(R.string.detected_file_fallback)
                enqueuePendingDownload(pendingDownload, detected.uri, detectedName, detected.relativePath, detected.folderUri)
            }
        }

        fun skipPlaylistVideo() {
            _uiState.update { state ->
                val plState = state.playlistDownloadState ?: return@update state
                if (plState.currentIndex + 1 < plState.videos.size) {
                    state.copy(
                        playlistDownloadState = plState.copy(currentIndex = plState.currentIndex + 1),
                    )
                } else {
                    // Playlist finished
                    state.copy(playlistDownloadState = null)
                }
            }
        }

        fun downloadPlaylistVideo() {
            val state = uiState.value
            val plState = state.playlistDownloadState ?: return
            val currentVideo = plState.videos[plState.currentIndex]

            // Launch NewPipe
            val normalizedUrl = Constants.buildYouTubeWatchUrl(currentVideo.videoId)
            val customCategories = state.customSponsorBlockCategories
            val finalUrl =
                if (customCategories != null) {
                    "$normalizedUrl&categories=" + customCategories.joinToString(",") { it.name }
                } else {
                    normalizedUrl
                }
            launchNewPipe(normalizedUrl)

            // Spawn pending download
            val now = System.currentTimeMillis()
            val maxExistingTimer = state.pendingDownloads.mapNotNull { it.estimatedReadyAtEpochMillis }.maxOrNull() ?: now
            val startFrom = maxOf(now, maxExistingTimer)
            val waitMillis = DEFAULT_DOWNLOAD_WAIT_SECONDS * 1000L
            val estimatedReady = startFrom + waitMillis

            val pendingDownload =
                PendingDownload(
                    videoId = currentVideo.videoId,
                    url = finalUrl,
                    title = currentVideo.title,
                    thumbnailUrl = currentVideo.thumbnailUrl,
                    createdAtEpochMillis = now,
                    convertVideoToAudio = plState.convertVideoToAudio,
                    deleteOriginalVideo = plState.deleteOriginalVideo,
                    estimatedReadyAtEpochMillis = estimatedReady,
                )

            _uiState.update { st ->
                val nextIndex = plState.currentIndex + 1
                val newPlState =
                    if (nextIndex < plState.videos.size) {
                        plState.copy(currentIndex = nextIndex)
                    } else {
                        null
                    }
                st.copy(
                    pendingDownloads = st.pendingDownloads + pendingDownload,
                    playlistDownloadState = newPlState,
                )
            }

            // Auto detect job
            val detectJob =
                viewModelScope.launch {
                    val delayMillis = estimatedReady - System.currentTimeMillis()
                    if (delayMillis > 0) {
                        kotlinx.coroutines.delay(delayMillis)
                    }
                    _uiState.update { st ->
                        st.copy(
                            pendingDownloads =
                                st.pendingDownloads.map {
                                    if (it.videoId == pendingDownload.videoId) {
                                        it.copy(estimatedReadyAtEpochMillis = null)
                                    } else {
                                        it
                                    }
                                },
                        )
                    }
                    autoDetectAndCleanInternal(pendingDownload.copy(estimatedReadyAtEpochMillis = null))
                }
            pendingDetectJobs[currentVideo.videoId] = detectJob
        }

        fun cancelPlaylistDownload() {
            _uiState.update { it.copy(playlistDownloadState = null) }
        }

        fun setPlaylistConvertVideoToAudio(value: Boolean) {
            _uiState.update { state ->
                state.playlistDownloadState?.let { pl ->
                    state.copy(playlistDownloadState = pl.copy(convertVideoToAudio = value))
                } ?: state
            }
        }

        fun setPlaylistDeleteOriginalVideo(value: Boolean) {
            _uiState.update { state ->
                state.playlistDownloadState?.let { pl ->
                    state.copy(playlistDownloadState = pl.copy(deleteOriginalVideo = value))
                } ?: state
            }
        }

        fun cancelPendingDownload(pendingDownload: PendingDownload) {
            _uiState.update { state ->
                state.copy(
                    pendingDownloads = state.pendingDownloads.filter { it.videoId != pendingDownload.videoId },
                )
            }
        }

        fun startManualPickForPendingDownload(pendingDownload: PendingDownload) {
            _uiState.update { it.copy(pendingDownloadForFilePicker = pendingDownload) }
        }

        private suspend fun enqueuePendingDownload(
            pendingDownload: PendingDownload,
            fileUri: String,
            displayName: String,
            knownRelativePath: String? = null,
            knownFolderUri: String? = null,
            customFolderUri: String? = null,
        ) {
            val settings = settingsRepository.settings.first()
            val metadata = downloadStorage.queryMetadata(fileUri)
            val isAudioExtension =
                metadata?.extension == "mp3" ||
                    metadata?.extension == "m4a" ||
                    metadata?.extension == "aac"
            // Use per-download choices if the pending download was started from the dialog;
            // fall back to settings defaults only for audio extensions.
            val convertVideoToAudio = pendingDownload.convertVideoToAudio || isAudioExtension
            val deleteOriginalVideo = if (convertVideoToAudio) pendingDownload.deleteOriginalVideo else settings.defaultDeleteOriginalVideo
            val mediaType =
                if (convertVideoToAudio) {
                    MediaType.AUDIO
                } else {
                    MediaType.VIDEO
                }

            var finalFolderUri: String? = customFolderUri
            var relativePath: String = ""

            if (finalFolderUri == null) {
                // We check if the source file is in a watchlist folder.
                if (knownFolderUri != null) {
                    finalFolderUri = knownFolderUri
                    relativePath = knownRelativePath ?: ""
                } else {
                    val matched = downloadStorage.getMatchedWatchlistFolder(fileUri)
                    if (matched != null) {
                        finalFolderUri = matched.folder.uri
                        relativePath = matched.relativePath
                    }
                }

                if (finalFolderUri != null && !downloadStorage.hasPersistedPermission(finalFolderUri!!)) {
                    _uiState.update {
                        it.copy(
                            showPermissionRevokedDialog = true,
                            revokedWatchlistFolder =
                                settings.watchlist.find {
                                        folder ->
                                    folder.uri == finalFolderUri
                                } ?: com.nihaltp.sbskip.model.WatchlistFolder("", ""),
                            pendingEnqueueData =
                                PendingEnqueueData(
                                    fileUri = fileUri,
                                    title = displayName.ifBlank { pendingDownload.title },
                                    youtubeUrl = pendingDownload.url,
                                    mediaType = mediaType,
                                    convertVideoToAudio = convertVideoToAudio,
                                    deleteOriginalVideo = deleteOriginalVideo,
                                    customFolderUri = customFolderUri,
                                    pendingDownload = pendingDownload,
                                ),
                        )
                    }
                    return
                }
            }

            // Conflict check!
            val sourceExtension = metadata?.extension ?: "mp4"
            val title = displayName.ifBlank { pendingDownload.title }
            val baseTitle =
                if (title.endsWith(".$sourceExtension", ignoreCase = true)) {
                    title.substring(0, title.length - sourceExtension.length - 1)
                } else {
                    title
                }

            val targetTitle = baseTitle + settings.autoCleanSuffix
            val targetExtension = if (convertVideoToAudio || mediaType == MediaType.AUDIO) "m4a" else sourceExtension

            val exists = downloadStorage.checkFileExists(targetTitle, targetExtension, mediaType, finalFolderUri)
            if (exists) {
                _uiState.update {
                    it.copy(
                        showConflictDialog = true,
                        conflictFileName = "$targetTitle.$targetExtension",
                        pendingEnqueueData =
                            PendingEnqueueData(
                                fileUri = fileUri,
                                title = title,
                                youtubeUrl = pendingDownload.url,
                                mediaType = mediaType,
                                convertVideoToAudio = convertVideoToAudio,
                                deleteOriginalVideo = deleteOriginalVideo,
                                customFolderUri = finalFolderUri,
                                relativePath = relativePath,
                                pendingDownload = pendingDownload,
                            ),
                    )
                }
                return
            }

            val result =
                queueRepository.enqueue(
                    localFileUri = fileUri,
                    title = title,
                    youtubeUrl = pendingDownload.url,
                    mediaType = mediaType,
                    convertVideoToAudio = convertVideoToAudio,
                    deleteOriginalVideo = deleteOriginalVideo,
                    audioOutputDirUri = if (mediaType == MediaType.AUDIO) finalFolderUri else null,
                    videoOutputDirUri = if (mediaType == MediaType.VIDEO) finalFolderUri else null,
                    relativePath = relativePath,
                )

            val toast =
                com.nihaltp.sbskip.model.ToastMessage(
                    message = if (result.success) context.getString(R.string.snackbar_media_enqueued) else result.message,
                )
            scheduleToastDismiss(toast.id)
            _uiState.update { state ->
                if (result.success) {
                    state.copy(
                        pendingDownloads = state.pendingDownloads.filter { it.videoId != pendingDownload.videoId },
                        toastMessages = state.toastMessages + toast,
                    )
                } else {
                    state.copy(toastMessages = state.toastMessages + toast)
                }
            }
        }

        private suspend fun findFileForUrlInternal(
            convertVideoToAudio: Boolean,
            deleteOriginalVideo: Boolean,
        ) {
            val state = uiState.value
            val inputUrl = state.urlInput.trim()
            val videoId = YouTubeUrlParser.extractVideoId(inputUrl)
            val playlistId = YouTubeUrlParser.extractPlaylistId(inputUrl)

            if (!playlistId.isNullOrBlank() && videoId.isNullOrBlank()) {
                showToast("Find File is not currently supported for playlists.")
                return
            }

            if (inputUrl.isBlank() || videoId.isNullOrBlank()) {
                showToast(context.getString(R.string.enter_valid_url))
                return
            }

            _uiState.update { it.copy(isFetchingMetadata = true) }

            val normalizedUrl = Constants.buildYouTubeWatchUrl(videoId)
            val customCategories = state.customSponsorBlockCategories
            val finalUrl =
                if (customCategories != null) {
                    "$normalizedUrl&categories=" + customCategories.joinToString(",") { it.name }
                } else {
                    normalizedUrl
                }

            val metadata =
                runCatching { fetchYouTubeOEmbed(normalizedUrl) }.getOrElse {
                    YouTubeMetadata(
                        title = state.urlInput.ifBlank { videoId },
                        authorName = null,
                        authorUrl = null,
                        thumbnailUrl = null,
                    )
                }

            val pendingDownload =
                PendingDownload(
                    videoId = videoId,
                    url = finalUrl,
                    title = metadata.title.orEmpty().ifBlank { videoId },
                    thumbnailUrl = metadata.thumbnailUrl,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    convertVideoToAudio = convertVideoToAudio,
                    deleteOriginalVideo = deleteOriginalVideo,
                )

            _uiState.update { state ->
                state.copy(
                    urlInput = "",
                    pendingDownloads = state.pendingDownloads + pendingDownload,
                    isFetchingMetadata = false,
                    customSponsorBlockCategories = null,
                )
            }

            autoDetectAndCleanInternal(pendingDownload)
        }

        fun handleSharedText(event: ShareIntentEvent) {
            if (event.text != null) {
                onUrlChanged(event.text)
            }
            if (event.fileUri != null) {
                onFileSelected(event.fileUri)
            }
        }

        fun retryQueueItem(
            id: Long,
            bypassDurationCheck: Boolean = false,
        ) {
            viewModelScope.launch {
                showToast(context.getString(R.string.retry_started))

                if (!bypassDurationCheck) {
                    val item = queueRepository.findItemById(id)
                    if (item != null) {
                        val settings = settingsRepository.settings.first()
                        val pending =
                            com.nihaltp.sbskip.model.PendingDownload(
                                videoId = com.nihaltp.sbskip.util.YouTubeUrlParser.extractVideoId(item.url) ?: "",
                                url = item.url,
                                title = item.title,
                                thumbnailUrl = item.thumbnailUrl,
                                createdAtEpochMillis = item.createdAtEpochMillis,
                            )
                        val candidates = collectRecentCandidates(pending, settings)
                        val bestCandidate = candidates.maxByOrNull { it.score }
                        if (bestCandidate != null && bestCandidate.score > 50 && bestCandidate.uri != item.localFileUri) {
                            queueRepository.updateLocalFileUri(id, bestCandidate.uri, bestCandidate.relativePath)
                            AppLogger.metadata("Retry: Updated local file URI for id=$id to ${bestCandidate.uri}")
                        }
                    }
                }

                queueRepository.retry(id, bypassDurationCheck)
            }
        }

        fun downloadQueueItemViaNewPipe(item: com.nihaltp.sbskip.model.DownloadQueueItem) {
            if (!uiState.value.isNewPipeInstalled) {
                showToast(context.getString(R.string.newpipe_not_installed))
                return
            }
            val normalizedUrl = com.nihaltp.sbskip.util.YouTubeUrlParser.normalize(item.url) ?: item.url
            launchNewPipe(normalizedUrl)
        }

        fun removeQueueItem(item: com.nihaltp.sbskip.model.DownloadQueueItem) {
            viewModelScope.launch {
                queueRepository.remove(item.id)
                showToast(
                    message = context.getString(R.string.snackbar_item_removed),
                    actionLabel = context.getString(R.string.undo),
                    itemToRestore = item,
                )
            }
        }

        fun undoRemoveQueueItem(item: com.nihaltp.sbskip.model.DownloadQueueItem) {
            viewModelScope.launch {
                val result =
                    queueRepository.enqueue(
                        localFileUri = item.localFileUri,
                        title = item.title,
                        youtubeUrl = item.url,
                        mediaType = item.mediaType,
                        convertVideoToAudio = item.convertVideoToAudio,
                        deleteOriginalVideo = item.deleteOriginalVideo,
                        audioOutputDirUri = item.audioOutputDirUri,
                        videoOutputDirUri = item.videoOutputDirUri,
                        relativePath = item.relativePath,
                    )
                if (result.success) {
                    showToast(context.getString(R.string.snackbar_item_restored))
                } else {
                    showToast(result.message)
                }
            }
        }

        fun consumeSnackbarMessage() {
            _uiState.update { it.copy(toastMessages = emptyList()) }
            //
            _uiState.update { it.copy(toastMessages = emptyList()) }
        }

        fun proceedWithMismatch() {
            val state = uiState.value
            val pending = state.pendingEnqueueData ?: return
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        selectedFileUri = pending.fileUri,
                        selectedFileName = pending.title,
                    )
                }
                queueCurrentItemInternal(force = true)
            }
        }

        fun dismissDurationMismatchDialog() {
            _uiState.update {
                it.copy(
                    showDurationMismatchDialog = false,
                    pendingEnqueueData = null,
                )
            }
        }

        fun dismissWatchlistPromptDialog() {
            _uiState.update {
                it.copy(
                    showWatchlistPromptDialog = false,
                )
            }
        }

        fun cancelConflictDialog() {
            _uiState.update {
                it.copy(
                    showConflictDialog = false,
                    pendingEnqueueData = null,
                )
            }
        }

        fun proceedConflictReplace() {
            val pending = uiState.value.pendingEnqueueData ?: return
            _uiState.update { it.copy(showConflictDialog = false, pendingEnqueueData = null) }
            viewModelScope.launch {
                val hasParams = pending.youtubeUrl.contains("?")
                val separator = if (hasParams) "&" else "?"
                val finalUrl =
                    if (pending.youtubeUrl.isBlank()) {
                        "sbskip://local?overwrite=true"
                    } else {
                        pending.youtubeUrl + separator + "overwrite=true"
                    }

                val result =
                    queueRepository.enqueue(
                        localFileUri = pending.fileUri,
                        title = pending.title,
                        youtubeUrl = finalUrl,
                        mediaType = pending.mediaType,
                        convertVideoToAudio = pending.convertVideoToAudio,
                        deleteOriginalVideo = pending.deleteOriginalVideo,
                        audioOutputDirUri = if (pending.mediaType == MediaType.AUDIO) pending.customFolderUri else null,
                        videoOutputDirUri = if (pending.mediaType == MediaType.VIDEO) pending.customFolderUri else null,
                    )

                val successToast =
                    com.nihaltp.sbskip.model.ToastMessage(
                        message = context.getString(R.string.snackbar_media_enqueued),
                    )
                val failureToast = com.nihaltp.sbskip.model.ToastMessage(message = result.message)
                if (result.success) scheduleToastDismiss(successToast.id) else scheduleToastDismiss(failureToast.id)
                _uiState.update { state ->
                    val filteredPending =
                        if (pending.pendingDownload != null) {
                            state.pendingDownloads.filter { it.videoId != pending.pendingDownload.videoId }
                        } else {
                            state.pendingDownloads.filter { it.url != pending.youtubeUrl }
                        }
                    if (result.success) {
                        state.copy(
                            urlInput = "",
                            selectedFileUri = null,
                            selectedFileName = "",
                            selectedFileMediaType = null,
                            convertVideoToAudio = false,
                            deleteOriginalVideo = true,
                            pendingDownloads = filteredPending,
                            showDurationMismatchDialog = false,
                            customSponsorBlockCategories = null,
                            toastMessages = state.toastMessages + successToast,
                        )
                    } else {
                        state.copy(toastMessages = state.toastMessages + failureToast)
                    }
                }
            }
        }

        fun proceedConflictRename() {
            val pending = uiState.value.pendingEnqueueData ?: return
            _uiState.update { it.copy(showConflictDialog = false, pendingEnqueueData = null) }
            viewModelScope.launch {
                val settings = settingsRepository.settings.first()
                val metadata = downloadStorage.queryMetadata(pending.fileUri)
                val sourceExtension = metadata?.extension ?: "mp4"

                val baseTitle =
                    if (pending.title.endsWith(".$sourceExtension", ignoreCase = true)) {
                        pending.title.substring(0, pending.title.length - sourceExtension.length - 1)
                    } else {
                        pending.title
                    }

                val targetTitle = baseTitle + settings.autoCleanSuffix
                val targetExtension = if (pending.convertVideoToAudio || pending.mediaType == MediaType.AUDIO) "m4a" else sourceExtension

                val uniqueTitle = downloadStorage.getUniqueTitle(targetTitle, targetExtension, pending.mediaType, pending.customFolderUri)

                val hasParams = pending.youtubeUrl.contains("?")
                val separator = if (hasParams) "&" else "?"
                val finalUrl =
                    if (pending.youtubeUrl.isBlank()) {
                        "sbskip://local?noSuffix=true"
                    } else {
                        pending.youtubeUrl + separator + "noSuffix=true"
                    }

                val result =
                    queueRepository.enqueue(
                        localFileUri = pending.fileUri,
                        title = uniqueTitle,
                        youtubeUrl = finalUrl,
                        mediaType = pending.mediaType,
                        convertVideoToAudio = pending.convertVideoToAudio,
                        deleteOriginalVideo = pending.deleteOriginalVideo,
                        audioOutputDirUri = if (pending.mediaType == MediaType.AUDIO) pending.customFolderUri else null,
                        videoOutputDirUri = if (pending.mediaType == MediaType.VIDEO) pending.customFolderUri else null,
                    )

                val successToast =
                    com.nihaltp.sbskip.model.ToastMessage(
                        message = context.getString(R.string.snackbar_media_enqueued),
                    )
                val failureToast = com.nihaltp.sbskip.model.ToastMessage(message = result.message)
                if (result.success) scheduleToastDismiss(successToast.id) else scheduleToastDismiss(failureToast.id)
                _uiState.update { state ->
                    val filteredPending =
                        if (pending.pendingDownload != null) {
                            state.pendingDownloads.filter { it.videoId != pending.pendingDownload.videoId }
                        } else {
                            state.pendingDownloads.filter { it.url != pending.youtubeUrl }
                        }
                    if (result.success) {
                        state.copy(
                            urlInput = "",
                            selectedFileUri = null,
                            selectedFileName = "",
                            selectedFileMediaType = null,
                            convertVideoToAudio = false,
                            deleteOriginalVideo = true,
                            pendingDownloads = filteredPending,
                            showDurationMismatchDialog = false,
                            customSponsorBlockCategories = null,
                            toastMessages = state.toastMessages + successToast,
                        )
                    } else {
                        state.copy(toastMessages = state.toastMessages + failureToast)
                    }
                }
            }
        }

        private suspend fun queueCurrentItemInternal(
            force: Boolean = false,
            customFolderUri: String? = null,
        ) {
            val state = uiState.value
            val fileUri = state.selectedFileUri
            val youtubeUrl = state.urlInput.trim()

            if (fileUri.isNullOrBlank()) {
                if (youtubeUrl.isBlank()) {
                    showToast(context.getString(R.string.snackbar_paste_first))
                    return
                }

                if (!state.isNewPipeInstalled) {
                    showToast(context.getString(R.string.newpipe_not_installed))
                    return
                }

                startDownloadAndCleanInternal(state.convertVideoToAudio, state.deleteOriginalVideo)
                return
            }

            val isConvertOnly = youtubeUrl.isBlank() && state.selectedFileMediaType == MediaType.VIDEO

            val settings = settingsRepository.settings.first()
            val metadata = downloadStorage.queryMetadata(fileUri)
            val isAudioExt =
                metadata?.extension == "mp3" ||
                    metadata?.extension == "m4a" ||
                    metadata?.extension == "aac"
            val mediaType =
                if (isConvertOnly || state.convertVideoToAudio || isAudioExt) {
                    MediaType.AUDIO
                } else {
                    MediaType.VIDEO
                }

            var finalFolderUri = customFolderUri
            var relativePath: String? = null

            if (finalFolderUri == null && !fileUri.isNullOrBlank()) {
                val matched = downloadStorage.getMatchedWatchlistFolder(fileUri)
                if (matched != null) {
                    if (downloadStorage.hasPersistedPermission(matched.folder.uri)) {
                        finalFolderUri = matched.folder.uri
                        relativePath = matched.relativePath
                    } else {
                        _uiState.update {
                            it.copy(
                                showPermissionRevokedDialog = true,
                                revokedWatchlistFolder = matched.folder,
                                pendingEnqueueData =
                                    PendingEnqueueData(
                                        fileUri = fileUri,
                                        title =
                                            state.selectedFileName.ifBlank {
                                                metadata?.title ?: context.getString(
                                                    R.string.imported_file_fallback,
                                                )
                                            },
                                        youtubeUrl = youtubeUrl,
                                        mediaType = mediaType,
                                        convertVideoToAudio = if (isConvertOnly) true else state.convertVideoToAudio,
                                        deleteOriginalVideo = state.deleteOriginalVideo,
                                        customFolderUri = customFolderUri,
                                    ),
                            )
                        }
                        return
                    }
                }
            }

            if (!isConvertOnly) {
                if (youtubeUrl.isBlank()) {
                    showToast(context.getString(R.string.snackbar_paste_first))
                    return
                }

                val videoId = YouTubeUrlParser.extractVideoId(youtubeUrl)
                if (videoId.isNullOrBlank()) {
                    showToast(context.getString(R.string.enter_valid_url))
                    return
                }

                val fileDuration = metadata?.durationSeconds ?: 0L

                if (!force) {
                    _uiState.update { it.copy(isVerifyingDuration = true) }
                    val youtubeDuration = com.nihaltp.sbskip.util.YouTubeDurationFetcher.fetchDuration(videoId)
                    _uiState.update { it.copy(isVerifyingDuration = false) }

                    if (youtubeDuration != null) {
                        val difference = kotlin.math.abs(fileDuration - youtubeDuration)
                        val hasMismatch =
                            if (settings.bypassSmallDurationDifference) {
                                difference > settings.maxDurationDifferenceSeconds
                            } else {
                                difference > 0
                            }
                        if (hasMismatch) {
                            val title =
                                state.selectedFileName.ifBlank {
                                    metadata?.title ?: context.getString(
                                        R.string.imported_file_fallback,
                                    )
                                }
                            _uiState.update {
                                it.copy(
                                    showDurationMismatchDialog = true,
                                    mismatchFileDuration = fileDuration,
                                    mismatchYoutubeDuration = youtubeDuration,
                                    pendingEnqueueData =
                                        com.nihaltp.sbskip.model.PendingEnqueueData(
                                            fileUri = fileUri,
                                            title = title,
                                            youtubeUrl = youtubeUrl,
                                            mediaType = mediaType,
                                        ),
                                )
                            }
                            return
                        }
                    }
                }
            }

            val title = state.selectedFileName.ifBlank { metadata?.title ?: context.getString(R.string.imported_file_fallback) }

            // Conflict check!
            val sourceExtension = metadata?.extension ?: "mp4"
            val baseTitle =
                if (title.endsWith(".$sourceExtension", ignoreCase = true)) {
                    title.substring(0, title.length - sourceExtension.length - 1)
                } else {
                    title
                }

            val targetTitle = baseTitle + settings.autoCleanSuffix
            val targetExtension = if (isConvertOnly || state.convertVideoToAudio || mediaType == MediaType.AUDIO) "m4a" else sourceExtension

            val exists = downloadStorage.checkFileExists(targetTitle, targetExtension, mediaType, finalFolderUri)
            if (exists) {
                _uiState.update {
                    it.copy(
                        showConflictDialog = true,
                        conflictFileName = "$targetTitle.$targetExtension",
                        pendingEnqueueData =
                            PendingEnqueueData(
                                fileUri = fileUri,
                                title = title,
                                youtubeUrl = youtubeUrl,
                                mediaType = mediaType,
                                convertVideoToAudio = if (isConvertOnly) true else state.convertVideoToAudio,
                                deleteOriginalVideo = state.deleteOriginalVideo,
                                customFolderUri = finalFolderUri,
                                relativePath = relativePath,
                            ),
                    )
                }
                return
            }

            var finalUrl =
                if (isConvertOnly) {
                    ""
                } else if (force) {
                    if (youtubeUrl.contains("?")) "$youtubeUrl&bypassDurationCheck=true" else "$youtubeUrl?bypassDurationCheck=true"
                } else {
                    youtubeUrl
                }

            if (finalUrl.isNotBlank() && state.customSponsorBlockCategories != null) {
                val hasParams = finalUrl.contains("?")
                val separator = if (hasParams) "&" else "?"
                finalUrl = finalUrl + separator + "categories=" + state.customSponsorBlockCategories.joinToString(",") { it.name }
            }

            val result =
                queueRepository.enqueue(
                    localFileUri = fileUri,
                    title = title,
                    youtubeUrl = finalUrl,
                    mediaType = mediaType,
                    convertVideoToAudio = if (isConvertOnly) true else state.convertVideoToAudio,
                    deleteOriginalVideo = state.deleteOriginalVideo,
                    audioOutputDirUri = if (mediaType == MediaType.AUDIO) finalFolderUri else null,
                    videoOutputDirUri = if (mediaType == MediaType.VIDEO) finalFolderUri else null,
                    relativePath = relativePath,
                )

            val successToast =
                com.nihaltp.sbskip.model.ToastMessage(
                    message = context.getString(R.string.snackbar_media_enqueued),
                )
            val failureToast = com.nihaltp.sbskip.model.ToastMessage(message = result.message)
            if (result.success) scheduleToastDismiss(successToast.id) else scheduleToastDismiss(failureToast.id)
            _uiState.update { state ->
                if (result.success) {
                    state.copy(
                        urlInput = "",
                        selectedFileUri = null,
                        selectedFileName = "",
                        selectedFileMediaType = null,
                        convertVideoToAudio = false,
                        deleteOriginalVideo = true,
                        pendingDownloads = state.pendingDownloads.filter { it.url != youtubeUrl },
                        showDurationMismatchDialog = false,
                        pendingEnqueueData = null,
                        customSponsorBlockCategories = null,
                        toastMessages = state.toastMessages + successToast,
                    )
                } else {
                    state.copy(toastMessages = state.toastMessages + failureToast)
                }
            }
        }

        private suspend fun startDownloadAndCleanInternal(
            convertVideoToAudio: Boolean,
            deleteOriginalVideo: Boolean,
        ) {
            val state = uiState.value
            val inputUrl = state.urlInput.trim()
            val videoId = YouTubeUrlParser.extractVideoId(inputUrl)
            val playlistId = YouTubeUrlParser.extractPlaylistId(inputUrl)

            if (inputUrl.isBlank() || (videoId.isNullOrBlank() && playlistId.isNullOrBlank())) {
                showToast(context.getString(R.string.enter_valid_url))
                return
            }

            if (!state.isNewPipeInstalled) {
                showToast(context.getString(R.string.newpipe_not_installed))
                return
            }

            _uiState.update { it.copy(isFetchingMetadata = true) }

            // If it has a playlist ID and no video ID, or if we decide to handle playlists primarily
            if (!playlistId.isNullOrBlank() && videoId.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val videos = com.nihaltp.sbskip.util.YouTubePlaylistFetcher.fetchPlaylistVideos(playlistId)
                        if (videos.isEmpty()) {
                            showToast("No videos found in playlist or playlist is private")
                            _uiState.update { it.copy(isFetchingMetadata = false) }
                            return@launch
                        }

                        _uiState.update { st ->
                            st.copy(
                                urlInput = "",
                                isFetchingMetadata = false,
                                customSponsorBlockCategories = null,
                                playlistDownloadState =
                                    com.nihaltp.sbskip.model.PlaylistDownloadState(
                                        playlistId = playlistId,
                                        title = "Playlist",
                                        videos = videos,
                                        currentIndex = 0,
                                        convertVideoToAudio = convertVideoToAudio,
                                        deleteOriginalVideo = deleteOriginalVideo,
                                    ),
                            )
                        }
                    } catch (e: Exception) {
                        showToast("Failed to fetch playlist")
                        _uiState.update { it.copy(isFetchingMetadata = false) }
                    }
                }
                return
            }

            val normalizedUrl = Constants.buildYouTubeWatchUrl(videoId!!)
            val customCategories = state.customSponsorBlockCategories
            val finalUrl =
                if (customCategories != null) {
                    "$normalizedUrl&categories=" + customCategories.joinToString(",") { it.name }
                } else {
                    normalizedUrl
                }

            // Fetch metadata and video duration concurrently
            val metadataDeferred =
                viewModelScope.async {
                    runCatching { fetchYouTubeOEmbed(normalizedUrl) }.getOrElse {
                        YouTubeMetadata(
                            title = state.urlInput.ifBlank { videoId },
                            authorName = null,
                            authorUrl = null,
                            thumbnailUrl = null,
                        )
                    }
                }
            val durationDeferred =
                viewModelScope.async {
                    com.nihaltp.sbskip.util.YouTubeDurationFetcher.fetchDuration(videoId)
                }

            val metadata = metadataDeferred.await()
            val durationSeconds = durationDeferred.await()

            // Compute how long to wait before scanning:
            // clamp(duration * WAIT_FACTOR, MIN_WAIT, MAX_WAIT)
            val waitSeconds: Long =
                if (durationSeconds != null && durationSeconds > 0) {
                    (durationSeconds * DOWNLOAD_WAIT_FACTOR)
                        .toLong()
                        .coerceIn(MIN_DOWNLOAD_WAIT_SECONDS, MAX_DOWNLOAD_WAIT_SECONDS)
                } else {
                    DEFAULT_DOWNLOAD_WAIT_SECONDS
                }

            val now = System.currentTimeMillis()
            val maxExistingTimer = uiState.value.pendingDownloads.mapNotNull { it.estimatedReadyAtEpochMillis }.maxOrNull() ?: now
            val startFrom = maxOf(now, maxExistingTimer)
            val waitMillis = waitSeconds * 1000L
            val estimatedReady = startFrom + waitMillis

            val pendingDownload =
                PendingDownload(
                    videoId = videoId,
                    url = finalUrl,
                    title = metadata.title.orEmpty().ifBlank { videoId },
                    thumbnailUrl = metadata.thumbnailUrl,
                    createdAtEpochMillis = now,
                    convertVideoToAudio = convertVideoToAudio,
                    deleteOriginalVideo = deleteOriginalVideo,
                    estimatedReadyAtEpochMillis = estimatedReady,
                )

            val showPrompt = settingsRepository.settings.first().watchlist.isEmpty()
            _uiState.update { state ->
                state.copy(
                    urlInput = "",
                    pendingDownloads = state.pendingDownloads + pendingDownload,
                    isFetchingMetadata = false,
                    customSponsorBlockCategories = null,
                    showWatchlistPromptDialog = showPrompt,
                )
            }

            launchNewPipe(normalizedUrl)

            // Delay auto-detect until the estimated download time has elapsed.
            // Store the Job so it can be cancelled if the user taps "Search now".
            val detectJob =
                viewModelScope.launch {
                    val delayMillis = estimatedReady - System.currentTimeMillis()
                    if (delayMillis > 0) {
                        kotlinx.coroutines.delay(delayMillis)
                    }
                    // Clear the countdown before running detection
                    _uiState.update { st ->
                        st.copy(
                            pendingDownloads =
                                st.pendingDownloads.map {
                                    if (it.videoId == pendingDownload.videoId) {
                                        it.copy(estimatedReadyAtEpochMillis = null)
                                    } else {
                                        it
                                    }
                                },
                        )
                    }
                    autoDetectAndCleanInternal(pendingDownload.copy(estimatedReadyAtEpochMillis = null))
                }
            pendingDetectJobs[videoId] = detectJob
        }

        private suspend fun autoDetectAndCleanInternal(pendingDownload: PendingDownload) {
            AppLogger.metadata(
                "AutoDetect: Started for videoId=${pendingDownload.videoId} " +
                    "title='${pendingDownload.title}' url=${pendingDownload.url} " +
                    "createdTime=${pendingDownload.createdAtEpochMillis}",
            )
            _uiState.update { state ->
                state.copy(
                    pendingDownloads =
                        state.pendingDownloads.map {
                            if (it.videoId == pendingDownload.videoId) {
                                it.copy(
                                    isDetectingFile = true,
                                    detectedFile = null,
                                    detectedFileName = null,
                                )
                            } else {
                                it
                            }
                        },
                )
            }

            val settings = settingsRepository.settings.first()
            val candidates =
                withContext(Dispatchers.IO) {
                    collectRecentCandidates(pendingDownload, settings)
                }
            val bestCandidate = candidates.maxByOrNull { it.score }

            if (bestCandidate == null || bestCandidate.score < MIN_CONFIDENCE_SCORE) {
                AppLogger.metadata(
                    "AutoDetect: Finished. No matching candidate found above " +
                        "threshold of $MIN_CONFIDENCE_SCORE " +
                        "(best candidate: ${bestCandidate?.let { "score=${it.score} uri=${it.uri}" } ?: "none"}) " +
                        "for videoId=${pendingDownload.videoId}",
                )
                val noMatchToast =
                    com.nihaltp.sbskip.model.ToastMessage(
                        message = context.getString(R.string.no_matching_download_found),
                    )
                scheduleToastDismiss(noMatchToast.id)
                _uiState.update { state ->
                    state.copy(
                        pendingDownloads =
                            state.pendingDownloads.map {
                                if (it.videoId == pendingDownload.videoId) it.copy(isDetectingFile = false) else it
                            },
                        toastMessages = state.toastMessages + noMatchToast,
                    )
                }
                return
            }

            val detectedName =
                withContext(Dispatchers.IO) {
                    readDisplayName(bestCandidate.uri) ?: bestCandidate.fallbackName
                }

            AppLogger.metadata(
                "AutoDetect: Finished. Winner detected: score=${bestCandidate.score} " +
                    "name='$detectedName' uri=${bestCandidate.uri} " +
                    "for videoId=${pendingDownload.videoId}",
            )
            val matchToast =
                com.nihaltp.sbskip.model.ToastMessage(
                    message = context.getString(R.string.found_matching_file, bestCandidate.score),
                )
            scheduleToastDismiss(matchToast.id)
            _uiState.update { state ->
                state.copy(
                    pendingDownloads =
                        state.pendingDownloads.map {
                            if (it.videoId == pendingDownload.videoId) {
                                it.copy(
                                    isDetectingFile = false,
                                    detectedFile =
                                        DetectedFile(
                                            uri = bestCandidate.uri,
                                            score = bestCandidate.score,
                                            relativePath = bestCandidate.relativePath,
                                            folderUri = bestCandidate.folderUri,
                                        ),
                                    detectedFileName = detectedName,
                                )
                            } else {
                                it
                            }
                        },
                    toastMessages = state.toastMessages + matchToast,
                )
            }

            if (settings.autoStartCleaning) {
                val updatedPendingDownload =
                    pendingDownload.copy(
                        detectedFile =
                            DetectedFile(
                                uri = bestCandidate.uri,
                                score = bestCandidate.score,
                                relativePath = bestCandidate.relativePath,
                                folderUri = bestCandidate.folderUri,
                            ),
                        detectedFileName = detectedName,
                    )
                confirmDetectedFile(updatedPendingDownload)
            }
        }

        private suspend fun fetchYouTubeOEmbed(videoUrl: String): YouTubeMetadata =
            withContext(Dispatchers.IO) {
                val oEmbedUrl =
                    videoUrl.toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.scheme("https")
                        ?.host("www.youtube.com")
                        ?.encodedPath("/oembed")
                        ?.addQueryParameter("url", videoUrl)
                        ?.addQueryParameter("format", "json")
                        ?.build()
                        ?: throw IOException(context.getString(R.string.unable_fetch_metadata))

                val request = Request.Builder().url(oEmbedUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("oEmbed request failed: ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = json.decodeFromString(YouTubeOEmbedResponse.serializer(), body)
                    val videoId = YouTubeUrlParser.extractVideoId(videoUrl)
                    val customThumbnailUrl = videoId?.let { Constants.buildYouTubeThumbnailUrl(it) } ?: parsed.thumbnailUrl
                    YouTubeMetadata(
                        title = parsed.title,
                        authorName = parsed.authorName,
                        authorUrl = parsed.authorUrl,
                        thumbnailUrl = customThumbnailUrl,
                    )
                }
            }

        private fun launchNewPipe(youtubeUrl: String) {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
                    setPackage(NEWPIPE_PACKAGE_NAME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                val failToast =
                    com.nihaltp.sbskip.model.ToastMessage(
                        message = context.getString(R.string.newpipe_launch_failed),
                    )
                scheduleToastDismiss(failToast.id)
                _uiState.update { state ->
                    state.copy(toastMessages = state.toastMessages + failToast)
                }
            }
        }

        private suspend fun collectRecentCandidates(
            pendingDownload: PendingDownload,
            settings: com.nihaltp.sbskip.model.AppSettings,
        ): List<DetectedCandidate> =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val candidates = mutableListOf<DetectedCandidate>()

                AppLogger.metadata("AutoDetect: Scanning watchlist directories for files...")

                // 2. Query watchlist folders
                settings.watchlist.forEach { folder ->
                    val folderUriStr = folder.uri
                    val relativePathHint = folder.path
                    if (folderUriStr.isNotEmpty() && folderUriStr.startsWith("content://")) {
                        try {
                            AppLogger.metadata("AutoDetect: Direct SAF scanning watchlist directory URI: $folderUriStr")
                            val folderUri = Uri.parse(folderUriStr)
                            val dirFile = DocumentFile.fromTreeUri(context, folderUri)
                            if (dirFile != null && dirFile.exists() && dirFile.isDirectory) {
                                val queue = ArrayDeque<Pair<androidx.documentfile.provider.DocumentFile, String>>()
                                queue.add(Pair(dirFile, ""))
                                var fileCount = 0

                                while (queue.isNotEmpty()) {
                                    val (currentDir, currentRelPath) = queue.removeFirst()
                                    val files = currentDir.listFiles()

                                    files.forEach filesLoop@{ file ->
                                        if (file.isDirectory) {
                                            val dirName = file.name
                                            if (dirName != null) {
                                                val nextRelPath = "$currentRelPath$dirName/"
                                                AppLogger.metadata("AutoDetect: Scanning directory: $nextRelPath")
                                                queue.add(Pair(file, nextRelPath))
                                            }
                                        } else if (file.isFile && file.name != null) {
                                            fileCount++
                                            val displayName = file.name!!
                                            val timestampMillis = file.lastModified()

                                            val actualRelPath = "$relativePathHint$currentRelPath"

                                            val score =
                                                scoreCandidate(
                                                    pendingDownload = pendingDownload,
                                                    displayName = displayName,
                                                    relativePath = actualRelPath,
                                                    durationSeconds = null,
                                                    timestampMillis = if (timestampMillis > 0) timestampMillis else now,
                                                    settings = settings,
                                                )

                                            if (score > 50) {
                                                AppLogger.metadata(
                                                    "AutoDetect: Scored SAF watchlist file " +
                                                        "displayName='$displayName' uri=${file.uri} score=$score",
                                                )
                                            }
                                            candidates.add(
                                                DetectedCandidate(
                                                    uri = file.uri.toString(),
                                                    score = score,
                                                    fallbackName = displayName,
                                                    relativePath = actualRelPath,
                                                    folderUri = folderUriStr,
                                                ),
                                            )
                                        }
                                    }
                                }
                                AppLogger.metadata("AutoDetect: SAF watchlist directory recursive scan found $fileCount files.")
                            } else {
                                AppLogger.metadata("AutoDetect: Direct SAF watchlist directory not found/resolved for URI: $folderUriStr")
                            }
                        } catch (e: Exception) {
                            AppLogger.error("MainViewModel", e, "AutoDetect: Failed to scan SAF watchlist directory: $folderUriStr")
                        }
                    }
                }

                candidates.sortedByDescending { it.score }
            }

        private fun scoreCandidate(
            pendingDownload: PendingDownload,
            displayName: String,
            relativePath: String,
            durationSeconds: Long?,
            timestampMillis: Long,
            settings: com.nihaltp.sbskip.model.AppSettings,
        ): Int {
            val normalizedTitle = normalizeText(pendingDownload.title)
            val normalizedCandidate = normalizeText(stripExtension(displayName))
            var score = 0

            val titleSim = titleSimilarityScore(normalizedTitle, normalizedCandidate)
            score += titleSim

            var hasVideoId = false
            if (pendingDownload.videoId.isNotBlank() && displayName.contains(pendingDownload.videoId, ignoreCase = true)) {
                score += 20
                hasVideoId = true
            }

            var folderMatched = false
            if (settings.watchlist.any { folderHintMatches(relativePath, it.path) }) {
                score += 18
                folderMatched = true
            }

            val ageMillis = kotlin.math.abs(timestampMillis - pendingDownload.createdAtEpochMillis)
            val ageBonus =
                when {
                    ageMillis <= 2 * 60 * 1000L -> 22
                    ageMillis <= 5 * 60 * 1000L -> 18
                    ageMillis <= 10 * 60 * 1000L -> 12
                    ageMillis <= 15 * 60 * 1000L -> 8
                    else -> 0
                }
            score += ageBonus

            var durationBonus = 0
            if (durationSeconds != null && pendingDownload.title.isNotBlank()) {
                // We do not have the exact expected duration from oEmbed, but very short files are unlikely to match long YouTube titles.
                if (durationSeconds >= 30L) {
                    score += 4
                    durationBonus = 4
                }
            }

            val finalScore = score.coerceIn(0, 100)

            if (finalScore > 50) {
                AppLogger.metadata(
                    "AutoDetect: Scored candidate displayName='$displayName' path='$relativePath' " +
                        "finalScore=$finalScore [Breakdown: titleSim=$titleSim " +
                        "videoIdBonus=${if (hasVideoId) 20 else 0} " +
                        "folderBonus=${if (folderMatched) 18 else 0} " +
                        "ageBonus=$ageBonus durationBonus=$durationBonus]",
                )
            }

            return finalScore
        }

        private fun titleSimilarityScore(
            expectedTitle: String,
            candidateTitle: String,
        ): Int {
            if (expectedTitle.isBlank() || candidateTitle.isBlank()) return 0

            if (candidateTitle == expectedTitle) {
                return 60
            }

            if (candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle)) {
                return 48
            }

            val expectedWords = expectedTitle.split(WORD_SPLIT_REGEX).filter { it.isNotBlank() }.toSet()
            val candidateWords = candidateTitle.split(WORD_SPLIT_REGEX).filter { it.isNotBlank() }.toSet()
            if (expectedWords.isEmpty() || candidateWords.isEmpty()) {
                return 0
            }

            val overlap = expectedWords.intersect(candidateWords).size.toDouble()
            val total = expectedWords.union(candidateWords).size.toDouble()
            val jaccard = overlap / total
            return (jaccard * 60.0).toInt()
        }

        private fun folderHintMatches(
            relativePath: String,
            folderHint: String,
        ): Boolean {
            if (folderHint.isBlank()) return false
            val normalizedPath = normalizeText(relativePath)
            val normalizedHint = normalizeText(folderHint)
            return normalizedPath.contains(normalizedHint)
        }

        private fun normalizeText(value: String): String {
            return value
                .lowercase(Locale.ROOT)
                .replace(WORD_SPLIT_REGEX, " ")
                .trim()
        }

        private fun stripExtension(displayName: String): String {
            val dotIndex = displayName.lastIndexOf('.')
            return if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
        }

        private fun readDisplayName(uriString: String): String? {
            val uri = Uri.parse(uriString)
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
            return null
        }

        private data class DetectedCandidate(
            val uri: String,
            val score: Int,
            val fallbackName: String,
            val relativePath: String? = null,
            val folderUri: String? = null,
        )

        @Serializable
        private data class YouTubeOEmbedResponse(
            val title: String? = null,
            @SerialName("author_name") val authorName: String? = null,
            @SerialName("author_url") val authorUrl: String? = null,
            @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        )

        private data class YouTubeMetadata(
            val title: String?,
            val authorName: String?,
            val authorUrl: String?,
            val thumbnailUrl: String?,
        )

        fun showToast(
            message: String,
            actionLabel: String? = null,
            itemToRestore: com.nihaltp.sbskip.model.DownloadQueueItem? = null,
        ) {
            val toast = com.nihaltp.sbskip.model.ToastMessage(message = message, actionLabel = actionLabel, itemToRestore = itemToRestore)
            _uiState.update { it.copy(toastMessages = it.toastMessages + toast) }
            scheduleToastDismiss(toast.id)
        }

        private fun scheduleToastDismiss(id: String) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(4000)
                dismissToast(id)
            }
        }

        fun dismissToast(id: String) {
            _uiState.update { it.copy(toastMessages = it.toastMessages.filter { t -> t.id != id }) }
        }

        companion object {
            private const val NEWPIPE_PACKAGE_NAME = "org.schabi.newpipe"
            private const val RECENT_WINDOW_MILLIS = 15 * 60 * 1000L
            private const val MIN_CONFIDENCE_SCORE = 55
            private const val MAX_SCAN_RESULTS_PER_COLLECTION = 100
            private val WORD_SPLIT_REGEX = Regex("[^a-z0-9]+")

            // How long to wait for a NewPipe download before running auto-detect.
            // Wait = clamp(videoDurationSeconds * FACTOR, MIN, MAX)
            private const val DOWNLOAD_WAIT_FACTOR = 0.15
            private const val MIN_DOWNLOAD_WAIT_SECONDS = 30L
            private const val MAX_DOWNLOAD_WAIT_SECONDS = 300L
            private const val DEFAULT_DOWNLOAD_WAIT_SECONDS = 60L // fallback if duration unknown
        }
    }
