package com.nihaltp.sbskip.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun testAppSettingsDefaultAudioSaveMode() {
        val settings = AppSettings()
        assertEquals(AudioSaveMode.RUNTIME_PICKER, settings.audioSaveMode)
    }

    @Test
    fun testAppSettingsCustomAudioSaveMode() {
        val settings =
            AppSettings(
                audioSaveMode = AudioSaveMode.PRESET_FOLDER,
            )
        assertEquals(AudioSaveMode.PRESET_FOLDER, settings.audioSaveMode)
    }

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

    @Test
    fun testAudioSaveModeEnumEntries() {
        val entries = AudioSaveMode.entries
        assertEquals(2, entries.size)
        assertEquals(AudioSaveMode.PRESET_FOLDER, entries[0])
        assertEquals(AudioSaveMode.RUNTIME_PICKER, entries[1])
    }
}
