package com.nihaltp.sbskip.processing

import com.arthenica.ffmpegkit.ReturnCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FFmpegMediaProcessorTest {
    private lateinit var processor: FFmpegMediaProcessor
    private lateinit var tempDir: File
    private val executedCommands = mutableListOf<String>()

    @Before
    fun setUp() {
        processor = FFmpegMediaProcessor()
        tempDir = File(System.getProperty("java.io.tmpdir"), "ffmpeg_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Override command executor to capture commands and mock success
        processor.commandExecutor = { command ->
            executedCommands.add(command)
            object : FFmpegExecutionResult {
                override val isSuccess: Boolean = true
                override val returnCode: ReturnCode = ReturnCode(0)
                override val logs: String = "Success"
                override val failStackTrace: String = ""
            }
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        executedCommands.clear()
    }

    private fun createDummyFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("dummy content")
        return file
    }

    @Test
    fun testEmptyKeepRanges_mp4_convertToAudio() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.mp4")
            val outputFile = File(tempDir, "output.m4a")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = emptyList(),
                convertVideoToAudio = true,
                progressListener = {},
            )

            assertEquals(1, executedCommands.size)
            val cmd = executedCommands[0]
            assertTrue(cmd.contains("-vn -c:a copy"))
            assertTrue(cmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testEmptyKeepRanges_webm_convertToAudio() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.webm")
            val outputFile = File(tempDir, "output.m4a")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = emptyList(),
                convertVideoToAudio = true,
                progressListener = {},
            )

            assertEquals(1, executedCommands.size)
            val cmd = executedCommands[0]
            assertTrue(cmd.contains("-vn -c:a aac"))
            assertTrue(cmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testEmptyKeepRanges_noConversion() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.webm")
            val outputFile = File(tempDir, "output.webm")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = emptyList(),
                convertVideoToAudio = false,
                progressListener = {},
            )

            // Should copy the file directly, no FFmpeg command called
            assertEquals(0, executedCommands.size)
            assertTrue(outputFile.exists())
            assertEquals("dummy content", outputFile.readText())
        }

    @Test
    fun testSingleKeepRange_mp4_convertToAudio() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.mp4")
            val outputFile = File(tempDir, "output.m4a")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = listOf(Pair(10.0, 20.0)),
                convertVideoToAudio = true,
                progressListener = {},
            )

            assertEquals(1, executedCommands.size)
            val cmd = executedCommands[0]
            assertTrue(cmd.contains("-ss 10.000"))
            assertTrue(cmd.contains("-t 10.000"))
            assertTrue(cmd.contains("-vn -c:a copy"))
            assertTrue(cmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testSingleKeepRange_webm_convertToAudio() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.webm")
            val outputFile = File(tempDir, "output.m4a")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = listOf(Pair(10.5, 25.0)),
                convertVideoToAudio = true,
                progressListener = {},
            )

            assertEquals(1, executedCommands.size)
            val cmd = executedCommands[0]
            assertTrue(cmd.contains("-ss 10.500"))
            assertTrue(cmd.contains("-t 14.500"))
            assertTrue(cmd.contains("-vn -c:a aac"))
            assertTrue(cmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testSingleKeepRange_webm_noConversion() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.webm")
            val outputFile = File(tempDir, "output.webm")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = listOf(Pair(5.0, 15.0)),
                convertVideoToAudio = false,
                progressListener = {},
            )

            assertEquals(1, executedCommands.size)
            val cmd = executedCommands[0]
            assertTrue(cmd.contains("-ss 5.000"))
            assertTrue(cmd.contains("-t 10.000"))
            assertTrue(cmd.contains("-c copy"))
            assertTrue(cmd.contains("-avoid_negative_ts make_zero"))
            assertTrue(cmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testMultipleKeepRanges_webm_convertToAudio() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.webm")
            val outputFile = File(tempDir, "output.m4a")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = listOf(Pair(0.0, 10.0), Pair(20.0, 30.0)),
                convertVideoToAudio = true,
                progressListener = {},
            )

            // Multiple steps:
            // 1. Trim segment 0 (-c copy -avoid_negative_ts make_zero)
            // 2. Trim segment 1 (-c copy -avoid_negative_ts make_zero)
            // 3. Concat (-fflags +genpts -f concat)
            // 4. Extract audio (-vn -c:a aac)
            assertEquals(4, executedCommands.size)

            // Check trim segment 0
            val trim0 = executedCommands[0]
            assertTrue(trim0.contains("-ss 0.000 -t 10.000"))
            assertTrue(trim0.contains("-c copy"))
            assertTrue(trim0.contains("-avoid_negative_ts make_zero"))
            assertTrue(trim0.contains("-analyzeduration 50M -probesize 50M"))

            // Check trim segment 1
            val trim1 = executedCommands[1]
            assertTrue(trim1.contains("-ss 20.000 -t 10.000"))
            assertTrue(trim1.contains("-c copy"))
            assertTrue(trim1.contains("-avoid_negative_ts make_zero"))
            assertTrue(trim1.contains("-analyzeduration 50M -probesize 50M"))

            // Check concat command
            val concatCmd = executedCommands[2]
            assertTrue(concatCmd.contains("-fflags +genpts"))
            assertTrue(concatCmd.contains("-f concat"))
            assertTrue(concatCmd.contains("-c copy"))
            assertTrue(concatCmd.contains("-analyzeduration 50M -probesize 50M"))

            // Check extract audio command
            val extractCmd = executedCommands[3]
            assertTrue(extractCmd.contains("-vn -c:a aac"))
            assertTrue(extractCmd.contains("-analyzeduration 50M -probesize 50M"))
        }

    @Test
    fun testMultipleKeepRanges_mkv_noConversion() =
        kotlinx.coroutines.runBlocking {
            val inputFile = createDummyFile("input.mkv")
            val outputFile = File(tempDir, "output.mkv")

            processor.processMedia(
                inputFile = inputFile,
                outputFile = outputFile,
                keepRanges = listOf(Pair(1.0, 5.0), Pair(10.0, 15.0)),
                convertVideoToAudio = false,
                progressListener = {},
            )

            // Steps:
            // 1. Trim segment 0
            // 2. Trim segment 1
            // 3. Concat directly to output
            assertEquals(3, executedCommands.size)

            val trim0 = executedCommands[0]
            assertTrue(trim0.contains("-ss 1.000 -t 4.000"))
            assertTrue(trim0.contains("-c copy"))
            assertTrue(trim0.contains("-avoid_negative_ts make_zero"))

            val trim1 = executedCommands[1]
            assertTrue(trim1.contains("-ss 10.000 -t 5.000"))
            assertTrue(trim1.contains("-c copy"))
            assertTrue(trim1.contains("-avoid_negative_ts make_zero"))

            val concatCmd = executedCommands[2]
            assertTrue(concatCmd.contains("-fflags +genpts"))
            assertTrue(concatCmd.contains("-f concat"))
            assertTrue(concatCmd.contains("-c copy"))
        }
}
