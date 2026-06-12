package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import com.nihaltp.sbskip.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

class CoverArtManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val httpClient = OkHttpClient()

        fun audioHasCoverImage(file: File): Boolean {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val picture = retriever.embeddedPicture
                picture != null
            } catch (e: Exception) {
                AppLogger.error("CoverArtManager", e, "Failed to check embedded picture for ${file.name}")
                false
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {
                }
            }
        }

        suspend fun downloadThumbnail(
            url: String,
            cacheDir: File,
        ): File? =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            AppLogger.worker("Failed to download thumbnail: HTTP ${response.code}")
                            return@withContext null
                        }
                        val body = response.body ?: return@withContext null
                        val tempFile = File.createTempFile("thumb_", ".jpg", cacheDir)
                        tempFile.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }
                        tempFile
                    }
                } catch (e: Exception) {
                    AppLogger.error("CoverArtManager", e, "Failed to download thumbnail from $url")
                    null
                }
            }
    }
