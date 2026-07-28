package com.nihaltp.sbskip.util

object Constants {
    const val YOUTUBE_BASE_URL = "https://www.youtube.com/"
    const val YOUTUBE_WATCH_URL_PREFIX = "${YOUTUBE_BASE_URL}watch?v="
    const val YOUTUBE_THUMBNAIL_URL_PREFIX = "https://img.youtube.com/vi/"
    const val SPONSORBLOCK_BASE_URL = "https://sponsor.ajay.app"
    const val SPONSORBLOCK_STATUS_BASE_URL = "https://status.sponsor.ajay.app"
    const val GITHUB_ISSUE_BASE_URL = "https://github.com/nihaltp/SBSkip/issues/new"

    fun buildYouTubeWatchUrl(videoId: String): String = "$YOUTUBE_WATCH_URL_PREFIX$videoId"

    fun buildYouTubeThumbnailUrl(videoId: String): String = "$YOUTUBE_THUMBNAIL_URL_PREFIX$videoId/mqdefault.jpg"

    fun buildGithubIssueUrl(
        titleParam: String,
        bodyParam: String,
    ): String = "$GITHUB_ISSUE_BASE_URL?title=$titleParam&body=$bodyParam"
}
