package com.nihaltp.sbskip.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchlistFolder(
    val path: String,
    val uri: String,
)
