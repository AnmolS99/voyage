package com.anmol.voyage.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the palette against the color table documented in CLAUDE.md, which the
 * iOS `AppColors` enum implements with the same values. If a color changes on
 * one platform without the other, this fails.
 */
class ColorPaletteTest {

    @Test
    fun `documented palette colors match CLAUDE_md`() {
        assertEquals(Color(0xFF2F86A6), VoyagePalette.ocean)
        assertEquals(Color(0xFF34BE82), VoyagePalette.land)
        assertEquals(Color(0xFF73D999), VoyagePalette.landSelected)
        assertEquals(Color(0xFFF2F013), VoyagePalette.visited)
        assertEquals(Color(0xFFFFFF4C), VoyagePalette.visitedSelected)
        assertEquals(Color(0xFF9966CC), VoyagePalette.wishlist)
        assertEquals(Color(0xFFBF8CF2), VoyagePalette.wishlistSelected)
        assertEquals(Color(0xFFD98C59), VoyagePalette.buttonColor)
    }

    @Test
    fun `capital marker reuses the theme orange`() {
        assertEquals(VoyagePalette.buttonColor, VoyagePalette.capitalMarker)
    }

    @Test
    fun `trophy gradients reuse the medal palette`() {
        assertEquals(VoyagePalette.medalSilverCenter, VoyagePalette.trophySilverLight)
        assertEquals(VoyagePalette.medalSilverEdge, VoyagePalette.trophySilverDark)
        assertEquals(VoyagePalette.medalGoldCenter, VoyagePalette.trophyGoldLight)
        assertEquals(VoyagePalette.medalGoldEdge, VoyagePalette.trophyGoldDark)
    }

    @Test
    fun `dark mode helpers pick the matching variant`() {
        assertEquals(Color.White, VoyagePalette.textPrimary(isDark = true))
        assertEquals(VoyagePalette.textPrimaryLight, VoyagePalette.textPrimary(isDark = false))
        assertEquals(VoyagePalette.trackDark, VoyagePalette.track(isDark = true))
        assertEquals(VoyagePalette.pageBgLight, VoyagePalette.pageBackground(isDark = false))
    }
}
