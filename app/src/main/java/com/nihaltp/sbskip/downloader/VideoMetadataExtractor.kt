package com.nihaltp.sbskip.downloader

import com.nihaltp.sbskip.model.VideoMetadata

interface VideoMetadataExtractor {
    suspend fun extract(url: String): VideoMetadata
}
