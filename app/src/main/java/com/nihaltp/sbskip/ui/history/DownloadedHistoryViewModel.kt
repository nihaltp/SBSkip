package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.ExportData
import com.nihaltp.sbskip.model.ImportResult
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadedHistoryViewModel
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
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
         * Tracks the videos of an imported clipboard or file backup in the downloaded history.
         *
         * Importing only writes `downloaded_videos`: it never enqueues download work, so no
         * download queue row is created and no Worker is scheduled. A video that is already
         * tracked is never inserted twice; a local file known from the backup is added to the
         * existing entry instead.
         *
         * @return the number of videos that were newly tracked.
         */
        suspend fun importVideos(result: ImportResult): Int {
            val importedVideos =
                when (result) {
                    is ImportResult.JsonSuccess -> result.exportData.toImportedVideos()
                    is ImportResult.Success -> result.items.map { videoId -> ImportedVideo(videoId) }
                    is ImportResult.Failure -> return 0
                }

            var importedCount = 0
            for (video in importedVideos) {
                if (trackImportedVideo(video)) {
                    importedCount++
                }
            }
            return importedCount
        }

        /**
         * Inserts an imported video, or attaches its local file to the already tracked video.
         *
         * @return true when a new video was tracked.
         */
        private suspend fun trackImportedVideo(video: ImportedVideo): Boolean {
            val existing = downloadedVideoRepository.findByVideoId(video.videoId)
            val outputPath = video.outputPath
            if (existing != null) {
                // Already tracked: at most its local file is new, which is not a new video.
                if (outputPath != null && !existing.outputPaths.contains(outputPath)) {
                    downloadedVideoRepository.addOutputPath(video.videoId, outputPath)
                }
                return false
            }

            downloadedVideoRepository.insert(
                DownloadedVideoEntity(
                    videoId = video.videoId,
                    url = video.url,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = null,
                    outputPaths = listOfNotNull(outputPath),
                    fileSize = null,
                    mediaType = MediaType.VIDEO.name,
                ),
            )
            return true
        }
    }

/**
 * A video parsed from an import, holding only the metadata that the import actually provides. The
 * local file is absent for plain text imports and for backups without a download history.
 */
private data class ImportedVideo(
    val videoId: String,
    val url: String = Constants.buildYouTubeWatchUrl(videoId),
    val title: String = videoId,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val outputPath: String? = null,
)

/**
 * Maps an imported backup to the videos it describes. The local file of an export item is looked up
 * in the backup's download history, first by the item URL and then by its watch URL.
 */
private fun ExportData.toImportedVideos(): List<ImportedVideo> {
    val historyByUrl = downloadHistory.orEmpty().associateBy { it.videoUrl }
    return items
        .mapNotNull { item ->
            val videoId = item.videoId.ifBlank { YouTubeUrlParser.cleanVideoId(item.url).orEmpty() }
            if (videoId.isBlank()) {
                return@mapNotNull null
            }

            val url = item.url.ifBlank { Constants.buildYouTubeWatchUrl(videoId) }
            val videoPath =
                historyByUrl[url]?.videoPath?.takeIf { it.isNotBlank() }
                    ?: historyByUrl[Constants.buildYouTubeWatchUrl(videoId)]
                        ?.videoPath
                        ?.takeIf { it.isNotBlank() }

            ImportedVideo(
                videoId = videoId,
                url = url,
                title = item.title?.takeIf { it.isNotBlank() } ?: videoId,
                author = item.author?.takeIf { it.isNotBlank() },
                thumbnailUrl = item.thumbnailUrl?.takeIf { it.isNotBlank() },
                outputPath = videoPath,
            )
        }.distinctBy { it.videoId }
}
