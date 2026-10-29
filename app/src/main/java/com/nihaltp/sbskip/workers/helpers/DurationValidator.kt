package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.AppLogger
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

class DurationValidator
    @Inject
    constructor(
        private val downloadStorage: DownloadStorage,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun validate(
            localFileUri: String,
            youtubeDuration: Long?,
            bypassCheck: Boolean,
        ): MediaFileMetadata {
            val localMetadata =
                downloadStorage.queryMetadata(localFileUri)
                    ?: throw IOException("Failed to read local media file metadata")

            val fileDuration = localMetadata.durationSeconds ?: 0L

            if (bypassCheck) {
                AppLogger.worker("Duration mismatch verification bypassed via explicit user request.")
                return localMetadata
            }

            if (youtubeDuration != null) {
                val settings = settingsRepository.settings.first()
                val difference = kotlin.math.abs(fileDuration - youtubeDuration)
                val hasMismatch =
                    if (settings.bypassSmallDurationDifference) {
                        difference > settings.maxDurationDifferenceSeconds
                    } else {
                        difference > 0
                    }
                if (hasMismatch) {
                    throw IllegalStateException(
                        "Picked file duration ($fileDuration s) does not match YouTube video duration ($youtubeDuration s)",
                    )
                }
            } else {
                AppLogger.worker("YouTube video duration could not be fetched; skipping validation.")
            }

            return localMetadata
        }
    }
