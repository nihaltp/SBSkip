package com.nihaltp.sbskip.util

object NetworkErrorClassifier {
    private val networkErrorMarkers =
        listOf(
            "timeout",
            "timed out",
            "unable to resolve host",
            "unknownhostexception",
            "network is unreachable",
            "no route to host",
            "connection refused",
        )

    fun isNetworkErrorMessage(errorMessage: String): Boolean {
        val normalized = errorMessage.lowercase()
        return networkErrorMarkers.any(normalized::contains)
    }

    fun isRetryableNetworkError(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is java.net.UnknownHostException ||
                current is java.net.SocketTimeoutException ||
                current is java.net.ConnectException ||
                isNetworkErrorMessage(current.message.orEmpty())
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
