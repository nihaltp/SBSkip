package com.nihaltp.sbskip.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppLoggerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var logFile: File
    private lateinit var logFileOld: File

    @Before
    fun setUp() {
        val parentDir = tempFolder.newFolder("logs")
        logFile = File(parentDir, "sbskip.log")
        logFileOld = File(parentDir, "sbskip.log.old")
        AppLogger.initForTesting(logFile)
        AppLogger.isVerboseLoggingEnabled = true
        AppLogger.clearLogs()
    }

    @After
    fun tearDown() {
        AppLogger.clearLogs()
    }

    @Test
    fun testLogWriting() {
        AppLogger.queue("This is a queue test log message")
        AppLogger.worker("This is a worker test log message")

        val logs = AppLogger.getLogs()
        assertTrue(logs.contains("[SBSkip-Queue] This is a queue test log message"))
        assertTrue(logs.contains("[SBSkip-Worker] This is a worker test log message"))
        assertTrue(logFile.exists())
    }

    @Test
    fun testClearLogs() {
        AppLogger.queue("Message to be cleared")
        assertTrue(AppLogger.getLogs().contains("Message to be cleared"))

        AppLogger.clearLogs()
        assertEquals("", AppLogger.getLogs())
        assertFalse(logFile.exists())
        assertFalse(logFileOld.exists())
    }

    @Test
    fun testLogRotation() {
        // Write a massive chunk of data that exceeds 500 KB to logFile directly
        val largeBuilder = StringBuilder()
        for (i in 1..10000) {
            largeBuilder.append("This is a long log line to consume space. Iteration number $i\n")
        }
        logFile.writeText(largeBuilder.toString())
        assertTrue(logFile.length() > 500 * 1024)

        // Logging a new line should trigger rotation
        AppLogger.queue("Trigger rotation message")

        // The old large file should have been rotated to sbskip.log.old
        assertTrue(logFileOld.exists())
        assertTrue(logFileOld.length() > 500 * 1024)

        // The current logFile should now be small, containing only the new log
        assertTrue(logFile.exists())
        assertTrue(logFile.length() < 1024)
        assertTrue(AppLogger.getLogs().contains("Trigger rotation message"))
    }

    @Test
    fun testErrorLoggingWhenVerboseLoggingDisabled() {
        AppLogger.isVerboseLoggingEnabled = false
        AppLogger.queue("This should not be logged")
        AppLogger.error("TestTag", Exception("TestException"), "This is an error")

        val logs = AppLogger.getLogs()
        assertFalse(logs.contains("This should not be logged"))
        assertTrue(logs.contains("SBSkip-ERROR-TestTag"))
        assertTrue(logs.contains("This is an error"))
    }
}
