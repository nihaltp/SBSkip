package com.nihaltp.sbskip.downloader

import com.nihaltp.sbskip.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessYtDlpExecutor @Inject constructor() : YtDlpExecutor {
    override suspend fun dumpSingleJson(url: String): YtDlpExecutionResult = withContext(Dispatchers.IO) {
        val command = listOf(
            "yt-dlp",
            "--dump-single-json",
            "--skip-download",
            "--no-playlist",
            url,
        )

        AppLogger.metadata("running yt-dlp metadata command")

        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (throwable: Throwable) {
            throw YtDlpExecutionException("Unable to start yt-dlp", throwable)
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw YtDlpExecutionException("yt-dlp exited with $exitCode: $output")
        }

        YtDlpExecutionResult(exitCode = exitCode, output = output)
    }
}

class YtDlpExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
