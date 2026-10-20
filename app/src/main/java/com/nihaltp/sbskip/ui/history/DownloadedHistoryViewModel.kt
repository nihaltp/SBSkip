package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.Constants
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import com.nihaltp.sbskip.util.FolderScanner
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FolderImportStatus {
    PENDING,
    SCANNING,
    COMPLETED,
    FAILED,
}

data class FolderImportQueueItem(
    val id: Long,
    val treeUri: Uri,
    val folderName: String?,
    val status: FolderImportStatus,
    val importedCount: Int,
    val errorMessage: String?,
)

@HiltViewModel
class DownloadedHistoryViewModel
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
        private val ffmpegMetadataReader: FfmpegMetadataReader,
        application: Application,
    ) : AndroidViewModel(application) {
        private val _isImporting = MutableStateFlow(false)
        val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

        private val _importProgressMessage = MutableStateFlow<String?>(null)
        val importProgressMessage: StateFlow<String?> = _importProgressMessage.asStateFlow()

        private val _importQueue = MutableStateFlow(emptyList<FolderImportQueueItem>())
        val importQueue: StateFlow<List<FolderImportQueueItem>> = _importQueue.asStateFlow()

        private var nextQueueId = 0L

        private fun nextId(): Long {
            nextQueueId++
            return nextQueueId
        }

        val videos: StateFlow<List<DownloadedVideoEntity>> =
            downloadedVideoRepository.observeDownloadedVideos
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
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

        private val contextById = mutableMapOf<Long, Context>()

        fun scanAndImportFolder(
            context: Context,
            treeUri: Uri,
        ) {
            viewModelScope.launch {
                // Take persistable URI permission at enqueue time so the permission is held
                // until the folder is actually scanned.
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )

                val folderName = deriveFolderName(context, treeUri)
                val item =
                    FolderImportQueueItem(
                        id = nextId(),
                        treeUri = treeUri,
                        folderName = folderName,
                        status = FolderImportStatus.PENDING,
                        importedCount = 0,
                        errorMessage = null,
                    )

                contextById[item.id] = context
                _importQueue.value = _importQueue.value + item

                // If no scan is currently running, kick off the queue processor.
                if (!_isImporting.value) {
                    processQueue()
                }
            }
        }

        /**
         * Processes the import queue one folder at a time. Only one FolderScanner collection
         * runs at a time; the next PENDING item is started automatically after the current
         * scan completes (success or failure).
         */
        private suspend fun processQueue() {
            val pending = _importQueue.value.firstOrNull { it.status == FolderImportStatus.PENDING }
            if (pending == null) return

            val scanningItem = pending.copy(status = FolderImportStatus.SCANNING)
            _importQueue.value =
                _importQueue.value.map { if (it.id == pending.id) scanningItem else it }

            _isImporting.value = true
            _importProgressMessage.value = null

            val scanContext =
                contextById[pending.id] ?: run {
                    _importQueue.value =
                        _importQueue.value.map {
                            if (it.id == pending.id) {
                                scanningItem.copy(
                                    status = FolderImportStatus.FAILED,
                                    errorMessage = "Context no longer available",
                                )
                            } else {
                                it
                            }
                        }
                    _isImporting.value = false
                    _importProgressMessage.value = null
                    processQueue()
                    return
                }

            try {
                withContext(Dispatchers.IO) {
                    val count = scanFolderItem(scanningItem, scanContext)
                    _importQueue.value =
                        _importQueue.value.map {
                            if (it.id == scanningItem.id) {
                                scanningItem.copy(
                                    status = FolderImportStatus.COMPLETED,
                                    importedCount = count,
                                )
                            } else {
                                it
                            }
                        }
                    AppLogger.metadata(
                        "ImportFolder: Completed queue item ${scanningItem.folderName} ($count imported)",
                    )
                }
            } catch (e: Exception) {
                _importQueue.value =
                    _importQueue.value.map {
                        if (it.id == scanningItem.id) {
                            scanningItem.copy(status = FolderImportStatus.FAILED, errorMessage = e.message)
                        } else {
                            it
                        }
                    }
                AppLogger.metadata("ImportFolder: Failed queue item ${scanningItem.folderName}: ${e.message}")
            } finally {
                contextById.remove(scanningItem.id)
                _isImporting.value = false
                _importProgressMessage.value = null
                // Auto-start the next PENDING folder, if any.
                processQueue()
            }
        }

        /**
         * Per-folder scan logic (extracted so it can be reused for each queued item).
         * Returns the number of newly imported videos.
         */
        private suspend fun scanFolderItem(
            item: FolderImportQueueItem,
            context: Context,
        ): Int {
            val cacheDir = context.cacheDir
            val contentResolver = context.contentResolver
            var importedCount = 0

            FolderScanner.scanMediaFiles(context, item.treeUri).collect { fileUri ->
                try {
                    val fileName = fileUri.lastPathSegment ?: "unknown"
                    _importProgressMessage.value = fileName

                    val metadata =
                        ffmpegMetadataReader.readMetadata(contentResolver, fileUri, cacheDir)
                    val youtubeId = metadata.youtubeId
                    // Sanitize the youtubeId: strip query parameters (e.g. "?overwrite=true")
                    // and keep only a clean 11-character YouTube video ID before storing it.
                    val cleanYoutubeId = youtubeId?.let { YouTubeUrlParser.cleanVideoId(it) }

                    if (cleanYoutubeId != null) {
                        // Duplicate detection:
                        // Check if a video with this videoId or this outputPath already exists in Room
                        val existingById = downloadedVideoRepository.findByVideoId(cleanYoutubeId)
                        val existingByPath =
                            downloadedVideoRepository.findByOutputPath(fileUri.toString())

                        if (existingById == null && existingByPath == null) {
                            val entity =
                                DownloadedVideoEntity(
                                    videoId = cleanYoutubeId,
                                    url =
                                        metadata.youtubeUrl
                                            ?: Constants.buildYouTubeWatchUrl(cleanYoutubeId),
                                    title = metadata.youtubeTitle ?: fileName,
                                    author = metadata.authorName,
                                    thumbnailUrl =
                                        Constants.buildYouTubeThumbnailUrl(cleanYoutubeId),
                                    durationSeconds = null,
                                    outputPath = fileUri.toString(),
                                    fileSize = metadata.fileSize,
                                    mediaType = mediaTypeForUri(fileUri),
                                )
                            downloadedVideoRepository.insert(entity)
                            importedCount++
                            AppLogger.metadata(
                                "ImportFolder: Imported video $cleanYoutubeId ($fileName)",
                            )
                        } else {
                            AppLogger.metadata("ImportFolder: Skipping duplicate $cleanYoutubeId ($fileName)")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.metadata("ImportFolder: Error probing $fileUri: ${e.message}")
                }
            }

            return importedCount
        }

        /**
         * Removes a PENDING or FAILED queue item (e.g. user-initiated cancel). Items that are
         * COMPLETED or currently SCANNING are left in place to preserve history.
         */
        fun removeQueueItem(id: Long) {
            _importQueue.value =
                _importQueue.value.filterNot {
                    it.id == id && it.status in setOf(FolderImportStatus.PENDING, FolderImportStatus.FAILED)
                }
        }

        private fun deriveFolderName(
            context: Context,
            treeUri: Uri,
        ): String? {
            return runCatching {
                DocumentFile.fromTreeUri(context, treeUri)?.name
            }.getOrNull()
        }

        private fun mediaTypeForUri(uri: Uri): String {
            val name = uri.lastPathSegment ?: ""
            val ext = name.substringAfterLast('.').lowercase()
            return if (ext in setOf("mp4", "m4v", "mov", "avi", "wmv", "flv")) "video" else "audio"
        }
    }
