package com.nihaltp.sbskip.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.FfmpegMetadataReader
import com.nihaltp.sbskip.util.FolderImportQueueItem
import com.nihaltp.sbskip.util.ImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadedHistoryViewModel
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
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

        private fun mediaTypeForUri(uri: Uri): String {
            val name = uri.lastPathSegment ?: ""
            val ext = name.substringAfterLast('.').lowercase()
            return if (ext in setOf("mp4", "m4v", "mov", "avi", "wmv", "flv")) "video" else "audio"
        }
    }
