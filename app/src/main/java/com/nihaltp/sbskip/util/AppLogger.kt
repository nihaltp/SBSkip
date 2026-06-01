package com.nihaltp.sbskip.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG_PREFIX = "SBSkip"
    private const val MAX_LOGS = 1000
    private val recentLogs = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: java.io.File? = null

    fun init(context: android.content.Context) {
        synchronized(this) {
            if (logFile == null) {
                logFile = java.io.File(context.applicationContext.filesDir, "sbskip.log")
            }
        }
    }

    fun initForTesting(testLogFile: java.io.File) {
        synchronized(this) {
            logFile = testLogFile
        }
    }

    private fun addLogEntry(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] [$tag] $message"
        recentLogs.add(logLine)
        if (recentLogs.size > MAX_LOGS) {
            recentLogs.removeAt(0)
        }
        writeLogToFile(logLine)
    }

    private fun writeLogToFile(logLine: String) {
        val file = logFile ?: return
        synchronized(this) {
            try {
                if (file.exists() && file.length() > 500 * 1024) {
                    rotateLogFiles(file)
                }
                file.appendText(logLine + "\n")
            } catch (e: Throwable) {
                // Running on JVM tests or permission issue. Fallback to stderr
                System.err.println("AppLogger: Failed to write to file: ${e.message}")
            }
        }
    }

    private fun rotateLogFiles(file: java.io.File) {
        try {
            val oldFile = java.io.File(file.parent, "${file.name}.old")
            if (oldFile.exists()) {
                oldFile.delete()
            }
            if (file.exists()) {
                file.renameTo(oldFile)
            }
        } catch (e: Throwable) {
            System.err.println("AppLogger: Failed to rotate logs: ${e.message}")
        }
    }

    private fun safeLogD(tag: String, message: String) {
        addLogEntry(tag, message)
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            // Running on plain JVM unit tests where android.util.Log is not available.
            System.out.println("$tag: $message")
        }
    }

    private fun safeLogE(tag: String, throwable: Throwable, message: String) {
        val fullMessage = "$message | Exception: ${throwable.localizedMessage ?: throwable.message}"
        addLogEntry(tag, fullMessage)
        try {
            Log.e(tag, message, throwable)
        } catch (e: Throwable) {
            System.err.println("$tag: $message")
            throwable.printStackTrace(System.err)
        }
    }

    fun queue(message: String) {
        safeLogD("$TAG_PREFIX-Queue", message)
    }

    fun worker(message: String) {
        safeLogD("$TAG_PREFIX-Worker", message)
    }

    fun metadata(message: String) {
        safeLogD("$TAG_PREFIX-Metadata", message)
    }

    fun error(tag: String, throwable: Throwable, message: String) {
        safeLogE("$TAG_PREFIX-$tag", throwable, message)
    }

    fun getLogs(): String {
        val file = logFile ?: return recentLogs.joinToString("\n")
        return synchronized(this) {
            try {
                val oldFile = java.io.File(file.parent, "${file.name}.old")
                val oldLogs = if (oldFile.exists()) oldFile.readText() else ""
                val currentLogs = if (file.exists()) file.readText() else ""
                (oldLogs + currentLogs).trim()
            } catch (e: Throwable) {
                recentLogs.joinToString("\n")
            }
        }
    }

    fun clearLogs() {
        recentLogs.clear()
        val file = logFile ?: return
        synchronized(this) {
            try {
                if (file.exists()) file.delete()
                val oldFile = java.io.File(file.parent, "${file.name}.old")
                if (oldFile.exists()) oldFile.delete()
            } catch (e: Throwable) {
                System.err.println("AppLogger: Failed to clear log files: ${e.message}")
            }
        }
    }
}
