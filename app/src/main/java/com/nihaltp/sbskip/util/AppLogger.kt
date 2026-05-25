package com.nihaltp.sbskip.util

import android.util.Log

object AppLogger {
    private const val TAG_PREFIX = "SBSkip"

    fun queue(message: String) {
        Log.d("$TAG_PREFIX-Queue", message)
    }

    fun worker(message: String) {
        Log.d("$TAG_PREFIX-Worker", message)
    }

    fun metadata(message: String) {
        Log.d("$TAG_PREFIX-Metadata", message)
    }

    fun error(tag: String, throwable: Throwable, message: String) {
        Log.e("$TAG_PREFIX-$tag", message, throwable)
    }
}
