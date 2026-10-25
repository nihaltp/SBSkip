package com.nihaltp.sbskip.util.ytdlp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

class YtDlpProcessRunnerProgressTest {
    private val stdoutFixture: String =
        javaClass.getResourceAsStream("/ytdlp/progress_lines.txt")!!
            .bufferedReader().use { it.readText() }

    private fun mockProcess(
        stdout: ByteArrayInputStream,
        stderr: ByteArrayInputStream,
    ): Process =
        mockk(relaxed = true) {
            every { inputStream } returns stdout
            every { errorStream } returns stderr
        }

    private fun runner(process: Process): YtDlpProcessRunner =
        YtDlpProcessRunner(
            throttleMillis = 0,
            processStarter = YtDlpProcessStarter { _, _, _ -> process },
        )

    @Test
    fun `progress lines are parsed from template output`() {
        val progress = YtDlpProgressParser.parse("PROGRESS|42.3%|123456|543210|1.72MiB/s|00:13")

        assertEquals(42.3f, progress!!.percent)
        assertEquals(123456L, progress.downloadedBytes)
        assertEquals(543210L, progress.totalBytesEstimate)
        assertEquals("1.72MiB/s", progress.speed)
        assertEquals("00:13", progress.eta)
    }

    @Test
    fun `tolerant of unknown and missing fields`() {
        val progress = YtDlpProgressParser.parse("PROGRESS|Unknown|321|Unknown|Unknown|Unknown")

        assertNull(progress!!.percent)
        assertEquals(321L, progress.downloadedBytes)
        assertNull(progress.totalBytesEstimate)
        assertNull(progress.speed)
        assertNull(progress.eta)
        assertNull(YtDlpProgressParser.parse("[download] Destination: out.mp4"))
        assertNull(YtDlpProgressParser.parse("PROGRESS|nope|nope"))
    }

    @Test
    fun `fixture stream produces one dispatch per progress line when unthrottled`() {
        val process =
            mockProcess(
                stdout = ByteArrayInputStream(stdoutFixture.toByteArray()),
                stderr = ByteArrayInputStream(ByteArray(0)),
            )
        every { process.waitFor() } returns 0

        val dispatched = mutableListOf<YtDlpProgress>()
        val result =
            runBlocking {
                runner(process).run(
                    command = listOf("libpython.so", "yt-dlp"),
                    environment = emptyMap(),
                    onProgress = { dispatched.add(it) },
                )
            }

        assertEquals(0, result.exitCode)
        val fixtureProgressLines =
            stdoutFixture.lineSequence().filter { it.startsWith("PROGRESS|") }.count()
        assertEquals(fixtureProgressLines, dispatched.size)
        assertEquals(
            "last dispatch equals result.lastProgress",
            dispatched.lastOrNull(),
            result.lastProgress,
        )
        val expectedStdout =
            stdoutFixture
                .lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("PROGRESS|") }
                .joinToString("\n")
        assertEquals(expectedStdout, result.stdout.trim())
        assertEquals("", result.stderrTail.trim())
    }

    @Test
    fun `progress dispatch is throttled when steps stay below one percent`() {
        val slowClock = AtomicLong(0)
        val process =
            mockProcess(
                stdout =
                    ByteArrayInputStream(
                        (
                            "PROGRESS|1.0%|10|1000|1MiB/s|00:00\n" +
                                "PROGRESS|1.1%|11|1000|1MiB/s|00:00\n" +
                                "PROGRESS|1.2%|12|1000|1MiB/s|00:00\n" +
                                "PROGRESS|1.3%|13|1000|1MiB/s|00:00\n"
                        ).toByteArray(),
                    ),
                stderr = ByteArrayInputStream(ByteArray(0)),
            )
        every { process.waitFor() } returns 0

        val dispatched = mutableListOf<YtDlpProgress>()
        runBlocking {
            YtDlpProcessRunner(
                throttleMillis = 500,
                nowMillis = { slowClock.getAndAdd(10) },
                processStarter = YtDlpProcessStarter { _, _, _ -> process },
            ).run(command = listOf("libpython.so", "yt-dlp"), environment = emptyMap(), onProgress = {
                dispatched.add(it)
            })
        }

        assertEquals(1, dispatched.size)
    }

    @Test
    fun `cancelling the coroutine destroys the process`() {
        val gate = CountDownLatch(1)
        val process =
            mockProcess(
                stdout = ByteArrayInputStream(ByteArray(0)),
                stderr = ByteArrayInputStream(ByteArray(0)),
            )
        every { process.waitFor() } answers {
            gate.await()
            -1
        }
        val processStarted = CompletableDeferred<Unit>()

        runBlocking {
            val job =
                launch(Dispatchers.IO) {
                    YtDlpProcessRunner(
                        throttleMillis = 0,
                        processStarter =
                            YtDlpProcessStarter { _, _, _ ->
                                processStarted.complete(Unit)
                                process
                            },
                    ).run(command = listOf("libpython.so", "yt-dlp"), environment = emptyMap(), onProgress = { })
                }
            processStarted.await()
            job.cancelAndJoin()
            verify { process.destroy() }
            gate.countDown()
        }
    }

    @Test
    fun `non-zero exit still yields the full result with stderr tail`() {
        val process =
            mockProcess(
                stdout = ByteArrayInputStream(ByteArray(0)),
                stderr = ByteArrayInputStream("ERROR: something went wrong\n".toByteArray()),
            )
        every { process.waitFor() } returns 1

        val result =
            runBlocking {
                runner(process).run(
                    command = listOf("libpython.so", "yt-dlp"),
                    environment = emptyMap(),
                    onProgress = { },
                )
            }

        assertEquals(1, result.exitCode)
        assertTrue(result.stderrTail.contains("ERROR: something went wrong"))
        assertNull(result.lastProgress)
    }
}
