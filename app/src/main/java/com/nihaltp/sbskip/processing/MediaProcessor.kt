package com.nihaltp.sbskip.processing

import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment

interface MediaProcessor {
    suspend fun trimSegments(inputFile: String, outputFile: String, segments: List<SponsorBlockSegment>): ProcessingResult
}

data class ProcessingResult(
    val outputFile: String,
    val progressLog: List<String> = emptyList(),
)
