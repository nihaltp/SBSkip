package com.nihaltp.sbskip.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

fun formatUriToPath(
    context: Context,
    uriString: String?,
): String {
    if (uriString.isNullOrBlank()) return "N/A"
    val decoded =
        try {
            Uri.decode(uriString)
        } catch (e: Exception) {
            uriString
        }

    try {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver

        if (uri.scheme == "content") {
            var docId: String? = null
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri)
                }
            } catch (e: Exception) {
                // Not a document Uri
            }

            var targetUri = uri
            if (docId != null && docId.startsWith("msf:")) {
                val mediaId = docId.substringAfter("msf:").toLongOrNull()
                if (mediaId != null) {
                    targetUri =
                        ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"),
                            mediaId,
                        )
                }
            }

            try {
                resolver.query(targetUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex != -1) {
                            val path = cursor.getString(dataIndex)
                            if (!path.isNullOrBlank()) {
                                return formatPrettyAbsolutePath(path)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }

            try {
                resolver.query(
                    targetUri,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use {
                        cursor ->
                    if (cursor.moveToFirst()) {
                        val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val relPath = if (relativePathIndex != -1) cursor.getString(relativePathIndex) else null
                        val dispName = if (displayNameIndex != -1) cursor.getString(displayNameIndex) else null
                        if (!relPath.isNullOrBlank() || !dispName.isNullOrBlank()) {
                            val folder = relPath?.trim('/')?.takeIf { it.isNotBlank() }
                            return if (folder != null && dispName != null) "$folder/$dispName" else dispName ?: "$folder/"
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }

            try {
                resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                        cursor ->
                    if (cursor.moveToFirst()) {
                        val dispIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (dispIndex != -1) {
                            val dispName = cursor.getString(dispIndex)
                            if (!dispName.isNullOrBlank()) {
                                return dispName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                return formatPrettyAbsolutePath(path)
            }
        }
    } catch (e: Exception) {
        // Fallback
    }

    if (decoded.contains("primary:")) {
        val path = decoded.substringAfterLast("primary:").trim('/')
        return if (path.isEmpty()) "SB Skip/" else "$path/"
    }
    if (decoded.contains("raw:")) {
        return formatPrettyAbsolutePath(decoded.substringAfterLast("raw:"))
    }
    if (decoded.contains("/document/")) {
        val docPart = decoded.substringAfterLast("/document/")
        if (docPart.isNotBlank()) {
            return docPart
        }
    }
    if (decoded.contains("/tree/")) {
        val treePart = decoded.substringAfterLast("/tree/")
        if (treePart.isNotBlank()) {
            return treePart
        }
    }

    return decoded
}

fun formatPrettyAbsolutePath(path: String): String {
    val prefixes = listOf("/storage/emulated/0/", "storage/emulated/0/", "/sdcard/", "sdcard/")
    var pretty = path
    for (prefix in prefixes) {
        if (pretty.startsWith(prefix, ignoreCase = true)) {
            pretty = pretty.substring(prefix.length)
            break
        }
    }
    return pretty.trim('/')
}
