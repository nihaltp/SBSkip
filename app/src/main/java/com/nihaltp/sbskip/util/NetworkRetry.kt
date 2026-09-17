package com.nihaltp.sbskip.util

import kotlinx.coroutines.delay

object NetworkRetry {
    private const val MAX_ATTEMPTS = 3
    private const val INITIAL_DELAY_MILLIS = 500L

    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                if (!NetworkErrorClassifier.isRetryableNetworkError(error) || attempt >= MAX_ATTEMPTS) {
                    throw error
                }
                delay(INITIAL_DELAY_MILLIS * (1L shl (attempt - 1)))
                attempt++
            }
        }
    }
}
