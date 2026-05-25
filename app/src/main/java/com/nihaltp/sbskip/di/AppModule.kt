package com.nihaltp.sbskip.di

import android.content.Context
import androidx.room.Room
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.database.SBSkipDatabase
import com.nihaltp.sbskip.data.repository.DefaultQueueRepository
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.downloader.DefaultVideoMetadataExtractor
import com.nihaltp.sbskip.downloader.ProcessYtDlpExecutor
import com.nihaltp.sbskip.downloader.VideoMetadataExtractor
import com.nihaltp.sbskip.downloader.YtDlpExecutor
import com.nihaltp.sbskip.workers.MetadataQueueWorkScheduler
import com.nihaltp.sbskip.workers.WorkManagerMetadataQueueScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindQueueRepository(impl: DefaultQueueRepository): QueueRepository

    @Binds
    abstract fun bindMetadataExtractor(impl: DefaultVideoMetadataExtractor): VideoMetadataExtractor

    @Binds
    abstract fun bindYtDlpExecutor(impl: ProcessYtDlpExecutor): YtDlpExecutor

    @Binds
    abstract fun bindQueueScheduler(impl: WorkManagerMetadataQueueScheduler): MetadataQueueWorkScheduler

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SBSkipDatabase {
            return Room.databaseBuilder(context, SBSkipDatabase::class.java, "sbskip.db")
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        fun provideQueueDao(database: SBSkipDatabase): DownloadQueueDao = database.downloadQueueDao()
    }
}
