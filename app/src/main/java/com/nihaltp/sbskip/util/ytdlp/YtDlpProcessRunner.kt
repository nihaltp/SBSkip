package com.nihaltp.sbskip.util.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs

/** One `PROGRESS|` line from yt-dlp's progress template. */
data class YtDlpProgress(
    val percent: Float?,
    val downloadedBytes: Long?,
    val totalBytesEstimate: Long?,
    val speed: String?,
    val eta: String?,
)

/** Outcome of one yt-dlp invocation. */
data class YtDlpRunResult(
    val exitCode: Int,
    /** Non-progress stdout lines (e.g. `[Merger] …`). */
    val stdout: String,
    /** Ring buffer of the last stderr bytes, for failure surfacing. */
    val stderrTail: String,
    /** Most recent progress event, if any (for final-status reporting). */
    val lastProgress: YtDlpProgress?,
)

/** Builds the exact `Process` instances the runner executes; seamed for unit tests. */
fun interface YtDlpProcessStarter {
    fun start(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: File?,
    ): Process
}

/**
 * Executes one yt-dlp invocation with byte-accurate progress parsing (plan §5) and cooperative
 * cancellation: cancelling the calling coroutine destroys the process gracefully, then
 * `destroyForcibly` after a short linger. No orphan yt-dlp child processes survive a cancel.
 */
open class YtDlpProcessRunner(
    /** Progress dispatch throttle (plan §5: ≥500 ms or a ≥1% percent step). */
    private val throttleMillis: Long = THROTTLE_MILLIS,
    /** Mono-ish clock seam for deterministic throttle tests. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val processStarter: YtDlpProcessStarter =
        YtDlpProcessStarter { command, environment, workingDirectory ->
            ProcessBuilder(command).apply {
                environment().apply {
                    clear()
                    putAll(environment)
                }
                workingDirectory?.let { directory(it) }
            }.start()
        },
) {
    /**
     * Runs yt-dlp to completion, dispatching throttled progress events. Non-zero exits are
     * returned as a normal [YtDlpRunResult] — interpreting the exit code is the caller's job
     * ([YtDlpRunResult.stderrTail] carries the failure reason).
     */
    open suspend fun run(
        command: List<String>,
        environment: Map<String, String>,
        onProgress: (YtDlpProgress) -> Unit,
    ): YtDlpRunResult =
        withContext(Dispatchers.IO) {
            val process = processStarter.start(command, environment, null)
            val stdoutBuffer = StringBuilder()
            val stderrTail = StringBuilder()
            val throttle = ThrottleState()
            var lastProgress: YtDlpProgress? = null

            val stdoutReader =
                thread(name = "ytdlp-stdout") {
                    runCatching {
                        process.inputStream
                            .bufferedReader()
                            .useLines { lines ->
                                lines.forEach { line ->
                                    val progress = YtDlpProgressParser.parse(line)
                                    if (progress != null) {
                                        lastProgress = progress
                                        if (throttle.shouldDispatch(nowMillis(), progress)) {
                                            onProgress(progress)
                                        }
                                    } else {
                                        synchronized(stdoutBuffer) {
                                            stdoutBuffer.appendLine(line)
                                            truncate(stdoutBuffer)
                                        }
                                    }
                                }
                            }
                    }
                }
            val stderrReader =
                thread(name = "ytdlp-stderr") {
                    runCatching {
                        process.errorStream
                            .bufferedReader()
                            .useLines { lines ->
                                lines.forEach { line ->
                                    synchronized(stderrTail) {
                                        stderrTail.appendLine(line)
                                        truncate(stderrTail)
                                    }
                                }
                            }
                    }
                }

            val exitCode = awaitExit(process)
            stdoutReader.join(STREAM_DRAIN_MILLIS)
            stderrReader.join(STREAM_DRAIN_MILLIS)
            YtDlpRunResult(
                exitCode = exitCode,
                stdout = stdoutBuffer.toString(),
                stderrTail = stderrTail.toString(),
                lastProgress = lastProgress,
            )
        }

    /** Waits for the process; destroying it when the calling coroutine is cancelled. */
    private suspend fun awaitExit(process: Process): Int =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                process.destroy()
                thread(isDaemon = true, name = "ytdlp-forcekill") {
                    try {
                        Thread.sleep(FORCE_KILL_LINGER_MILLIS)
                    } catch (_: InterruptedException) {
                    }
                    process.destroyForcibly()
                }
            }
            thread(name = "ytdlp-wait") {
                val exitCode =
                    try {
                        process.waitFor()
                    } catch (_: InterruptedException) {
                        -1
                    }
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(exitCode))
                }
            }
        }

    private inner class ThrottleState {
        var lastDispatchAt = Long.MIN_VALUE
        var lastDispatchedPercent = -1f

        fun shouldDispatch(
            now: Long,
            progress: YtDlpProgress,
        ): Boolean {
            val percent = progress.percent
            val steppedOnePercent = percent != null && abs(percent - lastDispatchedPercent) >= 1f
            val firstDispatch = lastDispatchedPercent < 0f
            if (firstDispatch || steppedOnePercent || now - lastDispatchAt >= throttleMillis) {
                lastDispatchAt = now
                percent?.let { lastDispatchedPercent = it }
                return true
            }
            return false
        }
    }

    companion object {
        const val THROTTLE_MILLIS = 500L
        const val FORCE_KILL_LINGER_MILLIS = 2_000L
        const val STREAM_DRAIN_MILLIS = 2_000L

        private const val TAIL_LIMIT = 8 * 1024

        /** Dumps head bytes off [buffer] so stderr/stdout keep only their recent tail. */
        fun truncate(buffer: StringBuilder): StringBuilder =
            if (buffer.length > TAIL_LIMIT) {
                buffer.delete(0, buffer.length - TAIL_LIMIT)
            } else {
                buffer
            }
    }
}

/** Parses yt-dlp `PROGRESS|` template lines; tolerant of "Unknown"/blank fields. */
object YtDlpProgressParser {
    const val LINE_PREFIX = "PROGRESS|"

    fun parse(line: String): YtDlpProgress? {
        if (!line.startsWith(LINE_PREFIX)) return null
        val parts = line.removePrefix(LINE_PREFIX).split('|')
        val percent =
            parts
                .getOrNull(0)
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.equals("unknown", ignoreCase = true) }
                ?.removeSuffix("%")
                ?.toFloatOrNull()
                ?.takeIf { !it.isNaN() && !it.isInfinite() }
        val downloadedBytes = parts.getOrNull(1)?.trim()?.toLongOrNull()
        val totalBytesEstimate = parts.getOrNull(2)?.trim()?.toLongOrNull()
        val speed = parts.getOrNull(3)?.trim()?.sanitise()
        val eta = parts.getOrNull(4)?.trim()?.sanitise()
        if (percent == null && downloadedBytes == null) return null
        return YtDlpProgress(percent, downloadedBytes, totalBytesEstimate, speed, eta)
    }

    private fun String.sanitise(): String? = if (isEmpty() || equals("unknown", ignoreCase = true)) null else this
}
