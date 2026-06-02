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
import com.nihaltp.sbskip.model.AudioSaveMode
import com.nihaltp.sbskip.model.DownloaderType
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
        val DOWNLOADER = stringPreferencesKey("downloader")
        val VIDEO_FOLDER = stringPreferencesKey("video_folder")
        val AUDIO_FOLDER = stringPreferencesKey("audio_folder")
        val VIDEO_FOLDER_URI = stringPreferencesKey("video_folder_uri")
        val AUDIO_FOLDER_URI = stringPreferencesKey("audio_folder_uri")
        val NEWPIPE_VIDEO_FOLDER = stringPreferencesKey("newpipe_video_folder")
        val NEWPIPE_AUDIO_FOLDER = stringPreferencesKey("newpipe_audio_folder")
        val NEWPIPE_VIDEO_FOLDER_URI = stringPreferencesKey("newpipe_video_folder_uri")
        val NEWPIPE_AUDIO_FOLDER_URI = stringPreferencesKey("newpipe_audio_folder_uri")
        val TEMP_FOLDER = stringPreferencesKey("temp_folder")
        val SB_ENABLED = booleanPreferencesKey("sb_enabled")
        val SB_CATEGORIES = stringSetPreferencesKey("sb_categories")
        val FILENAME_REPLACEMENT = stringPreferencesKey("filename_replacement")
        val KEEP_TEMP_FILES = booleanPreferencesKey("keep_temp_files")
        val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
        val SPONSORBLOCK_URL = stringPreferencesKey("sponsorblock_url")
        val OVERWRITE_BEHAVIOR = booleanPreferencesKey("overwrite_behavior")
        val AUTO_CLEAN_SUFFIX = stringPreferencesKey("auto_clean_suffix")
        val SPONSORBLOCK_STATUS_URL = stringPreferencesKey("sponsorblock_status_url")
        val DEFAULT_CONVERT_VIDEO_TO_AUDIO = booleanPreferencesKey("default_convert_video_to_audio")
        val DEFAULT_DELETE_ORIGINAL_VIDEO = booleanPreferencesKey("default_delete_original_video")
        val AUDIO_SAVE_MODE = stringPreferencesKey("audio_save_mode")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val downloader = runCatching {
            DownloaderType.valueOf(preferences[PreferencesKeys.DOWNLOADER] ?: DownloaderType.NEWPIPE.name)
        }.getOrDefault(DownloaderType.NEWPIPE)

        val audioSaveMode = runCatching {
            AudioSaveMode.valueOf(preferences[PreferencesKeys.AUDIO_SAVE_MODE] ?: AudioSaveMode.PRESET_FOLDER.name)
        }.getOrDefault(AudioSaveMode.PRESET_FOLDER)

        val categories = preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
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
            newPipeVideoFolder = preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER] ?: "Download/NewPipe/Video/",
            newPipeAudioFolder = preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER] ?: "Download/NewPipe/Audio/",
            newPipeVideoFolderUri = preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER_URI] ?: "",
            newPipeAudioFolderUri = preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER_URI] ?: "",
            tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
            sponsorBlockSettings = SponsorBlockSettings(
                enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                categories = categories,
            ),
            filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
            keepTempFiles = preferences[PreferencesKeys.KEEP_TEMP_FILES] ?: false,
            verboseLogging = preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false,
            sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
            sponsorBlockStatusUrl = preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] ?: "https://status.sponsor.ajay.app",
            overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
            autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "_cleaned",
            defaultConvertVideoToAudio = preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] ?: false,
            defaultDeleteOriginalVideo = preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] ?: true,
            audioSaveMode = audioSaveMode,
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
                downloader = runCatching {
                    DownloaderType.valueOf(preferences[PreferencesKeys.DOWNLOADER] ?: DownloaderType.NEWPIPE.name)
                }.getOrDefault(DownloaderType.NEWPIPE),
                videoFolder = preferences[PreferencesKeys.VIDEO_FOLDER] ?: "Movies/SB Skip/",
                audioFolder = preferences[PreferencesKeys.AUDIO_FOLDER] ?: "Music/SB Skip/",
                videoFolderUri = preferences[PreferencesKeys.VIDEO_FOLDER_URI] ?: "",
                audioFolderUri = preferences[PreferencesKeys.AUDIO_FOLDER_URI] ?: "",
                newPipeVideoFolder = preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER] ?: "Download/NewPipe/Video/",
                newPipeAudioFolder = preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER] ?: "Download/NewPipe/Audio/",
                newPipeVideoFolderUri = preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER_URI] ?: "",
                newPipeAudioFolderUri = preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER_URI] ?: "",
                tempFolder = preferences[PreferencesKeys.TEMP_FOLDER] ?: "SB Skip/tmp/",
                sponsorBlockSettings = SponsorBlockSettings(
                    enabled = preferences[PreferencesKeys.SB_ENABLED] ?: true,
                    categories = preferences[PreferencesKeys.SB_CATEGORIES]?.mapNotNull { name ->
                        runCatching { SponsorBlockCategory.valueOf(name) }.getOrNull()
                    }?.toSet() ?: SponsorBlockCategory.entries.toSet(),
                ),
                filenameReplacement = preferences[PreferencesKeys.FILENAME_REPLACEMENT]?.firstOrNull() ?: '_',
                keepTempFiles = preferences[PreferencesKeys.KEEP_TEMP_FILES] ?: false,
                verboseLogging = preferences[PreferencesKeys.VERBOSE_LOGGING] ?: false,
                sponsorBlockUrl = preferences[PreferencesKeys.SPONSORBLOCK_URL] ?: "https://sponsor.ajay.app",
                sponsorBlockStatusUrl = preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] ?: "https://status.sponsor.ajay.app",
                overwriteBehavior = preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] ?: true,
                autoCleanSuffix = preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] ?: "_cleaned",
                defaultConvertVideoToAudio = preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] ?: false,
                defaultDeleteOriginalVideo = preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] ?: true,
                audioSaveMode = runCatching {
                    AudioSaveMode.valueOf(preferences[PreferencesKeys.AUDIO_SAVE_MODE] ?: AudioSaveMode.PRESET_FOLDER.name)
                }.getOrDefault(AudioSaveMode.PRESET_FOLDER),
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
            preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER] = updated.newPipeVideoFolder
            preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER] = updated.newPipeAudioFolder
            preferences[PreferencesKeys.NEWPIPE_VIDEO_FOLDER_URI] = updated.newPipeVideoFolderUri
            preferences[PreferencesKeys.NEWPIPE_AUDIO_FOLDER_URI] = updated.newPipeAudioFolderUri
            preferences[PreferencesKeys.TEMP_FOLDER] = updated.tempFolder
            preferences[PreferencesKeys.SB_ENABLED] = updated.sponsorBlockSettings.enabled
            preferences[PreferencesKeys.SB_CATEGORIES] = updated.sponsorBlockSettings.categories.map { it.name }.toSet()
            preferences[PreferencesKeys.FILENAME_REPLACEMENT] = updated.filenameReplacement.toString()
            preferences[PreferencesKeys.KEEP_TEMP_FILES] = updated.keepTempFiles
            preferences[PreferencesKeys.VERBOSE_LOGGING] = updated.verboseLogging
            preferences[PreferencesKeys.SPONSORBLOCK_URL] = updated.sponsorBlockUrl
            preferences[PreferencesKeys.SPONSORBLOCK_STATUS_URL] = updated.sponsorBlockStatusUrl
            preferences[PreferencesKeys.OVERWRITE_BEHAVIOR] = updated.overwriteBehavior
            preferences[PreferencesKeys.AUTO_CLEAN_SUFFIX] = updated.autoCleanSuffix
            preferences[PreferencesKeys.DEFAULT_CONVERT_VIDEO_TO_AUDIO] = updated.defaultConvertVideoToAudio
            preferences[PreferencesKeys.DEFAULT_DELETE_ORIGINAL_VIDEO] = updated.defaultDeleteOriginalVideo
            preferences[PreferencesKeys.AUDIO_SAVE_MODE] = updated.audioSaveMode.name
        }
    }
}
