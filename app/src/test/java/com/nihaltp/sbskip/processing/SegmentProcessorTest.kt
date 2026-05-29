package com.nihaltp.sbskip.processing

import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentProcessorTest {

    @Test
    fun testEmptySegments() {
        val segments = emptyList<SponsorBlockSegment>()
        val keepRanges = SegmentProcessor.computeKeepRanges(segments, 100.0)

        assertEquals(1, keepRanges.size)
        assertEquals(Pair(0.0, 100.0), keepRanges[0])
    }

    @Test
    fun testDisjointSegments() {
        val segments = listOf(
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 10.0, 20.0),
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 40.0, 50.0),
        )
        val keepRanges = SegmentProcessor.computeKeepRanges(segments, 100.0)

        assertEquals(3, keepRanges.size)
        assertEquals(Pair(0.0, 10.0), keepRanges[0])
        assertEquals(Pair(20.0, 40.0), keepRanges[1])
        assertEquals(Pair(50.0, 100.0), keepRanges[2])
    }

    @Test
    fun testOverlappingSegments() {
        val segments = listOf(
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 10.0, 25.0),
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 20.0, 35.0),
        )
        val keepRanges = SegmentProcessor.computeKeepRanges(segments, 100.0)

        assertEquals(2, keepRanges.size)
        assertEquals(Pair(0.0, 10.0), keepRanges[0])
        assertEquals(Pair(35.0, 100.0), keepRanges[1])
    }

    @Test
    fun testTinyGapCleanup() {
        // Gap of 0.3 seconds is less than 0.5 default threshold, so they should merge
        val segments = listOf(
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 10.0, 20.0),
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 20.3, 30.0),
        )
        val keepRanges = SegmentProcessor.computeKeepRanges(segments, 100.0)

        assertEquals(2, keepRanges.size)
        assertEquals(Pair(0.0, 10.0), keepRanges[0])
        assertEquals(Pair(30.0, 100.0), keepRanges[1])
    }

    @Test
    fun testClamping() {
        // Segment starting before 0 and ending after duration
        val segments = listOf(
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, -5.0, 15.0),
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 90.0, 105.0),
        )
        val keepRanges = SegmentProcessor.computeKeepRanges(segments, 100.0)

        assertEquals(1, keepRanges.size)
        assertEquals(Pair(15.0, 90.0), keepRanges[0])
    }

    @Test
    fun testFilteringTinyKeepRanges() {
        // Keep range between segments is extremely tiny (0.05 seconds), should be filtered out
        val segments = listOf(
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 10.0, 20.0),
            SponsorBlockSegment(SponsorBlockCategory.SPONSOR, 20.05, 30.0),
        )
        // Set gapThreshold to 0.0 to prevent merging, forcing a tiny keep range to be computed
        val merged = SegmentProcessor.mergeSegments(segments, gapThresholdSeconds = 0.0)
        val keepRanges = SegmentProcessor.computeKeepRanges(merged, 100.0)

        // The tiny gap 20.0 to 20.05 is 0.05s, which is < 0.1s, so it should be filtered out
        assertEquals(2, keepRanges.size)
        assertEquals(Pair(0.0, 10.0), keepRanges[0])
        assertEquals(Pair(30.0, 100.0), keepRanges[1])
    }
}
