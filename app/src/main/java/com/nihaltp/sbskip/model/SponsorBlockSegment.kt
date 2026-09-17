package com.nihaltp.sbskip.model

data class SponsorBlockSegment(
    val category: SponsorBlockCategory,
    val startSeconds: Double,
    val endSeconds: Double,
)
