package com.nihaltp.sbskip.di

import com.nihaltp.sbskip.data.local.database.SBSkipDatabase
import com.nihaltp.sbskip.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestEntryPoint {
    fun database(): SBSkipDatabase
    fun settingsRepository(): SettingsRepository
}
