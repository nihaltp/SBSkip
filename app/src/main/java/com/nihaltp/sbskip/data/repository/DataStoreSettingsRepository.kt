package com.nihaltp.sbskip.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.AudioSaveMode
import com.nihaltp.sbskip.model.DownloaderType
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSettings
import com.nihaltp.sbskip.model.ThemeMode
import com.nihaltp.sbskip.model.WatchlistFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val json = Json { ignoreUnknownKeys = true }

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsRepository {
        private object PreferencesKeys {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
            val DOWNLOADER = stringPreferencesKey("downloader")
            val VIDEO_FOLDER = stringPreferencesKey("video_folder")
            val AUDIO_FOLDER = stringPreferencesKey("audio_folder")
            val VIDEO_FOLDER_URI = stringPreferencesKey("video_folder_uri")
            val AUDIO_FOLDER_URI = stringPreferencesKey("audio_folder_uri")
            val TEMP_FOLDER = stringPreferencesKey("temp_folder")
            val SB_ENABLED = booleanPreferencesKey("sb_enabled")
            val SB_CATEGORIES = stringSetPreferencesKey("sb_categories")
            val FILENAME_REPLACEMENT = stringPreferencesKey("filename_replacement")
            val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
            val SPONSORBLOCK_URL = stringPreferencesKey("sponsorblock_url")
            val OVERWRITE_BEHAVIOR = booleanPreferencesKey("overwrite_behavior")
            val AUTO_CLEAN_SUFFIX = stringPreferencesKey("auto_clean_suffix")
            val SPONSORBLOCK_STATUS_URL = stringPreferencesKey("sponsorblock_status_url")
            val DEFAULT_CONVERT_VIDEO_TO_AUDIO = booleanPreferencesKey("default_convert_video_to_audio")
            val DEFAULT_DELETE_ORIGINAL_VIDEO = booleanPreferencesKey("default_delete_original_video")
            val AUDIO_SAVE_MODE = stringPreferencesKey("audio_save_mode")
            val BYPASS_SMALL_DURATION_DIFFERENCE = booleanPreferencesKey("bypass_small_duration_difference")
            val MAX_DURATION_DIFFERENCE_SECONDS = intPreferencesKey("max_duration_difference_seconds")
            val WATCHLIST = stringPreferencesKey("watchlist")
        }

        override val settings: Flow<AppSettings> =
            context.dataStore.data.map { preferences ->
                val themeMode =
                    runCatching {
                        ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                    }.getOrDefault(ThemeMode.SYSTEM)

                val downloader =
                    runCatching {
                        DownloaderType.valueOf(preferences[PreferencesKeys.DOWNLOADER] ?: DownloaderType.NEWPIPE.name)
                    }.getOrDefault(DownloaderType.NEWPIPE)

                val audioSaveMode =
                    runCatching {
                        AudioSaveMode.valueOf(preferences[PreferencesKeys.AUDIO_SAVE_MODE] ?: AudioSaveMode.RUNTIME_PICKER.name)
                    }.getOrDefault(AudioSaveMode.RUNTIME_PICKER)

                val categories =
                    preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
                        runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
                    }?.toSet() ?: SponsorBlockCategory.entries.toSet()

                AppSettings(
                    themeMode = themeMode,
                    dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
                    notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                    downloader = downloader,
                    videoFolder = preferences[PreferencesKeys.VIDEO_FOLDER] ?: "Movies/SB Skip/",
                    audioFolder = preferences[PreferencesKeys.AUDIO_FOLDER] ?: "Music/SB Skip/",
                    videoFolderUri = preferences[PreferencesKeys.VIDEO_FOLDER_URI] ?: "",
                    audioFolderUri = preferences[PreferencesKeys.AUDIO_FOLDER_URI] ?: "",
                    tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
                    sponsorBlockSettings =
                        SponsorBlockSettings(
                            enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                            categories = categories,
                        ),
                    filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
                    verboseLogging =
                        (preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false).also {
                            com.nihaltp.sbskip.util.AppLogger.isVerboseLoggingEnabled = it
                        },
                    sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
                    sponsorBlockStatusUrl = preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] ?: "https://status.sponsor.ajay.app",
                    overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
                    autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "",
                    defaultConvertVideoToAudio = preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] ?: false,
                    defaultDeleteOriginalVideo = preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] ?: true,
                    audioSaveMode = audioSaveMode,
                    bypassSmallDurationDifference = preferences[PreferencesKeys.BYPASS_SMALL_DURATION_DIFFERENCE] ?: false,
                    maxDurationDifferenceSeconds = preferences[PreferencesKeys.MAX_DURATION_DIFFERENCE_SECONDS] ?: 1,
                    watchlist =
                        runCatching {
                            json.decodeFromString<List<WatchlistFolder>>(preferences[PreferencesKeys.WATCHLIST] ?: "[]")
                        }.getOrElse { emptyList() },
                )
            }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            context.dataStore.edit { preferences ->
                val current =
                    AppSettings(
                        themeMode =
                            runCatching {
                                ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                            }.getOrDefault(ThemeMode.SYSTEM),
                        dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
                        notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                        downloader =
                            runCatching {
                                DownloaderType.valueOf(preferences[PreferencesKeys.DOWNLOADER] ?: DownloaderType.NEWPIPE.name)
                            }.getOrDefault(DownloaderType.NEWPIPE),
                        videoFolder = preferences[PreferencesKeys.VIDEO_FOLDER] ?: "Movies/SB Skip/",
                        audioFolder = preferences[PreferencesKeys.AUDIO_FOLDER] ?: "Music/SB Skip/",
                        videoFolderUri = preferences[PreferencesKeys.VIDEO_FOLDER_URI] ?: "",
                        audioFolderUri = preferences[PreferencesKeys.AUDIO_FOLDER_URI] ?: "",
                        tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
                        sponsorBlockSettings =
                            SponsorBlockSettings(
                                enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                                categories =
                                    preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
                                        runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
                                    }?.toSet() ?: SponsorBlockCategory.entries.toSet(),
                            ),
                        filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
                        verboseLogging =
                            (preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false).also {
                                com.nihaltp.sbskip.util.AppLogger.isVerboseLoggingEnabled = it
                            },
                        sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
                        sponsorBlockStatusUrl = preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] ?: "https://status.sponsor.ajay.app",
                        overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
                        autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "",
                        defaultConvertVideoToAudio = preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] ?: false,
                        defaultDeleteOriginalVideo = preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] ?: true,
                        audioSaveMode =
                            runCatching {
                                AudioSaveMode.valueOf(preferences[PreferencesKeys.AUDIO_SAVE_MODE] ?: AudioSaveMode.RUNTIME_PICKER.name)
                            }.getOrDefault(AudioSaveMode.RUNTIME_PICKER),
                        bypassSmallDurationDifference = preferences[PreferencesKeys.BYPASS_SMALL_DURATION_DIFFERENCE] ?: false,
                        maxDurationDifferenceSeconds = preferences[PreferencesKeys.MAX_DURATION_DIFFERENCE_SECONDS] ?: 1,
                        watchlist =
                            runCatching {
                                json.decodeFromString<List<WatchlistFolder>>(preferences[PreferencesKeys.WATCHLIST] ?: "[]")
                            }.getOrElse { emptyList() },
                    )
                val updated = transform(current)

                preferences[PreferencesKeys.THEME_MODE] = updated.themeMode.name
                preferences[PreferencesKeys.DYNAMIC_COLOR] = updated.dynamicColor
                preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
                preferences[PreferencesKeys.DOWNLOADER] = updated.downloader.name
                preferences[PreferencesKeys.VIDEO_FOLDER] = updated.videoFolder
                preferences[PreferencesKeys.AUDIO_FOLDER] = updated.audioFolder
                preferences[PreferencesKeys.VIDEO_FOLDER_URI] = updated.videoFolderUri
                preferences[PreferencesKeys.AUDIO_FOLDER_URI] = updated.audioFolderUri
                preferences[PreferencesKeys.TEMP_FOLDER] = updated.tempFolder
                preferences[PreferencesKeys.SB_ENABLED] = updated.sponsorBlockSettings.enabled
                preferences[PreferencesKeys.SB_CATEGORIES] = updated.sponsorBlockSettings.categories.map { it.name }.toSet()
                preferences[PreferencesKeys.FILENAME_REPLACEMENT] = updated.filenameReplacement.toString()
                preferences[PreferencesKeys.VERBOSE_LOGGING] = updated.verboseLogging
                preferences[PreferencesKeys.SPONSORBLOCK_URL] = updated.sponsorBlockUrl
                preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] = updated.sponsorBlockStatusUrl
                preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] = updated.overwriteBehavior
                preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] = updated.autoCleanSuffix
                preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] = updated.defaultConvertVideoToAudio
                preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] = updated.defaultDeleteOriginalVideo
                preferences[PreferencesKeys.AUDIO_SAVE_MODE] = updated.audioSaveMode.name
                preferences[PreferencesKeys.BYPASS_SMALL_DURATION_DIFFERENCE] = updated.bypassSmallDurationDifference
                preferences[PreferencesKeys.MAX_DURATION_DIFFERENCE_SECONDS] = updated.maxDurationDifferenceSeconds
                preferences[PreferencesKeys.WATCHLIST] = json.encodeToString(updated.watchlist)
            }
        }
    }
