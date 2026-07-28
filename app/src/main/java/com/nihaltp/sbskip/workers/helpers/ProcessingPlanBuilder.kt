package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.processing.SegmentProcessor
import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment
import com.nihaltp.sbskip.util.AppLogger
import javax.inject.Inject

class ProcessingPlanBuilder
    @Inject
    constructor() {
        fun createPlan(
            item: DownloadQueueItem,
            localExtension: String,
            fileDuration: Long,
            segments: List<SponsorBlockSegment>?,
        ): ProcessingPlan {
            val keepRanges = mutableListOf<Pair<Double, Double>>()
            if (item.url.isNotBlank() && !item.url.startsWith("sbskip://") && !segments.isNullOrEmpty()) {
                val computedKeepRanges = SegmentProcessor.computeKeepRanges(segments, fileDuration.toDouble())
                keepRanges.addAll(computedKeepRanges)
                AppLogger.worker("Computed ${keepRanges.size} keep ranges from segments")
            }

            val isAudioExt = localExtension.lowercase() in setOf("m4a", "mp3", "aac", "opus", "ogg", "wav", "flac", "weba")
            val actualConvert = item.convertVideoToAudio && !isAudioExt
            val outputExtension = if (actualConvert) "m4a" else localExtension

            return ProcessingPlan(
                keepRanges = keepRanges,
                convertVideoToAudio = actualConvert,
                outputExtension = outputExtension,
            )
        }
    }
