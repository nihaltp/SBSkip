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
        private val httpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

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
                            AppLogger.worker("Failed to download artwork: HTTP ${response.code}")
                            return@withContext null
                        }
                        val body = response.body ?: return@withContext null
                        val contentType = body.contentType()?.toString()?.lowercase() ?: ""
                        if (!contentType.startsWith("image/")) {
                            AppLogger.worker("Failed to download artwork: invalid content type $contentType")
                            return@withContext null
                        }
                        val contentLength = body.contentLength()
                        if (contentLength > 5 * 1024 * 1024) { // 5MB limit
                            AppLogger.worker("Failed to download artwork: file too large ($contentLength bytes)")
                            return@withContext null
                        }

                        val extension = if (contentType.contains("png")) ".png" else ".jpg"
                        val tempFile = File.createTempFile("artwork_", extension, cacheDir)
                        tempFile.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }
                        tempFile
                    }
                } catch (e: Exception) {
                    AppLogger.error("CoverArtManager", e, "Failed to download artwork from $url")
                    null
                }
            }
    }
