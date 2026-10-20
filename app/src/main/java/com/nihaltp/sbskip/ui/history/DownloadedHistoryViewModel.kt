package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import com.nihaltp.sbskip.util.FolderScanner
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

        fun scanAndImportFolder(
            context: Context,
            treeUri: Uri,
        ) {
            viewModelScope.launch {
                _isImporting.value = true
                _importProgressMessage.value = null
                try {
                    val cacheDir = context.cacheDir
                    val contentResolver = context.contentResolver

                    withContext(Dispatchers.IO) {
                        FolderScanner.scanMediaFiles(context, treeUri).collect { fileUri ->
                            try {
                                val fileName = fileUri.lastPathSegment ?: "unknown"
                                _importProgressMessage.value = fileName

                                val metadata =
                                    ffmpegMetadataReader.readMetadata(contentResolver, fileUri, cacheDir)
                                val youtubeId = metadata.youtubeId

                                if (youtubeId != null) {
                                    // Duplicate detection:
                                    // Check if a video with this videoId or this outputPath already exists in Room
                                    val existingById = downloadedVideoRepository.findByVideoId(youtubeId)
                                    val existingByPath =
                                        downloadedVideoRepository.findByOutputPath(fileUri.toString())

                                    if (existingById == null && existingByPath == null) {
                                        val entity =
                                            DownloadedVideoEntity(
                                                videoId = youtubeId,
                                                url =
                                                    metadata.youtubeUrl
                                                        ?: "https://www.youtube.com/watch?v=$youtubeId",
                                                title = metadata.youtubeTitle ?: fileName,
                                                author = metadata.authorName,
                                                thumbnailUrl =
                                                    "https://img.youtube.com/vi/$youtubeId/mqdefault.jpg",
                                                durationSeconds = null,
                                                outputPath = fileUri.toString(),
                                                fileSize = metadata.fileSize,
                                                mediaType = mediaTypeForUri(fileUri),
                                            )
                                        downloadedVideoRepository.insert(entity)
                                        AppLogger.metadata("ImportFolder: Imported video $youtubeId ($fileName)")
                                    } else {
                                        AppLogger.metadata("ImportFolder: Skipping duplicate $youtubeId ($fileName)")
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.metadata("ImportFolder: Error probing $fileUri: ${e.message}")
                            }
                        }
                    }
                } finally {
                    _isImporting.value = false
                    _importProgressMessage.value = null
                }
            }
        }

        private fun mediaTypeForUri(uri: Uri): String {
            val name = uri.lastPathSegment ?: ""
            val ext = name.substringAfterLast('.').lowercase()
            return if (ext in setOf("mp4", "m4v", "mov", "avi", "wmv", "flv")) "video" else "audio"
        }
    }
