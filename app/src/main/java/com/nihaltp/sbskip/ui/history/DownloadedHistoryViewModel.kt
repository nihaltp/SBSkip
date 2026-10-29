package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.ExportData
import com.nihaltp.sbskip.model.ImportResult
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.FolderImportQueueItem
import com.nihaltp.sbskip.util.ImportManager
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import com.nihaltp.sbskip.workers.ImportEnrichmentScheduler
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
        private val importManager: ImportManager,
        private val importEnrichmentScheduler: ImportEnrichmentScheduler,
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
         * download queue row is created and no Worker is scheduled. Local files named by the backup
         * are checked first, so a video whose file is missing is still listed but stays unverified.
         * A failure on one video never aborts the rest of the import.
         *
         * @return what was newly tracked, what was already known, and which videos failed.
         */
        suspend fun importVideos(result: ImportResult): ImportOutcome {
            val videos =
                when (result) {
                    is ImportResult.JsonSuccess -> result.exportData.toImportedVideos()
                    is ImportResult.Success -> result.toImportedVideos()
                    is ImportResult.Failure -> return ImportOutcome(errors = listOf(result.error))
                }

            var imported = 0
            var skipped = 0
            val errors = mutableListOf<String>()
            for (video in videos) {
                try {
                    if (trackImportedVideo(video)) imported++ else skipped++
                } catch (e: Exception) {
                    AppLogger.error("DownloadedHistory", e, "Import failed for video ${video.videoId}")
                    errors += video.videoId
                }
            }
            return ImportOutcome(imported = imported, skipped = skipped, errors = errors).also {
                if (imported > 0) {
                    importEnrichmentScheduler.schedule()
                }
            }
        }

        /**
         * Inserts an imported video, skipping one that is already tracked. A video is tracked by
         * its YouTube id, so re-importing the same id is a no-op.
         *
         * @return true when a new video was tracked.
         */
        private suspend fun trackImportedVideo(video: ImportedVideo): Boolean {
            val existing = downloadedVideoRepository.findByVideoId(video.videoId)
            if (existing != null) {
                return false
            }

            downloadedVideoRepository.insert(
                DownloadedVideoEntity(
                    videoId = video.videoId,
                    url = video.url,
                    title = video.title,
                    author = video.author,
                    thumbnailUrl = video.thumbnailUrl ?: Constants.buildYouTubeThumbnailUrl(video.videoId),
                    durationSeconds = null,
                    fileSize = null,
                    mediaType = MediaType.VIDEO.name,
                    needsEnrichment = true,
                ),
            )
            return true
        }
    }

/** What an import changed: newly tracked videos, already known videos, and videos that failed. */
data class ImportOutcome(
    val imported: Int = 0,
    val skipped: Int = 0,
    val errors: List<String> = emptyList(),
)

/**
 * A video parsed from an import, holding only the metadata that the import actually provides. The
 * local files are absent for plain text imports and for backups without a download history.
 */
private data class ImportedVideo(
    val videoId: String,
    val url: String = Constants.buildYouTubeWatchUrl(videoId),
    val title: String = videoId,
    val author: String? = null,
    val thumbnailUrl: String? = null,
)

/** Plain text imports only carry video ids, so there is nothing else to map. */
private fun ImportResult.Success.toImportedVideos(): List<ImportedVideo> =
    items
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .map(::ImportedVideo)

/**
 * Maps an imported backup to the videos it describes. Entries sharing a video id are merged, so a
 * backup that lists the same video twice yields only one row — the import does not carry local
 * file paths anymore.
 */
private fun ExportData.toImportedVideos(): List<ImportedVideo> {
    val videos = LinkedHashMap<String, ImportedVideo>()
    items.forEachIndexed { index, item ->
        val videoId = item.videoId.ifBlank { YouTubeUrlParser.cleanVideoId(item.url).orEmpty() }
        if (videoId.isBlank()) {
            return@forEachIndexed
        }

        val url = item.url.ifBlank { Constants.buildYouTubeWatchUrl(videoId) }
        val existing = videos[videoId]
        videos[videoId] =
            if (existing == null) {
                ImportedVideo(
                    videoId = videoId,
                    url = url,
                    title = item.title?.takeIf(String::isNotBlank) ?: videoId,
                    author = item.author?.takeIf(String::isNotBlank),
                    thumbnailUrl = item.thumbnailUrl?.takeIf(String::isNotBlank),
                )
            } else {
                existing
            }
    }
    return videos.values.toList()
}
