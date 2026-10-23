package com.nihaltp.sbskip.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.ExportData
import com.nihaltp.sbskip.model.ExportFormat
import com.nihaltp.sbskip.model.ExportItem
import com.nihaltp.sbskip.model.ImportResult
import com.nihaltp.sbskip.util.parser.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object ExportImportUtil {
    private const val EXPORT_FILENAME_PREFIX = "sbskip_export_"
    private const val MIME_TYPE_TEXT = "text/plain"
    private const val MIME_TYPE_JSON = "application/json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    /**
     * Export queue items to a string based on the selected format.
     */
    fun exportQueue(
        queueItems: List<DownloadQueueItem>,
        format: ExportFormat,
    ): String {
        return when (format) {
            ExportFormat.VIDEO_IDS ->
                queueItems
                    .mapNotNull { YouTubeUrlParser.extractVideoId(it.url) }
                    .distinct()
                    .joinToString("\n")

            ExportFormat.FULL_URLS ->
                queueItems
                    .map { it.url }
                    .distinct()
                    .joinToString("\n")

            ExportFormat.FULL_JSON -> {
                val exportItems =
                    queueItems.map { item ->
                        val videoId = YouTubeUrlParser.extractVideoId(item.url) ?: ""
                        val categories = extractCategoriesFromUrl(item.url)
                        ExportItem(
                            videoId = videoId,
                            url = item.url,
                            title = item.title.ifBlank { null },
                            author = null, // Not stored in queue item
                            thumbnailUrl = item.thumbnailUrl,
                            sponsorBlockCategories = categories,
                        )
                    }
                val exportData = ExportData(items = exportItems)
                json.encodeToString(ExportData.serializer(), exportData)
            }
        }
    }

    /**
     * Export downloaded video entities to a string based on the selected format.
     */
    fun exportDownloadedVideos(
        entities: List<DownloadedVideoEntity>,
        format: ExportFormat,
    ): String {
        return when (format) {
            ExportFormat.VIDEO_IDS ->
                entities
                    .mapNotNull { YouTubeUrlParser.extractVideoId(it.url) }
                    .distinct()
                    .joinToString("\n")

            ExportFormat.FULL_URLS ->
                entities
                    .map { it.url }
                    .distinct()
                    .joinToString("\n")

            ExportFormat.FULL_JSON -> {
                val exportItems =
                    entities.map { entity ->
                        val videoId = YouTubeUrlParser.extractVideoId(entity.url) ?: ""
                        val categories = extractCategoriesFromUrl(entity.url)
                        ExportItem(
                            videoId = videoId,
                            url = entity.url,
                            title = entity.title.ifBlank { null },
                            author = entity.author?.ifBlank { null },
                            thumbnailUrl = entity.thumbnailUrl?.ifBlank { null },
                            sponsorBlockCategories = categories,
                        )
                    }
                val exportData = ExportData(items = exportItems)
                json.encodeToString(ExportData.serializer(), exportData)
            }
        }
    }

    /**
     * Extract sponsor block categories from URL query parameter.
     */
    private fun extractCategoriesFromUrl(url: String): List<String>? {
        val uri = java.net.URI(url)
        val categoriesParam =
            uri.rawQuery
                ?.split('&')
                ?.asSequence()
                ?.map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
                ?.firstOrNull { it.first == "categories" }
                ?.second

        return categoriesParam?.split(',')?.filter { it.isNotBlank() }
    }

    /**
     * Generate a filename for export with timestamp.
     */
    fun generateExportFilename(format: ExportFormat): String {
        val extension =
            when (format) {
                ExportFormat.VIDEO_IDS -> "txt"
                ExportFormat.FULL_URLS -> "txt"
                ExportFormat.FULL_JSON -> "json"
            }
        val timestamp =
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
        return "${EXPORT_FILENAME_PREFIX}$timestamp.$extension"
    }

    /**
     * Get MIME type for the export format.
     */
    fun getMimeType(format: ExportFormat): String {
        return when (format) {
            ExportFormat.FULL_JSON -> MIME_TYPE_JSON
            else -> MIME_TYPE_TEXT
        }
    }

    /**
     * Copy text to clipboard.
     */
    fun copyToClipboard(
        context: Context,
        text: String,
    ): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SB Skip Export", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get text from clipboard.
     */
    fun getFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse imported text to extract video IDs or URLs.
     */
    fun parseImportedText(text: String): ImportResult {
        val lines =
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ImportResult.Failure("No valid lines found in import text")
        }

        // Try to parse as JSON first
        val firstLine = lines.first()
        if (firstLine.startsWith("{") || firstLine.startsWith("[")) {
            return try {
                val exportData = json.decodeFromString<ExportData>(text)
                ImportResult.JsonSuccess(exportData)
            } catch (e: Exception) {
                // Not valid JSON, fall through to line-by-line parsing
                parseLinesAsUrls(lines)
            }
        }

        return parseLinesAsUrls(lines)
    }

    private fun parseLinesAsUrls(lines: List<String>): ImportResult {
        val videoIdsOrUrls =
            lines.mapNotNull { line ->
                // Try to extract video ID from URL or use as-is if it looks like a video ID
                val videoId = YouTubeUrlParser.extractVideoId(line)
                if (videoId != null) {
                    videoId
                } else if (YouTubeUrlParser.isValidVideoId(line)) {
                    line // Already a video ID
                } else {
                    null
                }
            }.distinct()

        if (videoIdsOrUrls.isEmpty()) {
            return ImportResult.Failure("No valid YouTube video IDs or URLs found")
        }

        return ImportResult.Success(videoIdsOrUrls)
    }

    /**
     * Read text content from a URI.
     */
    suspend fun readTextFromUri(
        context: Context,
        uri: Uri,
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Write text content to a Uri (e.g. from a document picker).
     */
    suspend fun writeTextToUri(
        context: Context,
        uri: Uri,
        text: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Show a toast message.
     */
    fun showToast(
        context: Context,
        message: String,
    ) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Extract video ID from a DownloadedVideoEntity.
     */
    fun extractVideoIdFromEntity(entity: DownloadedVideoEntity): String? {
        return YouTubeUrlParser.extractVideoId(entity.url)
    }
}
