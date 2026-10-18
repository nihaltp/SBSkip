package com.nihaltp.sbskip.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

        fun deleteVideo(id: Long) {
            viewModelScope.launch {
                downloadedVideoRepository.deleteById(id)
            }
        }

        fun clearHistory() {
            viewModelScope.launch {
                downloadedVideoRepository.deleteAll()
            }
        }
    }
