package com.nihaltp.sbskip

import com.nihaltp.sbskip.util.YouTubePlaylistFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RealPlaylistTest {
    @Test
    fun testRealPlaylist() =
        runBlocking {
            val playlistId = "PLY87vwSUPI1U"
            val fixtureFile = java.io.File("src/test/resources/fixtures/playlist_$playlistId.html")

            val html =
                if (fixtureFile.exists()) {
                    fixtureFile.readText()
                } else {
                    val downloadedHtml = java.net.URL("https://www.youtube.com/playlist?list=$playlistId").readText()
                    fixtureFile.parentFile?.mkdirs()
                    fixtureFile.writeText(downloadedHtml)
                    downloadedHtml
                }

            val videos = YouTubePlaylistFetcher.parsePlaylistHtml(html)
            println("Fetched videos: ${videos.size}")
            for (video in videos) {
                println("- ${video.title} (${video.videoId})")
            }
            assert(videos.isNotEmpty()) { "Videos should not be empty" }

            // Clean up the generated fixture file at the end of the test
            if (fixtureFile.exists()) {
                fixtureFile.delete()
            }
        }
}
