package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.nihaltp.sbskip.model.ProcessingPlan
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.storage.DownloadStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

open class MediaProcessingManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadStorage: DownloadStorage,
        private val mediaProcessor: MediaProcessor,
    ) {
        open suspend fun process(
            queueItemId: Long,
            localFileUri: String,
            localExtension: String,
            plan: ProcessingPlan,
            onProgress: (Int) -> Unit,
        ): File {
            val cacheDir = context.cacheDir
            val tempInputFile = File(cacheDir, "clean_in_$queueItemId.$localExtension")
            val tempOutputFile = File(cacheDir, "clean_out_$queueItemId.${plan.outputExtension}")

            try {
                // Copy the source into the cache. yt-dlp downloads land as plain files on disk,
                // while imported media arrives as SAF content URIs.
                if (localFileUri.startsWith("content://") || localFileUri.startsWith("file://")) {
                    downloadStorage.copyUriToTempFile(localFileUri, tempInputFile)
                } else {
                    withContext(Dispatchers.IO) {
                        File(localFileUri).copyTo(tempInputFile, overwrite = true)
                    }
                }

                if (plan.keepRanges.isEmpty() && !plan.convertVideoToAudio) {
                    onProgress(100)
                    tempInputFile.renameTo(tempOutputFile)
                    return tempOutputFile
                }

                // Process media with FFmpeg
                mediaProcessor.processMedia(
                    tempInputFile,
                    tempOutputFile,
                    plan.keepRanges,
                    plan.convertVideoToAudio,
                    onProgress,
                )

                return tempOutputFile
            } catch (t: Throwable) {
                if (tempOutputFile.exists()) {
                    tempOutputFile.delete()
                }
                throw t
            } finally {
                if (tempInputFile.exists()) {
                    tempInputFile.delete()
                }
            }
        }
    }
