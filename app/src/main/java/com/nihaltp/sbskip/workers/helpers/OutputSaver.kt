package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import javax.inject.Inject

class OutputSaver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadStorage: DownloadStorage,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun save(
            tempOutputFile: File,
            processingContext: ProcessingContext,
        ): String {
            val item = processingContext.queueItem
            val localMetadata = processingContext.localMetadata
            val plan = processingContext.plan
            val taskTitle = processingContext.oembedTitle ?: item.title.ifBlank { localMetadata.title }
            val settings = settingsRepository.settings.first()

            return if (item.convertVideoToAudio) {
                val outputSuffix = if (item.url.contains("noSuffix=true")) "" else settings.autoCleanSuffix
                val baseTitle =
                    if (taskTitle.endsWith(".${localMetadata.extension}", ignoreCase = true)) {
                        taskTitle.substring(0, taskTitle.length - localMetadata.extension.length - 1)
                    } else {
                        taskTitle
                    }
                val forceOverwrite = settings.overwriteBehavior || item.url.contains("overwrite=true")
                val savedUri =
                    downloadStorage.saveToPublicStorage(
                        tempFile = tempOutputFile,
                        title = "$baseTitle$outputSuffix",
                        extension = plan.outputExtension,
                        mediaType = MediaType.AUDIO,
                        customFolderUri = item.audioOutputDirUri,
                        overwrite = forceOverwrite,
                    )

                if (item.deleteOriginalVideo) {
                    val deleted = downloadStorage.deleteUri(item.localFileUri)
                    AppLogger.worker("Deleted original video file: ${item.localFileUri} status=$deleted")
                }

                savedUri
            } else if (settings.overwriteBehavior || item.url.contains("overwrite=true")) {
                val resolver = context.contentResolver
                val targetUri = android.net.Uri.parse(item.localFileUri)
                resolver.openOutputStream(targetUri, "w")?.use { output ->
                    java.io.FileInputStream(tempOutputFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Failed to open output stream for source file to overwrite")
                AppLogger.worker("Successfully overwrote the original file where it was picked from: ${item.localFileUri}")
                item.localFileUri
            } else {
                val outputSuffix = if (item.url.contains("noSuffix=true")) "" else settings.autoCleanSuffix
                val baseTitle =
                    if (taskTitle.endsWith(".${localMetadata.extension}", ignoreCase = true)) {
                        taskTitle.substring(0, taskTitle.length - localMetadata.extension.length - 1)
                    } else {
                        taskTitle
                    }
                downloadStorage.saveToPublicStorage(
                    tempFile = tempOutputFile,
                    title = "$baseTitle$outputSuffix",
                    extension = localMetadata.extension,
                    mediaType = item.mediaType,
                    customFolderUri =
                        if (item.mediaType == MediaType.AUDIO) {
                            item.audioOutputDirUri
                        } else {
                            null
                        },
                    overwrite = false,
                )
            }
        }
    }
