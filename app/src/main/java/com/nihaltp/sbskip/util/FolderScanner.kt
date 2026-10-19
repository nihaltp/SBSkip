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

    private suspend fun walk(
        doc: DocumentFile,
        collector: FlowCollector<Uri>,
        onMediaFile: () -> Unit,
    ) {
        val files = doc.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
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
}
