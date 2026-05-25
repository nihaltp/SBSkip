package com.nihaltp.sbskip.downloader

data class YtDlpExecutionResult(
    val exitCode: Int,
    val output: String,
)

interface YtDlpExecutor {
    suspend fun dumpSingleJson(url: String): YtDlpExecutionResult
}
