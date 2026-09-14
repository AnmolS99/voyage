package com.anmol.voyage.state

import com.anmol.voyage.data.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The preference enums, and the one piece of logic they carry. */
class PreferencesTest {

    @Test
    fun `following the system means following the system`() {
        assertTrue(ThemeMode.System.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.System.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `an explicit choice overrides the system`() {
        assertTrue(ThemeMode.Dark.isDark(systemInDarkTheme = false))
        assertFalse(ThemeMode.Light.isDark(systemInDarkTheme = true))
    }

    @Test
    fun `every globe style names its own JPEG in shared data`() {
        // iOS bundles the same files, so a rename there without one here would
        // otherwise surface only as a flat-colored globe on Android.
        assertEquals(GlobeStyle.entries.size, GlobeStyle.entries.map { it.textureAsset }.toSet().size)
        for (style in GlobeStyle.entries) {
            SharedFiles.open("shared/data/${style.textureAsset}").use { image ->
                val magic = image.readNBytes(2).map { it.toInt() and 0xFF }
                assertEquals("${style.textureAsset} is not a JPEG", listOf(0xFF, 0xD8), magic)
            }
        }
    }
}
