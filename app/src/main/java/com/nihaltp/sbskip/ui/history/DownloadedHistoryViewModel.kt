package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import com.nihaltp.sbskip.util.FolderImportQueueItem
import com.nihaltp.sbskip.util.ImportManager
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadedHistoryViewModel
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
        private val queueRepository: QueueRepository,
        private val settingsRepository: SettingsRepository,
        private val ffmpegMetadataReader: FfmpegMetadataReader,
        private val importManager: ImportManager,
        application: Application,
    ) : AndroidViewModel(application) {
        /**
         * Delegates to the process-lifetime ImportManager so folder imports survive
         * navigation away from the Downloaded History screen.
         */
        val isImporting: StateFlow<Boolean> = importManager.isImporting
        val importProgressMessage: StateFlow<String?> = importManager.importProgressMessage
        val importQueue: StateFlow<List<FolderImportQueueItem>> =
            importManager.importQueue

        val videos: StateFlow<List<DownloadedVideoEntity>> =
            downloadedVideoRepository.observeDownloadedVideos
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        /** Live case-insensitive filter over title, author, URL, and extracted video ID. */
        val filteredVideos: StateFlow<List<DownloadedVideoEntity>> =
            combine(videos, _searchQuery) { list, query ->
                if (query.isBlank()) {
                    list
                } else {
                    val needle = query.trim()
                    list.filter { video ->
                        val videoId = YouTubeUrlParser.extractVideoId(video.url)
                        videoId?.contains(needle, ignoreCase = true) == true ||
                            video.url.contains(needle, ignoreCase = true) ||
                            video.title.contains(needle, ignoreCase = true) ||
                            video.author?.contains(needle, ignoreCase = true) == true
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        val settings: StateFlow<com.nihaltp.sbskip.model.AppSettings> =
            settingsRepository.settings
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = com.nihaltp.sbskip.model.AppSettings(),
                )

        private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

        private val _selectedCount = MutableStateFlow(0)
        val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

        private fun updateSelected(newSet: Set<Long>) {
            _selectedIds.value = newSet
            _selectedCount.value = newSet.size
        }

        fun isSelected(id: Long): Boolean = _selectedIds.value.contains(id)

        fun toggleSelection(id: Long) {
            val current = _selectedIds.value
            val updated =
                if (current.contains(id)) {
                    current - id
                } else {
                    current + id
                }
            updateSelected(updated)
        }

        fun toggleSelectAll(videoIds: List<Long>) {
            val current = _selectedIds.value
            val allSelected = videoIds.isNotEmpty() && videoIds.all { current.contains(it) }
            val updated =
                if (allSelected) {
                    emptySet()
                } else {
                    videoIds.toSet()
                }
            updateSelected(updated)
        }

        fun clearSelection() {
            updateSelected(emptySet())
        }

        fun deleteVideo(id: Long) {
            updateSelected(_selectedIds.value - id)
            viewModelScope.launch {
                downloadedVideoRepository.deleteById(id)
            }
        }

        fun deleteSelectedVideos() {
            val ids = _selectedIds.value.toList()
            updateSelected(emptySet())
            viewModelScope.launch {
                for (id in ids) {
                    downloadedVideoRepository.deleteById(id)
                }
            }
        }

        fun clearHistory() {
            updateSelected(emptySet())
            viewModelScope.launch {
                downloadedVideoRepository.deleteAll()
            }
        }

        /**
         * Enqueues a folder for import on the process-lifetime ImportManager.
         * Navigating away no longer cancels the scan.
         */
        fun scanAndImportFolder(
            context: android.content.Context,
            treeUri: Uri,
        ) {
            importManager.scanAndImportFolder(treeUri)
        }

        /**
         * Removes a PENDING or FAILED queue item on the ImportManager.
         */
        fun removeQueueItem(id: Long) {
            importManager.removeQueueItem(id)
        }

        /**
         * Removes a finished (COMPLETED/FAILED) item from the active import queue. The screen
         * calls this once the result has been announced, so the queue only keeps the folders
         * that are still pending or being scanned.
         */
        fun dismissCompletedItem(id: Long) {
            importManager.dismissCompletedItem(id)
        }

        /**
         * Enqueues a downloaded video for SponsorBlock cleaning.
         */
        suspend fun enqueueDownloadedVideo(
            videoId: String,
            youtubeUrl: String,
            title: String,
            settings: com.nihaltp.sbskip.model.AppSettings,
        ): Boolean {
            val existingVideoIds =
                queueRepository.observeQueue().first()
                    .mapNotNull { YouTubeUrlParser.extractVideoId(it.url) }
                    .toSet()

            if (videoId in existingVideoIds) {
                return false
            }

            val mediaType =
                if (settings.defaultConvertVideoToAudio) {
                    com.nihaltp.sbskip.model.MediaType.AUDIO
                } else {
                    com.nihaltp.sbskip.model.MediaType.VIDEO
                }

            val result =
                queueRepository.enqueue(
                    localFileUri = "", // Will be auto-detected
                    title = title,
                    youtubeUrl = youtubeUrl,
                    mediaType = mediaType,
                    convertVideoToAudio = settings.defaultConvertVideoToAudio,
                    deleteOriginalVideo = settings.defaultDeleteOriginalVideo,
                    audioOutputDirUri = if (mediaType == com.nihaltp.sbskip.model.MediaType.AUDIO) null else null,
                    videoOutputDirUri = if (mediaType == com.nihaltp.sbskip.model.MediaType.VIDEO) null else null,
                    relativePath = "",
                )
            return result.success
        }

        /**
         * Enqueues multiple downloaded videos for SponsorBlock cleaning.
         */
        suspend fun enqueueVideoIds(
            videoIds: List<String>,
            settings: com.nihaltp.sbskip.model.AppSettings,
        ): Int {
            val existingVideoIds =
                queueRepository.observeQueue().first()
                    .mapNotNull { YouTubeUrlParser.extractVideoId(it.url) }
                    .toSet()

            val newVideoIds = videoIds.filter { !existingVideoIds.contains(it) }
            val duplicateCount = videoIds.size - newVideoIds.size

            if (newVideoIds.isEmpty()) {
                return 0
            }

            var enqueuedCount = 0

            newVideoIds.forEach { videoId ->
                val youtubeUrl = Constants.buildYouTubeWatchUrl(videoId)
                val result =
                    enqueueDownloadedVideo(
                        videoId = videoId,
                        youtubeUrl = youtubeUrl,
                        title = videoId,
                        settings = settings,
                    )
                if (result) enqueuedCount++
            }

            return enqueuedCount
        }
    }
