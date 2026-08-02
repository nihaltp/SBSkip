package com.nihaltp.sbskip.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun testAppSettingsDefaultBypassSmallDurationDifference() {
        val settings = AppSettings()
        assertEquals(false, settings.bypassSmallDurationDifference)
        assertEquals(1, settings.maxDurationDifferenceSeconds)
    }

    @Test
    fun testAppSettingsCustomBypassSmallDurationDifference() {
        val settings =
            AppSettings(
                bypassSmallDurationDifference = true,
                maxDurationDifferenceSeconds = 5,
            )
        assertEquals(true, settings.bypassSmallDurationDifference)
        assertEquals(5, settings.maxDurationDifferenceSeconds)
    }
}
