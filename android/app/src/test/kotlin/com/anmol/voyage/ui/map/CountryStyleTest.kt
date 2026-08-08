package com.anmol.voyage.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anmol.voyage.ui.theme.VoyagePalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The map's color rules, pinned against `ios/voyage/MapView.swift`.
 *
 * The globe has to reach the same conclusions in Phase 7, and CLAUDE.md makes
 * "globe and map look identical" a rule rather than an aspiration — so the rules
 * are asserted here, once, instead of being eyeballed in two renderers.
 */
class CountryStyleTest {

    private val land = MapShading.Solid(VoyagePalette.land)
    private val visited = MapShading.Solid(VoyagePalette.visited)
    private val wishlist = MapShading.Solid(VoyagePalette.wishlist)
    private val black = MapShading.Solid(Color.Black)

    private fun style(visited: Boolean = false, wishlist: Boolean = false, selected: Boolean = false) =
        CountryStyles.of(isVisited = visited, isWishlist = wishlist, isSelected = selected)

    @Test
    fun `an untouched country is land green with a thin black border`() {
        val style = style()
        assertEquals(land, style.fill)
        assertEquals(black, style.border)
        assertEquals(0.5.dp, style.borderWidth)
    }

    @Test
    fun `visited and wishlist countries take their status color as the fill`() {
        assertEquals(visited, style(visited = true).fill)
        assertEquals(wishlist, style(wishlist = true).fill)
        assertEquals(black, style(visited = true).border)
    }

    @Test
    fun `a country on both lists is filled with the gradient`() {
        assertEquals(MapShading.VisitedWishlist, style(visited = true, wishlist = true).fill)
    }

    @Test
    fun `selection thickens the border`() {
        assertEquals(1.5.dp, style(selected = true).borderWidth)
        assertEquals(0.5.dp, style().borderWidth)
    }

    @Test
    fun `status outranks selection by moving to the border`() {
        // The fill drops to plain land while selected, and the status color takes
        // over the border — so a visited country never *looks* unvisited.
        val visitedAndSelected = style(visited = true, selected = true)
        assertEquals(land, visitedAndSelected.fill)
        assertEquals(visited, visitedAndSelected.border)

        val wishlistAndSelected = style(wishlist = true, selected = true)
        assertEquals(land, wishlistAndSelected.fill)
        assertEquals(wishlist, wishlistAndSelected.border)

        val bothAndSelected = style(visited = true, wishlist = true, selected = true)
        assertEquals(land, bothAndSelected.fill)
        assertEquals(MapShading.VisitedWishlist, bothAndSelected.border)
    }

    @Test
    fun `a selected country with no status keeps a black border`() {
        assertEquals(black, style(selected = true).border)
    }
}
