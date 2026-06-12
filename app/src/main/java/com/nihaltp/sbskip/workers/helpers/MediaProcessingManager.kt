package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import com.nihaltp.sbskip.processing.MediaProcessor
import com.nihaltp.sbskip.storage.DownloadStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MediaProcessingManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadStorage: DownloadStorage,
        private val mediaProcessor: MediaProcessor,
    ) {
        suspend fun process(
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
                // Copy imported SAF Content URI file locally to cache for native random-access FFmpeg copy seeking
                downloadStorage.copyUriToTempFile(localFileUri, tempInputFile)

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
