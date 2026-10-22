package com.nihaltp.sbskip.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.documentfile.provider.DocumentFile
import com.nihaltp.sbskip.data.repository.DownloadedVideoRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.notifications.ImportNotificationManager
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
) {
    /**
     * True once the item reached a terminal state (completed or failed). Terminal items are
     * announced by the UI and then dropped from the active import queue.
     */
    val isTerminal: Boolean
        get() = status == FolderImportStatus.COMPLETED || status == FolderImportStatus.FAILED
}

/**
 * A queue item in its persisted form. It is stored in the settings so that the import queue
 * survives a restart of the app.
 */
@Serializable
data class PersistedFolderImport(
    val id: Long,
    val treeUri: String,
    val folderName: String?,
    val status: FolderImportStatus,
    val importedCount: Int,
    val errorMessage: String?,
)

/**
 * Converts the queue item into its persisted form.
 */
fun FolderImportQueueItem.toPersisted(): PersistedFolderImport =
    PersistedFolderImport(
        id = id,
        treeUri = treeUri.toString(),
        folderName = folderName,
        status = status,
        importedCount = importedCount,
        errorMessage = errorMessage,
    )

/**
 * Restores a queue item from its persisted form. A scan that was interrupted by the death of the
 * app is scanned again.
 */
fun PersistedFolderImport.toQueueItem(): FolderImportQueueItem =
    FolderImportQueueItem(
        id = id,
        treeUri = Uri.parse(treeUri),
        folderName = folderName,
        status = if (status == FolderImportStatus.SCANNING) FolderImportStatus.PENDING else status,
        importedCount = importedCount,
        errorMessage = errorMessage,
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
        private val importNotificationManager: ImportNotificationManager,
        private val settingsRepository: SettingsRepository,
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

        /** True once the persisted queue was restored, so that it is only restored once. */
        private var isRestored = false

        /**
         * Job of the folder that is currently being scanned. Cancelling it stops that scan while
         * the rest of the queue keeps being processed.
         */
        private var scanningJob: Job? = null

        private fun nextId(): Long {
            nextQueueId++
            return nextQueueId
        }

        /**
         * Restores the import queue that was persisted before the app was killed and continues
         * the import. Called once when the app starts.
         */
        fun resumePersistedImports() {
            scope.launch {
                if (isRestored) return@launch
                isRestored = true

                restoreQueue()
                persistQueueChanges()
            }
        }

        /**
         * Reads the persisted queue and starts the import again. A folder that was being scanned
         * when the app died is scanned again, the files that were already imported are skipped.
         */
        private suspend fun restoreQueue() {
            val persisted = settingsRepository.settings.first().importQueue
            if (persisted.isEmpty()) return

            val restored = persisted.map { it.toQueueItem() }
            _importQueue.value = restored
            // The ids of the restored items are reused, so the counter has to continue after them.
            nextQueueId = maxOf(nextQueueId, restored.maxOfOrNull { it.id } ?: 0L)
            AppLogger.metadata(
                "ImportFolder: Restored ${restored.size} queue item(s) after a restart of the app",
            )

            if (restored.any { it.status == FolderImportStatus.PENDING }) {
                updateFolderQueueNotification()
                processQueue()
            }
        }

        /**
         * Writes the queue to the settings whenever it changes, so that it survives a restart of
         * the app.
         */
        private suspend fun persistQueueChanges() {
            _importQueue.drop(1).collect { queue ->
                val persisted = queue.map { it.toPersisted() }
                settingsRepository.update { settings ->
                    settings.copy(importQueue = persisted)
                }
            }
        }

        /**
         * Appends an item to the queue while enforcing [MAX_QUEUE_SIZE]. When the queue is full
         * the oldest PENDING items are dropped first (the item being added is never dropped);
         * if there are not enough PENDING items, the oldest already finished items are dropped
         * as well. This keeps the queue bounded even when the UI is not around to announce
         * finished folders.
         */
        private fun enqueue(item: FolderImportQueueItem) {
            _importQueue.update { queue ->
                val next = queue + item
                val overflow = next.size - MAX_QUEUE_SIZE
                if (overflow <= 0) {
                    next
                } else {
                    val dropIds =
                        next.filter { it.status == FolderImportStatus.PENDING && it.id != item.id }
                            .take(overflow)
                            .map { it.id }
                            .toMutableSet()
                    if (dropIds.size < overflow) {
                        val finished = next.filter { it.id !in dropIds && it.id != item.id }
                        dropIds += finished.take(overflow - dropIds.size).map { it.id }
                    }
                    AppLogger.metadata(
                        "ImportFolder: Queue full, dropped ${dropIds.size} old queue item(s)",
                    )
                    next.filterNot { it.id in dropIds }
                }
            }
            updateFolderQueueNotification()
        }

        /**
         * Number of folders that still have to be imported: the folder that is currently being
         * scanned plus every folder that is still queued.
         */
        private fun remainingFolderCount(): Int =
            _importQueue.value.count {
                it.status == FolderImportStatus.PENDING || it.status == FolderImportStatus.SCANNING
            }

        /**
         * Keeps the "folders remaining" notification in sync with the queue.
         */
        private fun updateFolderQueueNotification() {
            importNotificationManager.updateFolderQueueNotification(remainingFolderCount())
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

                enqueue(item)

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
            _importQueue.update { queue ->
                queue.map { if (it.id == pending.id) scanningItem else it }
            }

            _isImporting.value = true
            _importProgressMessage.value = null

            try {
                val count = scanFolderCancellable(scanningItem)
                _importQueue.update { queue ->
                    queue.map {
                        if (it.id == scanningItem.id) {
                            scanningItem.copy(
                                status = FolderImportStatus.COMPLETED,
                                importedCount = count,
                            )
                        } else {
                            it
                        }
                    }
                }
                AppLogger.metadata(
                    "ImportFolder: Completed queue item ${scanningItem.folderName} ($count imported)",
                )
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    // The whole import was cancelled, not only the scan of this folder.
                    throw e
                }
                // The user cancelled the scan of this folder: it is already removed from the
                // queue, so the import simply continues with the next folder.
                AppLogger.metadata(
                    "ImportFolder: Scan of ${scanningItem.folderName} was cancelled by the user",
                )
            } catch (e: Exception) {
                _importQueue.update { queue ->
                    queue.map {
                        if (it.id == scanningItem.id) {
                            scanningItem.copy(status = FolderImportStatus.FAILED, errorMessage = e.message)
                        } else {
                            it
                        }
                    }
                }
                AppLogger.metadata("ImportFolder: Failed queue item ${scanningItem.folderName}: ${e.message}")
            } finally {
                _isImporting.value = false
                _importProgressMessage.value = null
                val hasMoreFolders = _importQueue.value.any { it.status == FolderImportStatus.PENDING }
                if (hasMoreFolders) {
                    // Auto-start the next PENDING folder, if any.
                    updateFolderQueueNotification()
                    processQueue()
                } else {
                    // Nothing left to import: dismiss the progress notifications.
                    importNotificationManager.cancelAll()
                }
            }
        }

        /**
         * Per-folder scan logic (extracted so it can be reused for each queued item).
         * Two-phase scanning:
         *   Phase 2 (file processing): Non-recursive scan of media files directly in this folder
         *     via ffprobe + DB insert — starts immediately, no deep traversal wait.
         *   Phase 1 (folder discovery): List immediate subfolders and enqueue each as a PENDING
         *     import queue item (deduped against existing queue URIs). Subfolders are then
         *     processed recursively through the queue, each repeating the two-phase approach.
         * Returns the number of newly imported videos in this folder (excluding subfolders).
         */
        private suspend fun scanFolderItem(item: FolderImportQueueItem): Int {
            val cacheDir = context.cacheDir
            val contentResolver = context.contentResolver
            var importedCount = 0

            // Phase 2: Process media files directly in this folder (non-recursive).
            // The files are listed up front so the exact number of remaining files is known.
            val mediaFiles = FolderScanner.scanMediaFilesInFolder(context, item.treeUri).toList()
            val totalFiles = mediaFiles.size
            var scannedFiles = 0
            importNotificationManager.updateFileQueueNotification(
                remainingFiles = totalFiles,
                currentFileName = null,
                totalFiles = totalFiles,
            )

            for (fileUri in mediaFiles) {
                // Stop as soon as the scan of this folder is cancelled.
                currentCoroutineContext().ensureActive()
                val fileName = fileUri.lastPathSegment ?: "unknown"
                try {
                    _importProgressMessage.value = fileName

                    // A file that was already imported is skipped without probing it: the probe
                    // is the expensive part of the import.
                    if (isAlreadyImportedFile(fileUri.toString())) {
                        AppLogger.metadata("ImportFolder: Skipping already imported file $fileName")
                        continue
                    }

                    val metadata =
                        ffmpegMetadataReader.readMetadata(contentResolver, fileUri, cacheDir)
                    val youtubeId = metadata.youtubeId
                    // Sanitize the youtubeId: strip query parameters (e.g. "?overwrite=true")
                    // and keep only a clean 11-character YouTube video ID before storing it.
                    val cleanYoutubeId = youtubeId?.let { YouTubeUrlParser.cleanVideoId(it) }

                    if (cleanYoutubeId != null) {
                        // A video is tracked by its YouTube id: when it is already tracked, the
                        // file is added to the existing entry instead of creating a second row.
                        val existingVideo = downloadedVideoRepository.findByVideoId(cleanYoutubeId)
                        if (existingVideo == null) {
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
                                    outputPaths = listOf(fileUri.toString()),
                                    fileSize = metadata.fileSize,
                                    mediaType = mediaTypeForUri(fileUri),
                                )
                            downloadedVideoRepository.insert(entity)
                            importedCount++
                            AppLogger.metadata(
                                "ImportFolder: Imported video $cleanYoutubeId ($fileName)",
                            )
                        } else {
                            val added =
                                downloadedVideoRepository.addOutputPath(
                                    videoId = cleanYoutubeId,
                                    outputPath = fileUri.toString(),
                                )
                            if (added) {
                                importedCount++
                                AppLogger.metadata(
                                    "ImportFolder: Added $fileName to the already tracked video $cleanYoutubeId",
                                )
                            } else {
                                AppLogger.metadata(
                                    "ImportFolder: Skipping duplicate $cleanYoutubeId ($fileName)",
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.metadata("ImportFolder: Error probing $fileUri: ${e.message}")
                } finally {
                    scannedFiles++
                    importNotificationManager.updateFileQueueNotification(
                        remainingFiles = (totalFiles - scannedFiles).coerceAtLeast(0),
                        currentFileName = fileName,
                        totalFiles = totalFiles,
                    )
                }
            }

            // Phase 1: Discover immediate subfolders and enqueue them as PENDING items.
            // Dedup against existing queue URIs so the same subfolder isn't queued twice.
            // NOTE: We do NOT call takePersistableUriPermission() on each subfolder URI —
            // those are child document URIs of the root tree URI, and the persistable
            // permission granted on the root URI already covers them. Calling
            // takePersistableUriPermission() on a child URI throws
            // "No persistable permission granted" on many DocumentsProvider implementations.
            val subfolderUris = FolderScanner.listSubfolderUris(context, item.treeUri)
            val existingUris = _importQueue.value.map { it.treeUri }.toSet()
            for (subUri in subfolderUris) {
                if (subUri !in existingUris) {
                    val subFolderName = deriveFolderName(subUri)
                    val subItem =
                        FolderImportQueueItem(
                            id = nextId(),
                            treeUri = subUri,
                            folderName = subFolderName,
                            status = FolderImportStatus.PENDING,
                            importedCount = 0,
                            errorMessage = null,
                        )
                    enqueue(subItem)
                    AppLogger.metadata(
                        "ImportFolder: Enqueued subfolder $subFolderName ($subUri)",
                    )
                }
            }

            return importedCount
        }

        /**
         * True when the file at [outputPath] was already imported. Such a file is skipped without
         * probing it, because the probe is the expensive part of the import.
         */
        internal suspend fun isAlreadyImportedFile(outputPath: String): Boolean =
            downloadedVideoRepository.findByOutputPath(outputPath) != null

        /**
         * Scans a single folder in a job of its own, so that the running scan can be cancelled by
         * the user. Cancelling the job only stops this folder: the queue keeps being processed.
         */
        private suspend fun scanFolderCancellable(item: FolderImportQueueItem): Int {
            val job = scope.async(Dispatchers.IO) { scanFolderItem(item) }
            scanningJob = job
            try {
                return job.await()
            } finally {
                scanningJob = null
                if (!currentCoroutineContext().isActive) {
                    // The whole import was cancelled, so the scan has to stop as well.
                    job.cancel()
                }
            }
        }

        /**
         * Cancels the scan of the folder that is currently being imported. The folder is removed
         * from the queue and the import continues with the next queued folder.
         */
        fun cancelCurrentScan() {
            val scanningId =
                _importQueue.value
                    .firstOrNull { it.status == FolderImportStatus.SCANNING }
                    ?.id ?: return
            cancelScanningFolder(scanningId)
            updateFolderQueueNotification()
        }

        /**
         * Cancels the running scan of the folder with [id] and removes it from the queue.
         */
        private fun cancelScanningFolder(id: Long) {
            scanningJob?.cancel(CancellationException("Import cancelled by the user"))
            scanningJob = null
            _importQueue.update { queue -> queue.filterNot { it.id == id } }
            // The file notification belongs to the cancelled scan, the next folder posts its own.
            importNotificationManager.updateFileQueueNotification(remainingFiles = 0)
            AppLogger.metadata("ImportFolder: Cancelled the running scan of queue item $id")
        }

        /**
         * Removes a queue item (e.g. user-initiated cancel). A folder that is currently being
         * scanned is cancelled and removed as well, the remaining queue is processed as usual.
         */
        fun removeQueueItem(id: Long) {
            val isScanning =
                _importQueue.value.any {
                    it.id == id && it.status == FolderImportStatus.SCANNING
                }
            if (isScanning) {
                cancelScanningFolder(id)
            } else {
                _importQueue.update { queue -> queue.filterNot { it.id == id } }
            }
            updateFolderQueueNotification()
        }

        /**
         * Removes a finished (COMPLETED or FAILED) item from the active import queue. The UI
         * calls this once the result has been announced to the user, so that the queue only
         * keeps the folders that are still pending or being scanned.
         */
        fun dismissCompletedItem(id: Long) {
            _importQueue.update { queue -> queue.filterNot { it.id == id && it.isTerminal } }
        }

        /**
         * Adds a queue item without triggering a scan. Used by tests to exercise the queue
         * bookkeeping (size cap, removal) without running an actual folder import.
         */
        @VisibleForTesting
        internal fun enqueueForTesting(item: FolderImportQueueItem) {
            enqueue(item)
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

        companion object {
            /**
             * Hard cap on the number of entries kept in the import queue. Older PENDING entries
             * are dropped first, so the queue can never grow without bound (e.g. when the
             * Downloaded History screen is not open to announce finished folders).
             */
            const val MAX_QUEUE_SIZE = 50
        }
    }
