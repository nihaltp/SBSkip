package com.nihaltp.sbskip.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
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
    ) : ViewModel() {
        val videos: StateFlow<List<DownloadedVideoEntity>> =
            downloadedVideoRepository.observeDownloadedVideos
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val selectedIds = mutableSetOf<Long>()

        private val _selectedCount = MutableStateFlow(0)
        val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

        private fun updateSelectedCount() {
            _selectedCount.value = selectedIds.size
        }

        fun isSelected(id: Long): Boolean = selectedIds.contains(id)

        fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            updateSelectedCount()
        }

        fun toggleSelectAll(videoIds: List<Long>) {
            val allSelected = videoIds.all { selectedIds.contains(it) }
            if (allSelected) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds += videoIds
            }
            updateSelectedCount()
        }

        fun deleteVideo(id: Long) {
            selectedIds.remove(id)
            viewModelScope.launch {
                downloadedVideoRepository.deleteById(id)
            }
            updateSelectedCount()
        }

        fun deleteSelectedVideos() {
            val ids = selectedIds.toList()
            selectedIds.clear()
            viewModelScope.launch {
                for (id in ids) {
                    downloadedVideoRepository.deleteById(id)
                }
            }
            updateSelectedCount()
        }

        fun clearHistory() {
            selectedIds.clear()
            viewModelScope.launch {
                downloadedVideoRepository.deleteAll()
            }
            updateSelectedCount()
        }
    }
