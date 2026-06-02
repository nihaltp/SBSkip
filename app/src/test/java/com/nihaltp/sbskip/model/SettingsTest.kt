package com.nihaltp.sbskip.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun testAppSettingsDefaultAudioSaveMode() {
        val settings = AppSettings()
        assertEquals(AudioSaveMode.PRESET_FOLDER, settings.audioSaveMode)
    }

    @Test
    fun testAppSettingsCustomAudioSaveMode() {
        val settings = AppSettings(
            audioSaveMode = AudioSaveMode.RUNTIME_PICKER,
        )
        assertEquals(AudioSaveMode.RUNTIME_PICKER, settings.audioSaveMode)
    }

    @Test
    fun testAudioSaveModeEnumEntries() {
        val entries = AudioSaveMode.entries
        assertEquals(2, entries.size)
        assertEquals(AudioSaveMode.PRESET_FOLDER, entries[0])
        assertEquals(AudioSaveMode.RUNTIME_PICKER, entries[1])
    }
}
