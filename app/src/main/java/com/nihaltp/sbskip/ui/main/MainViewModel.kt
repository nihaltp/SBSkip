package com.nihaltp.sbskip.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            queueRepository.observeQueue().collect { queueItems ->
                _uiState.update { it.copy(queueItems = queueItems) }
            }
        }
    }

    fun onUrlChanged(value: String) {
        _uiState.update { it.copy(urlInput = value) }
    }

    fun queueCurrentUrl() {
        val input = uiState.value.urlInput
        if (input.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Paste a YouTube URL first.") }
            return
        }

        viewModelScope.launch {
            val result = queueRepository.enqueue(input)
            _uiState.update {
                if (result.success) {
                    it.copy(urlInput = "", snackbarMessage = result.message)
                } else {
                    it.copy(snackbarMessage = result.message)
                }
            }
        }
    }

    fun handleSharedText(event: ShareIntentEvent) {
        onUrlChanged(event.text)
        queueCurrentUrl()
    }

    fun retryQueueItem(id: Long) {
        viewModelScope.launch {
            val result = queueRepository.retry(id)
            _uiState.update { it.copy(snackbarMessage = result.message) }
        }
    }

    fun removeQueueItem(id: Long) {
        viewModelScope.launch {
            queueRepository.remove(id)
            _uiState.update { it.copy(snackbarMessage = "Item removed.") }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
