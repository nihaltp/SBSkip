package com.nihaltp.sbskip.processing

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFmpegMediaProcessor @Inject constructor() : MediaProcessor {

    override suspend fun processMedia(
        inputFile: File,
        outputFile: File,
        keepRanges: List<Pair<Double, Double>>,
        progressListener: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) {
            throw IOException("Input file does not exist: ${inputFile.absolutePath}")
        }

        if (keepRanges.isEmpty()) {
            // Nothing to keep (unusual) -> copy empty or copy entire input
            AppLogger.worker("No keep ranges specified. Copying entire source file.")
            inputFile.copyTo(outputFile, overwrite = true)
            progressListener(100)
            return@withContext
        }

        if (keepRanges.size == 1) {
            // Optimization: single trim segment, no concat needed
            val (start, end) = keepRanges[0]
            val duration = end - start
            AppLogger.worker("Single keep range [start=$start, duration=$duration]. Running direct trim.")

            runFFmpegCommand(
                "-y -ss ${formatTime(start)} -t ${formatTime(duration)} -i \"${inputFile.absolutePath}\" -c copy \"${outputFile.absolutePath}\""
            )
            progressListener(100)
            return@withContext
        }

        // Multi-segment split & concat workflow
        val tempDir = inputFile.parentFile ?: File(inputFile.path).parentFile
        val segmentFiles = mutableListOf<File>()

        try {
            // 1. Trim each keep range into a separate temporary segment file
            keepRanges.forEachIndexed { index, (start, end) ->
                val duration = end - start
                val tempSegmentFile = File(tempDir, "temp_segment_${index}_${System.currentTimeMillis()}.${inputFile.extension}")
                segmentFiles.add(tempSegmentFile)

                AppLogger.worker("Trimming segment $index [start=$start, duration=$duration] to ${tempSegmentFile.name}")

                runFFmpegCommand(
                    "-y -ss ${formatTime(start)} -t ${formatTime(duration)} -i \"${inputFile.absolutePath}\" -c copy \"${tempSegmentFile.absolutePath}\""
                )

                // Report progressive split progress
                val percent = ((index + 1) * 70) / keepRanges.size
                progressListener(percent)
            }

            // 2. Write segment file paths to a concat text file
            val concatListFile = File(tempDir, "concat_list_${System.currentTimeMillis()}.txt")
            AppLogger.worker("Writing concat file list to ${concatListFile.name}")

            concatListFile.bufferedWriter().use { writer ->
                segmentFiles.forEach { file ->
                    val safePath = file.absolutePath.replace("'", "'\\''")
                    writer.write("file '$safePath'\n")
                }
            }

            // 3. Concatenate all segments using FFmpeg concat demuxer
            AppLogger.worker("Concatenating all segments into ${outputFile.name}")
            progressListener(85)

            try {
                runFFmpegCommand(
                    "-y -f concat -safe 0 -i \"${concatListFile.absolutePath}\" -c copy \"${outputFile.absolutePath}\""
                )
            } finally {
                // Safely clean up the concat list text file
                if (concatListFile.exists()) {
                    concatListFile.delete()
                }
            }

            progressListener(100)
            AppLogger.worker("Media processing completed successfully: ${outputFile.absolutePath}")

        } catch (e: Exception) {
            AppLogger.error("FFmpeg", e, "Error occurred during media processing")
            throw e
        } finally {
            // Clean up all temporary segment files
            segmentFiles.forEach { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    AppLogger.worker("Cleaned up temp segment ${file.name}: $deleted")
                }
            }
        }
    }

    private fun runFFmpegCommand(command: String) {
        AppLogger.worker("FFmpeg command: ffmpeg $command")
        val session = FFmpegKit.execute(command)
        val returnCode = session.getReturnCode()

        if (!ReturnCode.isSuccess(returnCode)) {
            val logs = session.getLogsAsString() ?: "No FFmpeg logs available"
            val failStackTrace = session.getFailStackTrace() ?: ""
            AppLogger.error("FFmpeg", Exception("FFmpeg command failed"), "FFmpeg Failure Logs:\n$logs\nStackTrace: $failStackTrace")
            throw IOException("FFmpeg command failed with return code $returnCode. Logs:\n$logs")
        }
    }

    private fun formatTime(seconds: Double): String {
        return String.format(Locale.US, "%.3f", seconds)
    }
}
