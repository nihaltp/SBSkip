package com.nihaltp.sbskip.workers.helpers

import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.sponsorblock.SponsorBlockSegment
import com.nihaltp.sbskip.storage.MediaFileMetadata

data class ProcessingPlan(
    val keepRanges: List<Pair<Double, Double>>,
    val convertVideoToAudio: Boolean,
    val outputExtension: String,
)

data class ProcessingContext(
    val queueItem: DownloadQueueItem,
    val localMetadata: MediaFileMetadata,
    val videoId: String?,
    val oembedTitle: String?,
    val authorName: String?,
    val authorUrl: String?,
    val thumbnailUrl: String?,
    val sbSkipSegments: String,
    val categories: Set<SponsorBlockCategory>,
    val segments: List<SponsorBlockSegment>?,
    val plan: ProcessingPlan,
)
