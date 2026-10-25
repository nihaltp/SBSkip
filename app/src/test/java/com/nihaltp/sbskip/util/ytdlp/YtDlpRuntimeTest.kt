package com.nihaltp.sbskip.util.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * Exercises [YtDlpRuntime.ensure] against synthetic payload zips (plan §10): symlink entries in
 * the python rootfs must materialise as real symlinks, extraction must be idempotent and
 * version-stamped, and a corrupted payload must fail without leaving a partial or stamped runtime.
 */
@RunWith(RobolectricTestRunner::class)
class YtDlpRuntimeTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val zipappBytes = "PK_ZIPAPP_FAKE_YTDLP".toByteArray()

    private lateinit var nativeLibDir: File
    private lateinit var runtime: YtDlpRuntime

    @Before
    fun setUp() {
        nativeLibDir = File(appContext.filesDir, "ytdlp-test-nativelib-" + System.nanoTime())
        nativeLibDir.mkdirs()
        appContext.applicationInfo.nativeLibraryDir = nativeLibDir.absolutePath
        runtime = YtDlpRuntime(appContext)
        runtime.zipappProvider = { ByteArrayInputStream(zipappBytes) }
    }

    @After
    fun tearDown() {
        nativeLibDir.deleteRecursively()
        File(appContext.noBackupFilesDir, YtDlpRuntime.RUNTIME_DIR).deleteRecursively()
    }

    @Test
    fun `ensure extracts both packages and recreates the rootfs symlink`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        val libDir = File(paths.pythonRoot, "usr/lib")
        val symlink = File(libDir, "libpython3.12.so")
        val symlinkTarget = File(libDir, "libpython3.12.so.1.0")
        assertTrue("python rootfs extracted", symlinkTarget.isFile)
        assertTrue("symlink must stay a symlink, not a file copy", Files.isSymbolicLink(symlink.toPath()))
        assertEquals(
            "libpython3.12.so.1.0",
            Files.readSymbolicLink(symlink.toPath()).toString(),
        )
        assertEquals("elf-fake-python", symlinkTarget.readText())
        val cert = File(paths.pythonRoot, "usr/etc/tls/cert.pem")
        assertTrue(cert.isFile)

        val ffmpegLib = File(paths.ffmpegRoot, "usr/lib/libavcodec.so")
        assertTrue(ffmpegLib.isFile)
        assertEquals("elf-fake-ffmpeg", ffmpegLib.readText())

        org.junit.Assert.assertArrayEquals(zipappBytes, paths.zipapp.readBytes())

        val versions = YtDlpRuntime.readVersions(File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE))!!
        assertEquals(YtDlpRuntime.sha256(paths.zipapp), versions.ytdlp)
        assertEquals(
            YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP)),
            versions.python,
        )
        assertEquals(
            YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.FFMPEG_ZIP)),
            versions.ffmpeg,
        )

        val tmpRoot = File(paths.baseDir, "tmp")
        val env = runtime.environment(paths, tmpRoot)
        assertEquals(
            "${paths.pythonRoot.absolutePath}/usr/lib:${paths.ffmpegRoot.absolutePath}/usr/lib",
            env["LD_LIBRARY_PATH"],
        )
        assertEquals("${paths.pythonRoot.absolutePath}/usr", env["PYTHONHOME"])
        assertEquals("${paths.pythonRoot.absolutePath}/usr", env["HOME"])
        assertEquals("${paths.pythonRoot.absolutePath}/usr/etc/tls/cert.pem", env["SSL_CERT_FILE"])
        assertEquals(tmpRoot.absolutePath, env["TMPDIR"])
    }

    @Test
    fun `ensure is idempotent while stamps match`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()
        val paths = runBlocking { runtime.ensure(appContext) }
        val marker = File(paths.pythonRoot, "usr/ensure-idempotency-marker")
        marker.writeText("sentinel")
        val stampsBefore = File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE).readText()

        runBlocking { runtime.ensure(appContext) }

        assertTrue("second ensure must not re-extract python", marker.exists())
        assertEquals(stampsBefore, File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE).readText())
    }

    @Test
    fun `stale stamp triggers re-extraction of that package only`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()
        val paths = runBlocking { runtime.ensure(appContext) }
        val staleStamp =
            YtDlpVersions(
                python = INVALID_STAMP,
                ffmpeg = YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.FFMPEG_ZIP)),
                ytdlp = YtDlpRuntime.sha256(paths.zipapp),
            )
        YtDlpRuntime.writeVersions(File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE), staleStamp)
        val marker = File(paths.pythonRoot, "usr/stale-stamp-marker")
        marker.writeText("sentinel")

        runBlocking { runtime.ensure(appContext) }

        assertFalse("python must be re-extracted on stamp mismatch", marker.exists())
        val versions = YtDlpRuntime.readVersions(File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE))!!
        assertEquals(
            YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP)),
            versions.python,
        )
        assertEquals("ffmpeg stamp survives", staleStamp.ffmpeg, versions.ffmpeg)
        assertEquals("zipapp stamp survives", staleStamp.ytdlp, versions.ytdlp)
    }

    @Test
    fun `corrupted payload fails cleanly without stamping a partial runtime`() {
        File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP).writeText("this is not a zip file")
        writeFfmpegPayloadZip()

        val exception =
            assertThrows(YtDlpInitException::class.java) {
                runBlocking { runtime.ensure(appContext) }
            }

        assertTrue(exception.message!!.contains("runtime"))
        val baseDir = File(appContext.noBackupFilesDir, YtDlpRuntime.RUNTIME_DIR)
        assertFalse(File(baseDir, YtDlpRuntime.VERSIONS_FILE).exists())
        val packagesDir = File(baseDir, YtDlpRuntime.PACKAGES_DIR)
        assertFalse(File(packagesDir, YtDlpRuntime.PYTHON_DIR).exists())
        assertFalse(
            "no tmp dir remains",
            packagesDir.isDirectory &&
                packagesDir.listFiles().orEmpty().any { it.name.contains(".tmp-") },
        )
    }

    private fun writePythonPayloadZip() {
        writePayloadZip(
            out = File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP),
            files =
                mapOf(
                    "usr/lib/libpython3.12.so.1.0" to "elf-fake-python",
                    "usr/etc/tls/cert.pem" to "-----BEGIN CERTIFICATE-----",
                ),
            symlinks = listOf("usr/lib/libpython3.12.so" to "libpython3.12.so.1.0"),
        )
    }

    private fun writeFfmpegPayloadZip() {
        writePayloadZip(
            out = File(nativeLibDir, YtDlpRuntime.FFMPEG_ZIP),
            files = mapOf("usr/lib/libavcodec.so" to "elf-fake-ffmpeg"),
            symlinks = emptyList(),
        )
    }

    /**
     * Writes a rootfs-style payload zip with commons-compress so symlink entries carry the Unix
     * link flag exactly like the Termux rootfs bundles inside `libpython.zip.so`.
     */
    private fun writePayloadZip(
        out: File,
        files: Map<String, String>,
        symlinks: List<Pair<String, String>>,
    ) {
        ZipArchiveOutputStream(FileOutputStream(out)).use { zip ->
            for ((name, content) in files) {
                val entry = ZipArchiveEntry(name)
                entry.unixMode = FILE_MODE
                zip.putArchiveEntry(entry)
                zip.write(content.toByteArray())
                zip.closeArchiveEntry()
            }
            for ((name, targetName) in symlinks) {
                val entry = ZipArchiveEntry(name)
                entry.unixMode = UnixStat.LINK_FLAG or LINK_MODE
                zip.putArchiveEntry(entry)
                zip.write(targetName.toByteArray())
                zip.closeArchiveEntry()
            }
        }
    }

    companion object {
        private val INVALID_STAMP = "0".repeat(64)
        private const val FILE_MODE = 0b110100100 // 0644
        private const val LINK_MODE = 0b111111111 // 0777
    }
}
