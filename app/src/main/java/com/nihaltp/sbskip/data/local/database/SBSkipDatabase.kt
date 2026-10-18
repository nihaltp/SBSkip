package com.nihaltp.sbskip.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nihaltp.sbskip.config.AppVersions
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.dao.DownloadedVideoDao
import com.nihaltp.sbskip.model.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadedVideoEntity

@Database(
    entities = [DownloadQueueEntity::class, DownloadedVideoEntity::class],
    version = AppVersions.DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SBSkipDatabase : RoomDatabase() {
    abstract fun downloadQueueDao(): DownloadQueueDao

    abstract fun downloadedVideoDao(): DownloadedVideoDao
}
