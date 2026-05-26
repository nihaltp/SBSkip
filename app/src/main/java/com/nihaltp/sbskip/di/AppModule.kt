package com.nihaltp.sbskip.di

import android.content.Context
import androidx.room.Room
import com.nihaltp.sbskip.data.local.dao.DownloadQueueDao
import com.nihaltp.sbskip.data.local.database.SBSkipDatabase
import com.nihaltp.sbskip.data.repository.DefaultQueueRepository
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.data.repository.DataStoreSettingsRepository
import com.nihaltp.sbskip.notifications.AndroidDownloadNotificationManager
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.processing.FFmpegMediaProcessor
import com.nihaltp.sbskip.sponsorblock.SponsorBlockService
import com.nihaltp.sbskip.sponsorblock.DefaultSponsorBlockService
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.storage.AndroidDownloadStorage
import com.nihaltp.sbskip.workers.DownloadWorkScheduler
import com.nihaltp.sbskip.workers.WorkManagerDownloadScheduler
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
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindSponsorBlockService(impl: DefaultSponsorBlockService): SponsorBlockService

    @Binds
    abstract fun bindMediaProcessor(impl: FFmpegMediaProcessor): MediaProcessor

    @Binds
    abstract fun bindDownloadStorage(impl: AndroidDownloadStorage): DownloadStorage

    @Binds
    abstract fun bindDownloadScheduler(impl: WorkManagerDownloadScheduler): DownloadWorkScheduler

    @Binds
    abstract fun bindDownloadNotificationManager(impl: AndroidDownloadNotificationManager): DownloadNotificationManager

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
