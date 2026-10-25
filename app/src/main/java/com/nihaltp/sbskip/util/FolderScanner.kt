package com.nihaltp.sbskip.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

object FolderScanner {
    private val MEDIA_EXTENSIONS =
        setOf(
            "mp4",
            "m4a",
            "mp3",
            "m4v",
            "mov",
            "mkv",
            "avi",
            "wmv",
            "flv",
            "flac",
            "wav",
            "aac",
            "ogg",
            "opus",
            "wma",
            "m4b",
            "m4r",
        )

    fun scanMediaFiles(
        context: Context,
        treeUri: Uri,
    ): Flow<Uri> =
        flow {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@flow
            var fileCount = 0
            walk(root, this) { fileCount++ }
            AppLogger.metadata("ImportFolder: Found $fileCount media files in scanned folder")
        }

    /**
     * Lists URIs of immediate subdirectories under [treeUri] (non-recursive).
     * Used for Phase 1 of fast folder scanning: discover subfolders quickly
     * so they can be enqueued as independent import queue items.
     */
    fun listSubfolderUris(
        context: Context,
        treeUri: Uri,
    ): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && !isHiddenFolderName(it.name) }
            ?.mapNotNull { it.uri }
            ?: emptyList()
    }

    /**
     * Emits URIs of media files directly inside [treeUri] (non-recursive).
     * Used for Phase 2 of fast folder scanning: process files in the current
     * folder without waiting for a deep recursive traversal.
     */
    fun scanMediaFilesInFolder(
        context: Context,
        treeUri: Uri,
    ): Flow<Uri> =
        flow {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@flow
            val files = root.listFiles() ?: return@flow
            for (file in files) {
                if (file.isFile) {
                    val name = file.name ?: continue
                    if (isMediaFile(name)) {
                        file.uri?.let { emit(it) }
                    }
                }
            }
        }

    private suspend fun walk(
        doc: DocumentFile,
        collector: FlowCollector<Uri>,
        onMediaFile: () -> Unit,
    ) {
        val files = doc.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Skip hidden folders such as ".git" during folder imports.
                if (isHiddenFolderName(file.name)) continue
                walk(file, collector, onMediaFile)
            } else if (file.isFile) {
                val name = file.name ?: continue
                if (isMediaFile(name)) {
                    onMediaFile()
                    file.uri?.let { collector.emit(it) }
                }
            }
        }
    }

    internal fun isMediaFile(name: String): Boolean {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) {
            name.substring(lastDot + 1).lowercase() in MEDIA_EXTENSIONS
        } else {
            false
        }
    }

    /**
     * True for folder names that start with a dot, e.g. ".git". Hidden folders are skipped during
     * folder imports.
     */
    internal fun isHiddenFolderName(name: String?): Boolean = name != null && name.startsWith(".")
}
