package com.nihaltp.sbskip.util.ytdlp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.yausername.youtubedl_android.R as YtDlpR

/** Thrown when the bundled yt-dlp runtime payloads cannot be installed. */
class YtDlpInitException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** SHA-256 stamps of the installed runtime payloads; persisted as `.versions.json`. */
@Serializable
data class YtDlpVersions(
    val python: String,
    val ffmpeg: String,
    val ytdlp: String,
)

/** Absolute locations of the installed yt-dlp runtime pieces (plan §4.1). */
data class YtDlpPaths(
    val baseDir: File,
    val pythonRoot: File,
    val ffmpegRoot: File,
    val pythonLauncher: File,
    val ffmpegLauncher: File,
    val ffprobeLauncher: File,
    val launcherDir: File,
    val zipapp: File,
)

/**
 * Bootstraps the bundled yt-dlp runtime (JunkFood02 payloads, Shape (a) / plan §4).
 *
 * Launchers (`libpython.so`, `libffmpeg.so`, `libffprobe.so`) are already extracted by the system
 * into `applicationInfo.nativeLibraryDir` (exec strategy E1, `useLegacyPackaging = true`). The
 * rootfs zip payloads (`libpython.zip.so`, `libffmpeg.zip.so`) and the yt-dlp zipapp
 * (`res/raw/ytdlp`) are materialised into `noBackupFilesDir` here.
 *
 * Extraction is idempotent and version-stamped with per-payload SHA-256 hashes; a stamp mismatch
 * (e.g. an app update ships new payloads) re-extracts that package cleanly. The yt-dlp zipapp is
 * content-hashed identically so bundle bumps propagate without touching user data.
 */
@Singleton
class YtDlpRuntime
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        private val mutex = Mutex()

        /** Injectable seam for tests; production reads the yt-dlp zipapp bundled in the AAR. */
        internal var zipappProvider: (Context) -> InputStream = {
            // res/raw/ytdlp ships inside the JunkFood02 library AAR; its id lives in that module's
            // namespaced R (the project uses non-transitive R classes).
            it.resources.openRawResource(YtDlpR.raw.ytdlp)
        }

        /**
         * Installs/validates the runtime and returns its paths. Safe to call from any coroutine;
         * extraction runs at most once per payload version, guarded by a VM-wide mutex.
         */
        suspend fun ensure(context: Context = appContext): YtDlpPaths =
            mutex.withLock {
                val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
                val baseDir = File(context.noBackupFilesDir, RUNTIME_DIR)
                val packagesDir = File(baseDir, PACKAGES_DIR)
                val pythonRoot = File(packagesDir, PYTHON_DIR)
                val ffmpegRoot = File(packagesDir, FFMPEG_DIR)
                val zipappDir = File(baseDir, YTDLP_DIR)
                val zipappFile = File(zipappDir, YTDLP_BIN)

                val payloadPython = File(nativeLibDir, PYTHON_ZIP)
                val payloadFfmpeg = File(nativeLibDir, FFMPEG_ZIP)
                val expectedZipapp = zipappProvider(context).use { sha256(it) }
                val expectedPython = sha256(payloadPython)
                val expectedFfmpeg = sha256(payloadFfmpeg)

                val versionsFile = File(baseDir, VERSIONS_FILE)
                var stamps = readVersions(versionsFile) ?: YtDlpVersions(python = "", ffmpeg = "", ytdlp = "")

                if (!pythonRoot.isDirectory || stamps.python != expectedPython) {
                    installPackage(payloadPython, pythonRoot)
                    stamps = stamps.copy(python = expectedPython)
                }
                if (!ffmpegRoot.isDirectory || stamps.ffmpeg != expectedFfmpeg) {
                    installPackage(payloadFfmpeg, ffmpegRoot)
                    stamps = stamps.copy(ffmpeg = expectedFfmpeg)
                }
                if (!zipappFile.isFile || stamps.ytdlp != expectedZipapp) {
                    installZipapp(zipappProvider(context), zipappDir, zipappFile)
                    stamps = stamps.copy(ytdlp = expectedZipapp)
                }
                if (stamps != readVersions(versionsFile)) {
                    writeVersions(versionsFile, stamps)
                }

                YtDlpPaths(
                    baseDir = baseDir,
                    pythonRoot = pythonRoot,
                    ffmpegRoot = ffmpegRoot,
                    pythonLauncher = File(nativeLibDir, PYTHON_LAUNCHER),
                    ffmpegLauncher = File(nativeLibDir, FFMPEG_LAUNCHER),
                    ffprobeLauncher = File(nativeLibDir, FFPROBE_LAUNCHER),
                    launcherDir = nativeLibDir,
                    zipapp = zipappFile,
                )
            }

        /**
         * Reinstalls [dest] from the rootfs zip [payload] into a temp directory that is only moved
         * into place after a successful extraction, so a corrupted payload never destroys a working
         * runtime and a failed extraction never half-replaces the fresh copy.
         */
        private fun installPackage(
            payload: File,
            dest: File,
        ) {
            val tmp = File(dest.parentFile, dest.name + ".tmp-" + System.currentTimeMillis())
            try {
                tmp.mkdirs()
                unzipRootfs(payload, tmp)
            } catch (e: Exception) {
                tmp.deleteRecursively()
                throw YtDlpInitException("Failed to install ${dest.name} runtime", e)
            }
            if (!dest.deleteRecursively() && dest.exists()) {
                tmp.deleteRecursively()
                throw YtDlpInitException("Failed to replace existing ${dest.name} runtime")
            }
            if (!tmp.renameTo(dest)) {
                tmp.deleteRecursively()
                throw YtDlpInitException("Failed to move ${dest.name} runtime into place")
            }
        }

        private fun installZipapp(
            source: InputStream,
            zipappDir: File,
            zipappFile: File,
        ) {
            try {
                zipappDir.mkdirs()
                zipappFile.outputStream().use { out -> source.use { it.copyTo(out) } }
            } catch (e: Exception) {
                zipappFile.delete()
                zipappDir.deleteRecursively()
                throw YtDlpInitException("Failed to install yt-dlp zipapp", e)
            }
        }

        /** Runtime environment table (plan §4.1) applied to spawned yt-dlp processes. */
        fun environment(
            paths: YtDlpPaths,
            tmpRoot: File,
        ): Map<String, String> =
            mapOf(
                "LD_LIBRARY_PATH" to
                    "${paths.pythonRoot.absolutePath}/usr/lib:${paths.ffmpegRoot.absolutePath}/usr/lib",
                "SSL_CERT_FILE" to "${paths.pythonRoot.absolutePath}/usr/etc/tls/cert.pem",
                "PYTHONHOME" to "${paths.pythonRoot.absolutePath}/usr",
                "HOME" to "${paths.pythonRoot.absolutePath}/usr",
                "TMPDIR" to tmpRoot.absolutePath,
            )

        /** Work dir for a queue item: `<cacheDir>/ytdlp-tmp/<queueItemId>/` (plan §4.1). */
        fun workDir(
            cacheDir: File,
            queueItemId: Long,
        ): File = File(File(cacheDir, WORK_DIR_ROOT), queueItemId.toString())

        /** Deletes all per-download work dirs; sweeps orphans left by killed workers. */
        fun cleanStaleWorkDirs(cacheDir: File) {
            File(cacheDir, WORK_DIR_ROOT).deleteRecursively()
        }

        companion object {
            const val RUNTIME_DIR = "ytdlp-runtime"
            const val PACKAGES_DIR = "packages"
            const val PYTHON_DIR = "python"
            const val FFMPEG_DIR = "ffmpeg"
            const val YTDLP_DIR = "yt-dlp"
            const val YTDLP_BIN = "yt-dlp"
            const val VERSIONS_FILE = ".versions.json"
            const val WORK_DIR_ROOT = "ytdlp-tmp"

            const val PYTHON_ZIP = "libpython.zip.so"
            const val FFMPEG_ZIP = "libffmpeg.zip.so"
            const val FFMPEG_LAUNCHER = "libffmpeg.so"
            const val FFPROBE_LAUNCHER = "libffprobe.so"
            const val PYTHON_LAUNCHER = "libpython.so"

            private val json = Json { ignoreUnknownKeys = true }

            fun readVersions(file: File): YtDlpVersions? {
                if (!file.exists()) return null
                return runCatching {
                    json.decodeFromString(YtDlpVersions.serializer(), file.readText())
                }.getOrNull()
            }

            fun writeVersions(
                file: File,
                versions: YtDlpVersions,
            ) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(YtDlpVersions.serializer(), versions))
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                }
            }

            fun sha256(file: File): String =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }

            fun sha256(stream: InputStream): String {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                stream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }

