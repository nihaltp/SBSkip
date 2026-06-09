package com.nihaltp.sbskip.processing

import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment

object SegmentProcessor {
    /**
     * Sorts, merges overlapping segments, and performs tiny-gap cleanup on SponsorBlock segments.
     * Gaps smaller than [gapThresholdSeconds] between removed segments will be merged into a single segment
     * to prevent tiny keep segments that lead to visual glitches.
     */
    fun mergeSegments(
        segments: List<SponsorBlockSegment>,
        gapThresholdSeconds: Double = 0.5,
    ): List<SponsorBlockSegment> {
        if (segments.isEmpty()) return emptyList()

        // 1. Sort segments by start time
        val sorted = segments.sortedBy { it.startSeconds }

        val merged = mutableListOf<SponsorBlockSegment>()
        for (segment in sorted) {
            if (segment.startSeconds >= segment.endSeconds) continue // Skip invalid segments

            if (merged.isEmpty()) {
                merged.add(segment)
            } else {
                val last = merged.last()
                // If segment overlaps or has a tiny gap with the last merged segment, merge them
                if (segment.startSeconds <= last.endSeconds + gapThresholdSeconds) {
                    val newEnd = maxOf(last.endSeconds, segment.endSeconds)
                    merged[merged.lastIndex] = last.copy(endSeconds = newEnd)
                } else {
                    merged.add(segment)
                }
            }
        }
        return merged
    }

    /**
     * Inverts the removed segments list to find the keep ranges in a media file of length [totalDurationSeconds].
     * Clamps all segments to the duration bounds and filters out keep ranges smaller than 0.1 seconds.
     */
    fun computeKeepRanges(
        removedSegments: List<SponsorBlockSegment>,
        totalDurationSeconds: Double,
    ): List<Pair<Double, Double>> {
        if (totalDurationSeconds <= 0.0) return emptyList()

        // 1. Merge overlapping / close segments
        val merged = mergeSegments(removedSegments)

        // 2. Compute keep ranges (complement of merged segments in [0, totalDurationSeconds])
        val keepRanges = mutableListOf<Pair<Double, Double>>()
        var currentStart = 0.0

        for (segment in merged) {
            // Clamp segment bounds to video duration
            val start = clamp(segment.startSeconds, 0.0, totalDurationSeconds)
            val end = clamp(segment.endSeconds, 0.0, totalDurationSeconds)

            if (start > currentStart) {
                // Keep range before this removed segment
                keepRanges.add(Pair(currentStart, start))
            }
            currentStart = maxOf(currentStart, end)
        }

        if (currentStart < totalDurationSeconds) {
            // Keep range from last segment to end of video
            keepRanges.add(Pair(currentStart, totalDurationSeconds))
        }

        // 3. Filter out empty or extremely tiny keep ranges (< 0.1s) which are practically imperceptible
        return keepRanges.filter { (start, end) -> (end - start) >= 0.1 }
    }

    private fun clamp(
        value: Double,
        min: Double,
        max: Double,
    ): Double {
        return maxOf(min, minOf(value, max))
    }
}
