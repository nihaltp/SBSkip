package com.nihaltp.sbskip.sponsorblock

import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.model.SponsorBlockSegment

interface SponsorBlockService {
    suspend fun fetchSegments(
        videoId: String,
        categories: Set<SponsorBlockCategory>,
    ): List<SponsorBlockSegment>

    suspend fun checkApiStatus(): String
}
