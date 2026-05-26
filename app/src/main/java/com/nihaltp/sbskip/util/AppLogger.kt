package com.nihaltp.sbskip.util

import android.util.Log

object AppLogger {
    private const val TAG_PREFIX = "SBSkip"

    private fun safeLogD(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            // Running on plain JVM unit tests where android.util.Log is not available.
            System.out.println("$tag: $message")
        }
    }

    private fun safeLogE(tag: String, throwable: Throwable, message: String) {
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
}
