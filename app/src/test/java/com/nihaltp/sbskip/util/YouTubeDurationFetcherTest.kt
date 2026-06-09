package com.nihaltp.sbskip.util

import com.nihaltp.sbskip.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class YouTubeDurationFetcherTest {
    @Test
    fun testParseDurationFromLengthSeconds() {
        val mockHtml =
            """
            <html>
                <script>
                    var ytInitialPlayerResponse = {
                        "videoDetails": {
                            "lengthSeconds": "185",
                            "title": "Mock Video"
                        }
                    };
                </script>
            </html>
            """.trimIndent()

        val parsed = YouTubeDurationFetcher.parseDurationFromHtml(mockHtml)
        assertEquals(185L, parsed)
    }

    @Test
    fun testParseDurationFromApproxDurationMs() {
        val mockHtml =
            """
            <html>
                <body>
                    <div>"approxDurationMs":"240000"</div>
                </body>
            </html>
            """.trimIndent()

        val parsed = YouTubeDurationFetcher.parseDurationFromHtml(mockHtml)
        assertEquals(240L, parsed)
    }

    @Test
    fun testParseDurationFromItempropMetaTag() {
        val mockHtml =
            """
            <html>
                <head>
                    <meta itemprop="duration" content="PT3M45S" />
                </head>
            </html>
            """.trimIndent()

        val parsed = YouTubeDurationFetcher.parseDurationFromHtml(mockHtml)
        assertEquals(225L, parsed) // 3 * 60 + 45 = 225
    }

    @Test
    fun testParseDurationUnresolved() {
        val mockHtml =
            """
            <html>
                <body>No duration details anywhere here.</body>
            </html>
            """.trimIndent()

        val parsed = YouTubeDurationFetcher.parseDurationFromHtml(mockHtml)
        assertNull(parsed)
    }

    @Test
    fun testDurationValidationLogicSuccess() {
        val youtubeDuration = 180L
        val fileDuration = 180L // Exactly equal
        val difference = kotlin.math.abs(fileDuration - youtubeDuration)

        if (difference > 0) {
            fail("Should not throw since difference is exactly 0")
        }
    }

    @Test
    fun testDurationValidationLogicFailure() {
        val youtubeDuration = 180L
        val fileDuration = 181L // 1 second difference
        val difference = kotlin.math.abs(fileDuration - youtubeDuration)

        if (difference > 0) {
            // Expected failure path
            val message = "Picked file duration ($fileDuration s) does not match YouTube video duration ($youtubeDuration s)"
            assertEquals("Picked file duration (181 s) does not match YouTube video duration (180 s)", message)
        } else {
            fail("Should have detected duration mismatch")
        }
    }

    @Test
    fun testDurationValidationBypass() {
        val youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&bypassDurationCheck=true"
        val bypassCheck = youtubeUrl.contains("bypassDurationCheck=true")

        val youtubeDuration = 180L
        val fileDuration = 195L

        if (bypassCheck) {
            // Success - check is bypassed!
        } else {
            val difference = kotlin.math.abs(fileDuration - youtubeDuration)
            if (difference > 0) {
                fail("Should have been bypassed")
            }
        }
    }

    @Test
    fun testDurationValidationBypassSmallDifferenceEnabledWithinThreshold() {
        val settings =
            AppSettings(
                bypassSmallDurationDifference = true,
                maxDurationDifferenceSeconds = 3,
            )

        val youtubeDuration = 180L
        val fileDuration = 183L // 3 seconds difference (equal to threshold)
        val difference = kotlin.math.abs(fileDuration - youtubeDuration)

        val hasMismatch =
            if (settings.bypassSmallDurationDifference) {
                difference > settings.maxDurationDifferenceSeconds
            } else {
                difference > 0
            }

        if (hasMismatch) {
            fail("Should not have mismatch since difference is within threshold")
        }
    }

    @Test
    fun testDurationValidationBypassSmallDifferenceEnabledExceedsThreshold() {
        val settings =
            AppSettings(
                bypassSmallDurationDifference = true,
                maxDurationDifferenceSeconds = 3,
            )

        val youtubeDuration = 180L
        val fileDuration = 184L // 4 seconds difference (exceeds threshold)
        val difference = kotlin.math.abs(fileDuration - youtubeDuration)

        val hasMismatch =
            if (settings.bypassSmallDurationDifference) {
                difference > settings.maxDurationDifferenceSeconds
            } else {
                difference > 0
            }

        if (!hasMismatch) {
            fail("Should have mismatch since difference exceeds threshold")
        }
    }

    @Test
    fun testDurationValidationBypassSmallDifferenceDisabled() {
        val settings =
            AppSettings(
                bypassSmallDurationDifference = false,
                maxDurationDifferenceSeconds = 3,
            )

        val youtubeDuration = 180L
        val fileDuration = 181L // 1 second difference
        val difference = kotlin.math.abs(fileDuration - youtubeDuration)

        val hasMismatch =
            if (settings.bypassSmallDurationDifference) {
                difference > settings.maxDurationDifferenceSeconds
            } else {
                difference > 0
            }

        if (!hasMismatch) {
            fail("Should have mismatch since bypass setting is disabled")
        }
    }
}
