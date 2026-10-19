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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ImportCandidate(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val youtubeId: String,
    val youtubeUrl: String?,
    var isSelected: Boolean = false,
)

@HiltViewModel
class ImportFromFolderViewModel
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
        private val ffmpegMetadataReader: FfmpegMetadataReader,
        application: Application,
    ) : AndroidViewModel(application) {
        private val _folderUri = MutableStateFlow<Uri?>(null)
        val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

        internal val candidatesState = MutableStateFlow<List<ImportCandidate>>(emptyList())
        val candidates: StateFlow<List<ImportCandidate>> = candidatesState.asStateFlow()

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        private val selectedIds = mutableSetOf<String>()

        private val _selectedCount = MutableStateFlow(0)
        val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

        private fun updateSelectedCount() {
            _selectedCount.value = selectedIds.size
        }

        fun setFolderUri(uri: Uri?) {
            _folderUri.value = uri
            if (uri != null) {
                selectedIds.clear()
                candidatesState.value = emptyList()
            }
        }

        fun toggleCandidate(candidate: ImportCandidate) {
            val wasSelected = selectedIds.contains(candidate.id)
            if (wasSelected) {
                selectedIds.remove(candidate.id)
            } else {
                selectedIds.add(candidate.id)
            }
            updateSelectedCount()
            val current = candidatesState.value
            candidatesState.value =
                current.map {
                    if (it.id == candidate.id) it.copy(isSelected = !wasSelected) else it
                }
        }

        fun toggleSelectAll() {
            val allSelected = candidatesState.value.all { it.id in selectedIds }
            if (allSelected) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds += candidatesState.value.map { it.id }
            }
            updateSelectedCount()
            candidatesState.value =
                candidatesState.value.map {
                    it.copy(isSelected = it.id in selectedIds)
                }
        }

        fun scanFolder(
            context: Context,
            treeUri: Uri,
        ) {
            viewModelScope.launch {
                _isScanning.value = true
                try {
                    val cacheDir = context.cacheDir
                    val contentResolver = context.contentResolver

                    val candidates =
                        withContext(Dispatchers.IO) {
                            val mutableCandidates = mutableListOf<ImportCandidate>()

                            FolderScanner.scanMediaFiles(context, treeUri).collect { fileUri ->
                                try {
                                    val fileName = fileUri.lastPathSegment ?: "unknown"
                                    val metadata = ffmpegMetadataReader.readMetadata(contentResolver, fileUri, cacheDir)
                                    val youtubeId = metadata.youtubeId
                                    if (youtubeId != null) {
                                        mutableCandidates.add(
                                            ImportCandidate(
                                                uri = fileUri,
                                                fileName = fileName,
                                                youtubeId = youtubeId,
                                                youtubeUrl = metadata.youtubeUrl,
                                                isSelected = false,
                                            ),
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Skip files that fail to probe; one bad file shouldn't stop the scan
                                }
                            }

                            AppLogger.metadata("ImportFolder: Scan complete. Found ${mutableCandidates.size} sbskip-downloaded files")
                            mutableCandidates
                        }

                    candidatesState.value = candidates
                } finally {
                    _isScanning.value = false
                }
            }
        }

        fun importSelected() {
            val selected = candidatesState.value.filter { it.id in selectedIds }
            if (selected.isEmpty()) return

            viewModelScope.launch {
                val now = System.currentTimeMillis()
                for (candidate in selected) {
                    val entity =
                        DownloadedVideoEntity(
                            videoId = candidate.youtubeId,
                            url =
                                candidate.youtubeUrl
                                    ?: "https://www.youtube.com/watch?v=${candidate.youtubeId}",
                            title = candidate.fileName,
                            thumbnailUrl = null,
                            durationSeconds = null,
                            downloadedAtEpochMillis = now,
                            outputPath = candidate.uri.toString(),
                            mediaType = mediaTypeForUri(candidate.uri),
                        )
                    downloadedVideoRepository.insert(entity)
                }
                selectedIds.clear()
                candidatesState.value = emptyList()
                _folderUri.value = null
                updateSelectedCount()
            }
        }

        fun clearSelection() {
            selectedIds.clear()
            candidatesState.value = candidatesState.value.map { it.copy(isSelected = false) }
        }

        private fun mediaTypeForUri(uri: Uri): String {
            val name = uri.lastPathSegment ?: ""
            val ext = name.substringAfterLast('.').lowercase()
            return if (ext in setOf("mp4", "m4v", "mov", "avi", "wmv", "flv")) "video" else "audio"
        }
    }
