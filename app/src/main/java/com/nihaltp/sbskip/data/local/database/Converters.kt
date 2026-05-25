package com.nihaltp.sbskip.data.local.database

import androidx.room.TypeConverter
import com.nihaltp.sbskip.model.DownloadQueueStatus

class Converters {
    @TypeConverter
    fun toDownloadQueueStatus(value: String): DownloadQueueStatus = DownloadQueueStatus.valueOf(value)

    @TypeConverter
    fun fromDownloadQueueStatus(value: DownloadQueueStatus): String = value.name
}
