package com.nihaltp.sbskip.navigation

import android.net.Uri

data class ShareIntentEvent(
    val text: String? = null,
    val fileUri: Uri? = null,
    val token: Long,
)
