package com.nihaltp.sbskip.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val items: List<ExportItem> = emptyList(),
    /**
     * Download history entries in the full backup shape used by other SB Skip forks that implement
     * the Seal backup format (e.g. full JSON backups from Seal). The full JSON backup writes both
     * the [items] list and this list so that the backup can be imported by apps that only read
     * the `downloadHistory` key.
     */
    val downloadHistory: List<SealDownloadHistoryEntry>? = null,
)

@Serializable
data class ExportItem(
    val videoId: String,
    val url: String,
    val title: String?,
    val author: String?,
    val thumbnailUrl: String?,
    val sponsorBlockCategories: List<String>?,
)

/**
 * A single download history entry of a full JSON backup in the Seal compatible shape. The JSON key
 * names mirror Seal's `database.backup.DownloadedVideoInfo` so that full JSON backups can be
 * exchanged between SB Skip and Seal without further conversion.
 */
@Serializable
data class SealDownloadHistoryEntry(
    val id: Int,
    val videoTitle: String,
    val videoAuthor: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val videoPath: String,
    val extractor: String? = null,
)

enum class ExportFormat {
    VIDEO_IDS, // Plain text: one 11-char ID per line
    FULL_URLS, // Plain text: one full URL per line
    FULL_JSON, // JSON with all metadata
}

enum class ExportDestination { FILE, CLIPBOARD }

enum class ImportSource { FILE, CLIPBOARD, FOLDER }

sealed class ImportResult {
    data class Success(val items: List<String>) : ImportResult() // video IDs or URLs

    data class JsonSuccess(val exportData: ExportData) : ImportResult()

    data class Failure(val error: String) : ImportResult()
}
