package com.nihaltp.sbskip.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Process-lifetime import manager. Owns the import queue and the sequential scanning coroutine,
 * so folder imports survive navigation away from the Downloaded History screen.
 */
@Singleton
class ImportManager
    @Inject
    constructor(
        private val downloadedVideoRepository: DownloadedVideoRepository,
        private val ffmpegMetadataReader: FfmpegMetadataReader,
        @ApplicationContext private val context: Context,
    ) {
        private val scope = MainScope()

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

        /**
         * Enqueues a folder for import. Takes persistable URI permission at enqueue time so the
         * permission is held until the folder is actually scanned. If a scan is already running,
         * the folder is added as PENDING and scanned after the current one completes.
         */
        fun scanAndImportFolder(treeUri: Uri) {
            scope.launch {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )

                val folderName = deriveFolderName(treeUri)
                val item =
                    FolderImportQueueItem(
                        id = nextId(),
                        treeUri = treeUri,
                        folderName = folderName,
                        status = FolderImportStatus.PENDING,
                        importedCount = 0,
                        errorMessage = null,
                    )

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

            try {
                withContext(Dispatchers.IO) {
                    val count = scanFolderItem(scanningItem)
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
        private suspend fun scanFolderItem(item: FolderImportQueueItem): Int {
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

        private fun deriveFolderName(treeUri: Uri): String? {
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
