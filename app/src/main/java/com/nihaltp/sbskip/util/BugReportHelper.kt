package com.nihaltp.sbskip.util

import android.net.Uri
import com.nihaltp.sbskip.model.DownloadQueueItem

fun buildGithubBugReportUrl(
    versionName: String,
    item: DownloadQueueItem,
): String {
    val titleParam = Uri.encode("[Bug] Pipeline clean failed: ${item.errorMessage?.take(45)}")
    val bodyTemplate =
        """
        |### SB Skip Pipeline Error Report
        |
        |* **App Version**: $versionName
        |* **Video Title**: ${item.title}
        |* **Video URL**: ${item.cleanUrl}
        |* **Media Type**: ${item.mediaType.name}
        |
        |#### Exception Stack Trace
        |```text
        |${item.errorMessage}
        |```
        """.trimMargin()
    val bodyParam = Uri.encode(bodyTemplate)
    return "https://github.com/nihaltp/SBSkip/issues/new?title=$titleParam&body=$bodyParam"
}
