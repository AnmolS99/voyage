package com.anmol.voyage.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast, and the label colors the status chips choose with it. */
class ContrastTest {

    @Test
    fun blackOnWhiteIsTwentyOneToOne() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(VoyagePalette.wishlist, VoyagePalette.wishlist), 0.001f)
    }

    @Test
    fun theVisitedGreenTakesABlackLabel() {
        assertEquals(Color.Black, readableContentColor(VoyagePalette.buttonVisited))
    }

    @Test
    fun aDarkColorTakesAWhiteLabel() {
        assertEquals(Color.White, readableContentColor(VoyagePalette.oceanDark))
    }

    /** The selected visited and wishlist chips, at the 4.5:1 AA asks of body text. */
    @Test
    fun everyStatusChipLabelPassesAa() {
        for (color in listOf(VoyagePalette.buttonVisited, VoyagePalette.wishlist)) {
            val ratio = contrastRatio(color, readableContentColor(color))
            assertTrue("$color labels at $ratio:1", ratio >= 4.5f)
        }
    }
}
