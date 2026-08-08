package com.anmol.voyage.state

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
}
