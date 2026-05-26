package com.nihaltp.sbskip.processing

import java.io.File

interface MediaProcessor {
    /**
     * Cuts the [inputFile] according to the [keepRanges] and concatenates them into [outputFile] using FFmpeg.
     * The [progressListener] reports processing progress in percent (0 to 100).
     */
    suspend fun processMedia(
        inputFile: File,
        outputFile: File,
        keepRanges: List<Pair<Double, Double>>,
        progressListener: (Int) -> Unit = {},
    )
}
