package com.anmol.voyage.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anmol.voyage.ui.theme.VoyagePalette

/** How one country's fill or border is painted. */
sealed interface MapShading {

    data class Solid(val color: Color) : MapShading

    /**
     * The diagonal visited→wishlist gradient (yellow at bottom-left, purple at
     * top-right) used when a country is on both lists. The stops come from the
     * palette; the geometry depends on the shape being painted, so the renderer
     * builds the actual brush.
     */
    data object VisitedWishlist : MapShading

    /**
     * Not painted, so the Earth texture underneath shows through — what iOS's
     * map paints as `.clear`, and its globe gets by hiding the country's node.
     */
    data object None : MapShading
}

/** Fill, border, and border width for one country in its current state. */
data class CountryStyle(
    val fill: MapShading,
    val border: MapShading,
    val borderWidth: Dp,
)

/**
 * The map's country styling rules, ported 1:1 from iOS `MapView`.
 *
 * Three things are easy to get wrong and are therefore spelled out here rather
 * than inline in the renderer, where the globe (Phase 7) would end up restating
 * them:
 *
 *  - **Status outranks selection.** A selected country's *fill* always drops to
 *    plain land; its visited/wishlist status moves to the border. That is what
 *    keeps a visited country from looking unvisited while selected.
 *  - **Both lists means a gradient**, on the border when selected and on the
 *    fill otherwise.
 *  - **Plain land is the texture.** With an Earth texture drawn underneath,
 *    plain land is [MapShading.None] so the texture shows through — iOS's
 *    `hasTexture ? .clear : land`. Land green is only the fallback for a texture
 *    that could not be loaded.
 */
object CountryStyles {

    private val LAND = MapShading.Solid(VoyagePalette.land)
    private val VISITED = MapShading.Solid(VoyagePalette.visited)
    private val WISHLIST = MapShading.Solid(VoyagePalette.wishlist)
    private val BORDER = MapShading.Solid(Color.Black)

    /** Border width in screen terms; iOS uses the same 0.5/1.5 points. */
    private val BORDER_WIDTH = 0.5.dp
    private val SELECTED_BORDER_WIDTH = 1.5.dp

    /**
     * @param highlight a challenge game's temporary color for the country —
     *   green when found, red when revealed. It is a status like the others and
     *   outranks them, as iOS's `countryHighlightColors` does; games play on a
     *   scene with nothing visited, so in practice it stands alone.
     */
    fun of(
        isVisited: Boolean,
        isWishlist: Boolean,
        isSelected: Boolean,
        hasTexture: Boolean,
        highlight: Color? = null,
    ): CountryStyle {
        val status = when {
            highlight != null -> MapShading.Solid(highlight)
            isVisited && isWishlist -> MapShading.VisitedWishlist
            isVisited -> VISITED
            isWishlist -> WISHLIST
            else -> null
        }
        val land = if (hasTexture) MapShading.None else LAND
        return if (isSelected) {
            CountryStyle(
                fill = land,
                border = status ?: BORDER,
                borderWidth = SELECTED_BORDER_WIDTH,
            )
        } else {
            CountryStyle(
                fill = status ?: land,
                border = BORDER,
                borderWidth = BORDER_WIDTH,
            )
        }
    }
}