/**
 * Symlink-aware rootfs unzip (plan §4.1).
 *
 * Mirrors upstream youtubedl-android's [ZipUtils] contract: Unix symlink entries inside the
 * Termux rootfs bundles (e.g. `usr/lib/libpython3.12.so -> libpython3.12.so.1.0`) are recreated
 * as real symlinks via `java.nio.file.Files.createSymbolicLink` (Android API 26+; also works
 * under Robolectric, where `android.system.Os.symlink` is stubbed). Like upstream, file
 * permission bits are not restored — the runtime only needs files readable and the launchers
 * (`nativeLibraryDir`) were made exec-able by the OS itself.
 */
private fun unzipRootfs(
    payload: File,
    target: File,
) {
    val targetCanonical = target.canonicalPath + File.separator
    ZipFile(payload).use { zipFile ->
        val entries: java.util.Enumeration<ZipArchiveEntry> = zipFile.entries
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryDestination = File(target, entry.name)
            if (!entryDestination.canonicalPath.startsWith(targetCanonical)) {
                throw YtDlpInitException("Zip entry escapes the target dir: ${entry.name}")
            }
            when {
                entry.isDirectory -> entryDestination.mkdirs()
                entry.isUnixSymlink -> {
                    val linkTarget =
                        zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
                    entryDestination.parentFile?.mkdirs()
                    try {
                        Files.createSymbolicLink(entryDestination.toPath(), Path.of(linkTarget))
                    } catch (e: FileAlreadyExistsException) {
                        entryDestination.delete()
                        Files.createSymbolicLink(entryDestination.toPath(), Path.of(linkTarget))
                    }
                }
                else -> {
                    entryDestination.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(entryDestination).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
