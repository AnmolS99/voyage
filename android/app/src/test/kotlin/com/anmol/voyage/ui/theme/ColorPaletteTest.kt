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
        assertEquals(VoyagePalette.medalBronzeCenter, VoyagePalette.trophyBronzeLight)
        assertEquals(VoyagePalette.medalBronzeEdge, VoyagePalette.trophyBronzeDark)
        assertEquals(VoyagePalette.medalSilverCenter, VoyagePalette.trophySilverLight)
        assertEquals(VoyagePalette.medalSilverEdge, VoyagePalette.trophySilverDark)
        assertEquals(VoyagePalette.medalGoldCenter, VoyagePalette.trophyGoldLight)
        assertEquals(VoyagePalette.medalGoldEdge, VoyagePalette.trophyGoldDark)
    }

    @Test
    fun `medal colors match iOS AppColors`() {
        // iOS writes these as 0–1 components; each is that value × 255, rounded.
        assertEquals(Color(0xFFD98F4C), VoyagePalette.medalBronzeCenter) // 0.85, 0.56, 0.30
        assertEquals(Color(0xFF8C5426), VoyagePalette.medalBronzeEdge) // 0.55, 0.33, 0.15
        assertEquals(Color(0xFF73421A), VoyagePalette.medalBronzeRim) // 0.45, 0.26, 0.10
        assertEquals(Color(0xFF4C4C54), VoyagePalette.medalShadowFace) // 0.30, 0.30, 0.33
        assertEquals(Color(0xFF2B2B30), VoyagePalette.medalShadowSymbol) // 0.17, 0.17, 0.19
        assertEquals(Color(0xFFB0703B), VoyagePalette.medalBronzeAccent) // 0.69, 0.44, 0.23
        assertEquals(Color(0xFF666673), VoyagePalette.medalSilverInk) // 0.40, 0.40, 0.45
        assertEquals(Color(0xFF8C610D), VoyagePalette.medalGoldInk) // 0.55, 0.38, 0.05
        assertEquals(VoyagePalette.medalSilverEdge, VoyagePalette.medalSilverAccent)
        assertEquals(VoyagePalette.medalGoldEdge, VoyagePalette.medalGoldAccent)
    }

    @Test
    fun `dark mode helpers pick the matching variant`() {
        assertEquals(Color.White, VoyagePalette.textPrimary(isDark = true))
        assertEquals(VoyagePalette.textPrimaryLight, VoyagePalette.textPrimary(isDark = false))
        assertEquals(VoyagePalette.trackDark, VoyagePalette.track(isDark = true))
        assertEquals(VoyagePalette.pageBgLight, VoyagePalette.pageBackground(isDark = false))
    }
}
