package com.nihaltp.sbskip

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.ThemeMode
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.notifications.ImportNotificationManager
import com.nihaltp.sbskip.util.ImportManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class SBSkipApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var importManager: ImportManager

    @Inject
    lateinit var downloadNotificationManager: DownloadNotificationManager

    @Inject
    lateinit var importNotificationManager: ImportNotificationManager

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        com.nihaltp.sbskip.util.AppLogger.init(this)

        // Create the notification channels up front, so that the first notification is always
        // posted on an existing (silent) channel.
        downloadNotificationManager.ensureChannel()
        importNotificationManager.ensureChannel()

        runBlocking {
            val initialSettings = settingsRepository.settings.first()
            val nightMode =
                when (initialSettings.themeMode) {
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        // Continue the folder imports that were running when the app was killed.
        importManager.resumePersistedImports()

        GlobalScope.launch(Dispatchers.Main) {
            settingsRepository.settings.collect { settings ->
                val nightMode =
                    when (settings.themeMode) {
                        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
