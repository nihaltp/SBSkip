package com.nihaltp.sbskip.processing

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide lock that serializes all FFmpeg sessions.
 *
 * FFmpeg runs in-process and allocates large frame buffers; concurrent sessions
 * compete for the same app heap and can trigger OOM / Low Memory Killer,
 * freezing the UI (white screen) and killing sibling yt-dlp downloads (exit=137).
 *
 * Both [FFmpegMediaProcessor] and [MediaTagger] share this single lock so that
 * their FFmpeg sessions can never overlap, regardless of how many Hilt instances
 * of either class exist.
 */
@Singleton
class FFmpegLock @Inject constructor() {
    val mutex = Mutex()
}
