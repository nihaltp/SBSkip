package com.nihaltp.sbskip.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.entity.DownloadQueueEntity

@Database(
    entities = [DownloadQueueEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SBSkipDatabase : RoomDatabase() {
    abstract fun downloadQueueDao(): DownloadQueueDao
}
