package com.nihaltp.sbskip.sponsorblock

import com.nihaltp.sbskip.model.SponsorBlockCategory

interface SponsorBlockService {
    suspend fun fetchSegments(
        videoId: String,
        categories: Set<SponsorBlockCategory>,
    ): List<SponsorBlockSegment>

    suspend fun checkApiStatus(): String
}

data class SponsorBlockSegment(
    val category: SponsorBlockCategory,
    val startSeconds: Double,
    val endSeconds: Double,
)
