package com.nihaltp.sbskip.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ClipboardHelper {
    suspend fun copyImageToClipboard(
        context: Context,
        imageUrl: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = context.imageLoader
                val request =
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false) // Safe software bitmap creation
                        .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        return@withContext saveAndCopyBitmap(context, bitmap)
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("ClipboardHelper", e, "Failed to load/copy thumbnail image")
            }
            return@withContext false
        }

    private fun saveAndCopyBitmap(
        context: Context,
        bitmap: Bitmap,
    ): Boolean {
        try {
            val sharedImagesDir = File(context.cacheDir, "shared_images")
            if (!sharedImagesDir.exists()) {
                sharedImagesDir.mkdirs()
            }
            val file = File(sharedImagesDir, "thumbnail.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newUri(context.contentResolver, "Thumbnail Image", uri)

            clipboard.setPrimaryClip(clipData)
            return true
        } catch (e: Exception) {
            AppLogger.error("ClipboardHelper", e, "Failed to save/copy bitmap")
        }
        return false
    }
}
