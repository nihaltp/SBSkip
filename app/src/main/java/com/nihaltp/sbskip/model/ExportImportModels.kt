package com.nihaltp.sbskip.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val items: List<ExportItem>,
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

enum class ExportFormat {
    VIDEO_IDS, // Plain text: one 11-char ID per line
    FULL_URLS, // Plain text: one full URL per line
    FULL_JSON, // JSON with all metadata
}

enum class ExportDestination { FILE, CLIPBOARD }

enum class ImportSource { FILE, CLIPBOARD }

sealed class ImportResult {
    data class Success(val items: List<String>) : ImportResult() // video IDs or URLs

    data class JsonSuccess(val exportData: ExportData) : ImportResult()

    data class Failure(val error: String) : ImportResult()
}
