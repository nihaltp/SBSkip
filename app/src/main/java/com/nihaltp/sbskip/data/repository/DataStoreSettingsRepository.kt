package com.nihaltp.sbskip.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSettings
import com.nihaltp.sbskip.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val VIDEO_FOLDER = stringPreferencesKey("video_folder")
        val AUDIO_FOLDER = stringPreferencesKey("audio_folder")
        val TEMP_FOLDER = stringPreferencesKey("temp_folder")
        val SB_ENABLED = booleanPreferencesKey("sb_enabled")
        val SB_CATEGORIES = stringSetPreferencesKey("sb_categories")
        val FILENAME_REPLACEMENT = stringPreferencesKey("filename_replacement")
        val KEEP_TEMP_FILES = booleanPreferencesKey("keep_temp_files")
        val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
        val SPONSORBLOCK_URL = stringPreferencesKey("sponsorblock_url")
        val OVERWRITE_BEHAVIOR = booleanPreferencesKey("overwrite_behavior")
        val AUTO_CLEAN_SUFFIX = stringPreferencesKey("auto_clean_suffix")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val categories = preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
            runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
        }?.toSet() ?: SponsorBlockCategory.entries.toSet()

        AppSettings(
            themeMode = themeMode,
            dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
            notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
            videoFolder = preferences[PreferencesKeys.VIDEO_FOLDER] ?: "Movies/SB Skip/",
            audioFolder = preferences[PreferencesKeys.AUDIO_FOLDER] ?: "Music/SB Skip/",
            tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
            sponsorBlockSettings = SponsorBlockSettings(
                enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                categories = categories,
            ),
            filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
            keepTempFiles = preferences[PreferencesKeys.KEEP_TEMP_FILES] ?: false,
            verboseLogging = preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false,
            sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
            overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
            autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "_cleaned",
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val current = AppSettings(
                themeMode = runCatching {
                    ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
                notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                videoFolder = preferences[PreferencesKeys.VIDEO_FOLDER] ?: "Movies/SB Skip/",
                audioFolder = preferences[PreferencesKeys.AUDIO_FOLDER] ?: "Music/SB Skip/",
                tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
                sponsorBlockSettings = SponsorBlockSettings(
                    enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                    categories = preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
                        runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
                    }?.toSet() ?: SponsorBlockCategory.entries.toSet()
                ),
                filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
                keepTempFiles = preferences[PreferencesKeys.KEEP_TEMP_FILES] ?: false,
                verboseLogging = preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false,
                sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
                overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
                autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "_cleaned",
            )
            val updated = transform(current)

            preferences[PreferencesKeys.THEME_MODE] = updated.themeMode.name
            preferences[PreferencesKeys.DYNAMIC_COLOR] = updated.dynamicColor
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            preferences[PreferencesKeys.VIDEO_FOLDER] = updated.videoFolder
            preferences[PreferencesKeys.AUDIO_FOLDER] = updated.audioFolder
            preferences[PreferencesKeys.TEMP_FOLDER] = updated.tempFolder
            preferences[PreferencesKeys.SB_ENABLED] = updated.sponsorBlockSettings.enabled
            preferences[PreferencesKeys.SB_CATEGORIES] = updated.sponsorBlockSettings.categories.map { it.name }.toSet()
            preferences[PreferencesKeys.FILENAME_REPLACEMENT] = updated.filenameReplacement.toString()
            preferences[PreferencesKeys.KEEP_TEMP_FILES] = updated.keepTempFiles
            preferences[PreferencesKeys.VERBOSE_LOGGING] = updated.verboseLogging
            preferences[PreferencesKeys.SPONSORBLOCK_URL] = updated.sponsorBlockUrl
            preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] = updated.overwriteBehavior
            preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] = updated.autoCleanSuffix
        }
    }
}
