package com.nihaltp.sbskip.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks whether the local files named by an imported backup still exist on this device.
 *
 * A backup can come from another device, so its paths are not trustworthy: a `content://` uri only
 * resolves while the underlying document provider still exposes it, and a plain path can point at a
 * file that was never copied over. Everything that cannot be opened is dropped, so a video whose
 * file is missing is imported as unverified instead of pretending to own a local file.
 */
@Singleton
class ImportPathVerifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * @return the paths that exist on this device, without blank or repeated entries.
         */
        suspend fun verify(paths: List<String>): List<String> =
            withContext(Dispatchers.IO) {
                paths
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .filter { path -> exists(path) }
            }

        private fun exists(path: String): Boolean {
            val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return false
            return when (uri.scheme) {
                null -> File(path).exists()

                ContentResolver.SCHEME_FILE -> uri.path?.let { file -> File(file).exists() } ?: false

                else ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { true } ?: false
                    }.getOrDefault(false)
            }
        }
    }
