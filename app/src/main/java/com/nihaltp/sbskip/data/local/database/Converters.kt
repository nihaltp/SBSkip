package com.nihaltp.sbskip.data.local.database

import androidx.room.TypeConverter
import com.nihaltp.sbskip.model.DownloadQueueStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts a list of strings (for example the file paths of a downloaded video) into a single
 * database column.
 */
object StringListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(value: List<String>): String = json.encodeToString(value)

    fun decode(value: String): List<String> = runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}

class Converters {
    @TypeConverter
    fun toDownloadQueueStatus(value: String): DownloadQueueStatus = DownloadQueueStatus.valueOf(value)

    @TypeConverter
    fun fromDownloadQueueStatus(value: DownloadQueueStatus): String = value.name

    @TypeConverter
    fun toStringList(value: String): List<String> = StringListConverter.decode(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = StringListConverter.encode(value)
}
