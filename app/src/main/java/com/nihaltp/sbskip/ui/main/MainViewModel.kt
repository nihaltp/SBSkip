package com.nihaltp.sbskip.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.storage.DownloadStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val downloadStorage: DownloadStorage,
    @ApplicationContext private val context: Context,
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

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            val metadata = downloadStorage.queryMetadata(uri.toString())
            val name = metadata?.let { "${it.title}.${it.extension}" } ?: "Imported File"
            _uiState.update {
                it.copy(
                    selectedFileUri = uri.toString(),
                    selectedFileName = name
                )
            }
        }
    }

    fun queueCurrentItem() {
        val state = uiState.value
        val url = state.urlInput
        val fileUri = state.selectedFileUri
        val fileName = state.selectedFileName

        if (fileUri.isNullOrBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Please select a local media file first.") }
            return
        }
        if (url.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.snackbar_paste_first)) }
            return
        }

        viewModelScope.launch {
            val metadata = downloadStorage.queryMetadata(fileUri)
            val mediaType = if (metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "mp3") {
                MediaType.AUDIO
            } else {
                MediaType.VIDEO
            }

            val result = queueRepository.enqueue(
                localFileUri = fileUri,
                title = fileName,
                youtubeUrl = url,
                mediaType = mediaType,
            )

            _uiState.update {
                if (result.success) {
                    it.copy(
                        urlInput = "",
                        selectedFileUri = null,
                        selectedFileName = "",
                        snackbarMessage = "Media enqueued for SponsorBlock cleaning!"
                    )
                } else {
                    it.copy(snackbarMessage = result.message)
                }
            }
        }
    }

    fun handleSharedText(event: ShareIntentEvent) {
        if (event.text != null) {
            onUrlChanged(event.text)
        }
        if (event.fileUri != null) {
            onFileSelected(event.fileUri)
        }
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
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.snackbar_item_removed)) }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
