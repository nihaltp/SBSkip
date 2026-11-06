package com.nihaltp.sbskip.util.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class YtDlpRuntimeAbiTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val zipappBytes = "PK_ZIPAPP_FAKE_YTDLP".toByteArray()

    private lateinit var nativeLibDir: File
    private lateinit var runtime: YtDlpRuntime

    @Before
    fun setUp() {
        nativeLibDir = File(appContext.filesDir, "ytdlp-abi-test-" + System.nanoTime())
        nativeLibDir.mkdirs()
        appContext.applicationInfo.nativeLibraryDir = nativeLibDir.absolutePath
        runtime = YtDlpRuntime(appContext)
        runtime.zipappProvider = { java.io.ByteArrayInputStream(zipappBytes) }
    }

    @Test
    fun `environment includes correct paths for arm64-v8a`() {
        // The nativeLibraryDir should be set correctly
        assertTrue(appContext.applicationInfo.nativeLibraryDir.contains("ytdlp-abi-test"))

        // Write payload zips
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        val env = runtime.environment(paths, File(paths.baseDir, "tmp"))

        // Verify environment variables are set correctly
        assertNotNull(env["LD_LIBRARY_PATH"])
        assertNotNull(env["PYTHONHOME"])
        assertNotNull(env["HOME"])
        assertNotNull(env["SSL_CERT_FILE"])
        assertNotNull(env["TMPDIR"])

        // Verify paths are absolute
        assertTrue(env["LD_LIBRARY_PATH"]!!.startsWith("/"))
        assertTrue(env["PYTHONHOME"]!!.startsWith("/"))
    }

    @Test
    fun `environment includes correct paths for armeabi-v7a`() {
        // The nativeLibraryDir should be set correctly
        assertTrue(appContext.applicationInfo.nativeLibraryDir.contains("ytdlp-abi-test"))

        // Write payload zips
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        val env = runtime.environment(paths, File(paths.baseDir, "tmp"))

        // Verify environment variables are set correctly
        assertNotNull(env["LD_LIBRARY_PATH"])
        assertNotNull(env["PYTHONHOME"])
        assertNotNull(env["HOME"])
        assertNotNull(env["SSL_CERT_FILE"])
        assertNotNull(env["TMPDIR"])

        // Verify paths are absolute
        assertTrue(env["LD_LIBRARY_PATH"]!!.startsWith("/"))
        assertTrue(env["PYTHONHOME"]!!.startsWith("/"))
    }

    @Test
    fun `ensure creates runtime directory structure`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        // Verify directory structure
        assertTrue(paths.baseDir.exists())
        assertTrue(paths.baseDir.isDirectory)
        assertTrue(paths.pythonRoot.exists())
        assertTrue(paths.pythonRoot.isDirectory)
        assertTrue(paths.ffmpegRoot.exists())
        assertTrue(paths.ffmpegRoot.isDirectory)
        assertTrue(paths.zipapp.exists())
        assertTrue(paths.zipapp.isFile)
    }

    @Test
    fun `runtime paths are ABI-agnostic`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        // Paths should not contain ABI-specific strings
        assertFalse(paths.baseDir.absolutePath.contains("arm64"))
        assertFalse(paths.baseDir.absolutePath.contains("armeabi"))
        assertFalse(paths.baseDir.absolutePath.contains("x86"))
        assertFalse(paths.pythonRoot.absolutePath.contains("arm64"))
        assertFalse(paths.ffmpegRoot.absolutePath.contains("arm64"))
    }

    @Test
    fun `sha256 calculation is consistent across ABIs`() {
        writePythonPayloadZip()
        writeFfmpegPayloadZip()

        val paths = runBlocking { runtime.ensure(appContext) }

        val pythonSha = YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP))
        val ffmpegSha = YtDlpRuntime.sha256(File(nativeLibDir, YtDlpRuntime.FFMPEG_ZIP))
        val ytdlpSha = YtDlpRuntime.sha256(paths.zipapp)

        // SHA256 should be consistent
        assertEquals(64, pythonSha.length)
        assertEquals(64, ffmpegSha.length)
        assertEquals(64, ytdlpSha.length)

        // Verify they match what's stored in versions file
        val versions = YtDlpRuntime.readVersions(File(paths.baseDir, YtDlpRuntime.VERSIONS_FILE))!!
        assertEquals(pythonSha, versions.python)
        assertEquals(ffmpegSha, versions.ffmpeg)
        assertEquals(ytdlpSha, versions.ytdlp)
    }

    @Test
    fun `corrupted python payload fails on all ABIs`() {
        File(nativeLibDir, YtDlpRuntime.PYTHON_ZIP).writeText("not a valid zip")
        writeFfmpegPayloadZip()

        val exception =
            org.junit.Assert.assertThrows(YtDlpInitException::class.java) {
                runBlocking { runtime.ensure(appContext) }
            }

        assertTrue(exception.message!!.contains("runtime"))
    }

    @Test
    fun `corrupted ffmpeg payload fails on all ABIs`() {
        writePythonPayloadZip()
        File(nativeLibDir, YtDlpRuntime.FFMPEG_ZIP).writeText("not a valid zip")

        val exception =
            org.junit.Assert.assertThrows(YtDlpInitException::class.java) {
                runBlocking { runtime.ensure(appContext) }
            }

        assertTrue(exception.message!!.contains("runtime"))
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

    private fun writePayloadZip(
        out: File,
        files: Map<String, String>,
        symlinks: List<Pair<String, String>>,
    ) {
        org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(java.io.FileOutputStream(out)).use { zip ->
            for ((name, content) in files) {
                val entry = org.apache.commons.compress.archivers.zip.ZipArchiveEntry(name)
                entry.unixMode = 0b110100100 // 0644
                zip.putArchiveEntry(entry)
                zip.write(content.toByteArray())
                zip.closeArchiveEntry()
            }
            for ((name, targetName) in symlinks) {
                val entry = org.apache.commons.compress.archivers.zip.ZipArchiveEntry(name)
                entry.unixMode = org.apache.commons.compress.archivers.zip.UnixStat.LINK_FLAG or 0b111111111
                zip.putArchiveEntry(entry)
                zip.write(targetName.toByteArray())
                zip.closeArchiveEntry()
            }
        }
    }
}
