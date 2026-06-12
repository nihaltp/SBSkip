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
            if (item.url.isNotBlank() && !item.url.startsWith("sbskip://") && segments != null) {
                val computedKeepRanges = SegmentProcessor.computeKeepRanges(segments, fileDuration.toDouble())
                keepRanges.addAll(computedKeepRanges)
                AppLogger.worker("Computed ${keepRanges.size} keep ranges from segments")
            }

            val outputExtension = if (item.convertVideoToAudio) "m4a" else localExtension

            return ProcessingPlan(
                keepRanges = keepRanges,
                convertVideoToAudio = item.convertVideoToAudio,
                outputExtension = outputExtension,
            )
        }
    }
