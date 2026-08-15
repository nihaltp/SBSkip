package com.nihaltp.sbskip

import com.nihaltp.sbskip.util.YouTubePlaylistFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RealPlaylistTest {
    @Test
    fun testRealPlaylist() =
        runBlocking {
            val html = java.net.URL("https://www.youtube.com/playlist?list=PLY87vwSUPI1U").readText()
            java.io.File("playlist_test_dump.html").writeText(html)
            val videos = YouTubePlaylistFetcher.parsePlaylistHtml(html)
            println("Fetched videos: ${videos.size}")
            for (video in videos) {
                println("- ${video.title} (${video.videoId})")
            }
            assert(videos.isNotEmpty()) { "Videos should not be empty" }
        }
}
