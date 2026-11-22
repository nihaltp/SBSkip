package com.nihaltp.sbskip.model

/**
 * Default download folder paths used when the user has not selected a custom folder.
 *
 * Kept in a single file so the default is consistent across settings reads,
 * the AppSettings data class defaults, and MediaStore path computation.
 */
object DownloadFolderDefaults {
    const val VIDEO_FOLDER = "Download/SBSkip/Video/"
    const val AUDIO_FOLDER = "Download/SBSkip/Audio/"
}
