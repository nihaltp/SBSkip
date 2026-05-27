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

    private fun addLogEntry(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] [$tag] $message"
        recentLogs.add(logLine)
        if (recentLogs.size > MAX_LOGS) {
            recentLogs.removeAt(0)
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
        return recentLogs.joinToString("\n")
    }

    fun clearLogs() {
        recentLogs.clear()
    }
}
