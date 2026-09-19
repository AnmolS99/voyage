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

    private fun style(
        visited: Boolean = false,
        wishlist: Boolean = false,
        selected: Boolean = false,
        textured: Boolean = false,
    ) = CountryStyles.of(isVisited = visited, isWishlist = wishlist, isSelected = selected, hasTexture = textured)

    @Test
    fun `over an Earth texture plain land is left unpainted`() {
        // iOS's `hasTexture ? .clear : land`, for untouched and selected alike.
        assertEquals(MapShading.None, style(textured = true).fill)
        assertEquals(MapShading.None, style(selected = true, textured = true).fill)
        assertEquals(MapShading.None, style(visited = true, selected = true, textured = true).fill)
        assertEquals(black, style(textured = true).border)
    }

    @Test
    fun `a texture never hides a status`() {
        assertEquals(visited, style(visited = true, textured = true).fill)
        assertEquals(MapShading.VisitedWishlist, style(visited = true, wishlist = true, textured = true).fill)
        assertEquals(wishlist, style(wishlist = true, selected = true, textured = true).border)
    }

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

    @Test
    fun `a challenge highlight is a status, and outranks the others`() {
        val green = VoyagePalette.challengeCorrect
        val highlighted = CountryStyles.of(
            isVisited = true,
            isWishlist = true,
            isSelected = false,
            hasTexture = true,
            highlight = green,
        )
        assertEquals(MapShading.Solid(green), highlighted.fill)
        assertEquals(black, highlighted.border)
    }

    @Test
    fun `a selected highlighted country moves its highlight to the border`() {
        // A revealed miss: outlined in red over the texture, as iOS draws it.
        val red = VoyagePalette.challengeWrong
        val revealed = CountryStyles.of(
            isVisited = false,
            isWishlist = false,
            isSelected = true,
            hasTexture = true,
            highlight = red,
        )
        assertEquals(MapShading.None, revealed.fill)
        assertEquals(MapShading.Solid(red), revealed.border)
    }
}
