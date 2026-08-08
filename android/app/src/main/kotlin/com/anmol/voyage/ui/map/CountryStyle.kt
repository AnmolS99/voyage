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
 * Two things are easy to get wrong and are therefore spelled out here rather
 * than inline in the renderer, where the globe (Phase 7) would end up restating
 * them:
 *
 *  - **Status outranks selection.** A selected country's *fill* always drops to
 *    plain land green; its visited/wishlist status moves to the border. That is
 *    what keeps a visited country from looking unvisited while selected.
 *  - **Both lists means a gradient**, on the border when selected and on the
 *    fill otherwise.
 */
object CountryStyles {

    private val LAND = MapShading.Solid(VoyagePalette.land)
    private val VISITED = MapShading.Solid(VoyagePalette.visited)
    private val WISHLIST = MapShading.Solid(VoyagePalette.wishlist)
    private val BORDER = MapShading.Solid(Color.Black)

    /** Border width in screen terms; iOS uses the same 0.5/1.5 points. */
    private val BORDER_WIDTH = 0.5.dp
    private val SELECTED_BORDER_WIDTH = 1.5.dp

    fun of(isVisited: Boolean, isWishlist: Boolean, isSelected: Boolean): CountryStyle {
        val status = when {
            isVisited && isWishlist -> MapShading.VisitedWishlist
            isVisited -> VISITED
            isWishlist -> WISHLIST
            else -> null
        }
        return if (isSelected) {
            CountryStyle(
                fill = LAND,
                border = status ?: BORDER,
                borderWidth = SELECTED_BORDER_WIDTH,
            )
        } else {
            CountryStyle(
                fill = status ?: LAND,
                border = BORDER,
                borderWidth = BORDER_WIDTH,
            )
        }
    }
}
