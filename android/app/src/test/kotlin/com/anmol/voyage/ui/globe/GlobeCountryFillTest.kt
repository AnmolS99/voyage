package com.anmol.voyage.ui.globe

import androidx.compose.ui.graphics.Color
import com.anmol.voyage.ui.theme.VoyagePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The globe's colors, against the same rules `CountryStyleTest` pins for the map.
 *
 * CLAUDE.md makes "globe and map look identical" a rule, and until Phase 7.5 the
 * globe genuinely broke it — with no borders to move a status onto, selection had
 * to brighten the fill instead. Now that both renderers go through
 * `CountryStyles`, this asserts the translation from a shading to Filament
 * uniforms rather than a second set of rules.
 */
class GlobeCountryFillTest {

    private fun fill(visited: Boolean = false, wishlist: Boolean = false, selected: Boolean = false) =
        GlobeCountryFills.of(isVisited = visited, isWishlist = wishlist, isSelected = selected)

    @Test
    fun `an untouched country is flat land green`() {
        val fill = fill()
        assertEquals(VoyagePalette.land, fill.colorA)
        assertFalse(fill.gradient)
    }

    @Test
    fun `status colors the fill when the country is not selected`() {
        assertEquals(VoyagePalette.visited, fill(visited = true).colorA)
        assertEquals(VoyagePalette.wishlist, fill(wishlist = true).colorA)
    }

    @Test
    fun `both lists fill with the visited to wishlist gradient`() {
        val fill = fill(visited = true, wishlist = true)
        assertTrue(fill.gradient)
        assertEquals(VoyagePalette.visited, fill.colorA)
        assertEquals(VoyagePalette.wishlist, fill.colorB)
    }

    @Test
    fun `selection drops the fill to land and moves status to the border`() {
        // The rule that keeps a visited country from looking unvisited while
        // selected — the same assertion CountryStyleTest makes for the map.
        assertEquals(VoyagePalette.land, fill(visited = true, selected = true).colorA)
        assertEquals(
            VoyagePalette.visited,
            GlobeCountryFills.selectedBorderOf(isVisited = true, isWishlist = false).colorA,
        )

        assertEquals(VoyagePalette.land, fill(wishlist = true, selected = true).colorA)
        assertEquals(
            VoyagePalette.wishlist,
            GlobeCountryFills.selectedBorderOf(isVisited = false, isWishlist = true).colorA,
        )
    }

    @Test
    fun `a selected country on both lists gets the gradient on its border`() {
        val border = GlobeCountryFills.selectedBorderOf(isVisited = true, isWishlist = true)
        assertTrue(border.gradient)
        assertEquals(VoyagePalette.visited, border.colorA)
        assertEquals(VoyagePalette.wishlist, border.colorB)
        assertEquals(VoyagePalette.land, fill(visited = true, wishlist = true, selected = true).colorA)
    }

    @Test
    fun `a selected country with no status keeps a black border`() {
        assertEquals(Color.Black, GlobeCountryFills.selectedBorderOf(isVisited = false, isWishlist = false).colorA)
    }

    @Test
    fun `palette colors reach Filament unconverted`() {
        // Only correct because the renderer disables post-processing, and the
        // reason the ocean is #2F86A6 on screen rather than #073D61.
        val components = VoyagePalette.ocean.toFilamentColor()
        assertEquals(VoyagePalette.ocean.red, components[0], 0f)
        assertEquals(VoyagePalette.ocean.green, components[1], 0f)
        assertEquals(VoyagePalette.ocean.blue, components[2], 0f)
        assertEquals(1f, components[3], 0f)
    }
}
