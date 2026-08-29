package com.anmol.voyage.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The two markers drawn on top of the world: the capital star, and the dots that
 * stand in for countries too small to have a shape.
 *
 * Both renderers size these the same way — **in screen terms, at a fixed size**,
 * regardless of what the world underneath is doing. That is what keeps a
 * microstate visible and tappable at every zoom instead of shrinking to a
 * subpixel, and it is the reason [MarkerSizes] lives here rather than in either
 * renderer: CLAUDE.md requires the globe and the map to draw capitals and dots
 * identically, and one shared measurement is a stronger guarantee than two that
 * happen to agree.
 *
 * They are *drawn* differently, and have to be. The map paints them onto its
 * `Canvas` with the helpers below, outside its pan/zoom matrix. The globe builds
 * them as meshes in the Filament scene (see `MarkerMeshes`), because an overlay
 * above the render surface draws from its own copy of the camera and trails the
 * globe by a frame while dragging. What the two share is the shape
 * ([CapitalMarker]), the colors ([CountryStyles]) and the sizes here.
 *
 * Note that iOS's globe instead gives its markers a fixed *world* size and
 * compensates the star with `capitalMarkerScale`'s `sqrt(zoomScale)`, so its
 * globe and map disagree about how big a marker is. Android's agree.
 */

/**
 * Marker sizes in pixels at the current density, from the `dp` values the iOS
 * map specifies in points.
 *
 * A dot's border width is not here: it comes from [CountryStyle.borderWidth],
 * because it thickens with selection like any other border.
 */
class MarkerSizes(val dotRadiusPx: Float, val starRadiusPx: Float, val starOutlinePx: Float)

private val MICROSTATE_DOT_RADIUS = 5.dp
private val CAPITAL_STAR_RADIUS = 6.dp
private val CAPITAL_STAR_OUTLINE = 1.dp

@Composable
fun rememberMarkerSizes(): MarkerSizes {
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            MarkerSizes(
                dotRadiusPx = MICROSTATE_DOT_RADIUS.toPx(),
                starRadiusPx = CAPITAL_STAR_RADIUS.toPx(),
                starOutlinePx = CAPITAL_STAR_OUTLINE.toPx(),
            )
        }
    }
}

/**
 * The capital star as a path, for the map. In a +Y-is-down space, which is what
 * a `Canvas` wants; the globe asks [CapitalMarker] for +Y-up corners instead.
 */
@Composable
fun rememberCapitalStarPath(): Path {
    val sizes = rememberMarkerSizes()
    return remember(sizes) { CapitalMarker.starPath(sizes.starRadiusPx) }
}

/** Draws the capital star centered on [center]. */
fun DrawScope.drawCapitalStar(center: Offset, starPath: Path, sizes: MarkerSizes) {
    translate(left = center.x, top = center.y) {
        drawPath(starPath, SolidColor(VoyagePalette.capitalMarker))
        drawPath(
            path = starPath,
            brush = SolidColor(VoyagePalette.capitalMarkerOutline),
            style = Stroke(width = sizes.starOutlinePx),
        )
    }
}

/**
 * Draws one Point-feature country as a dot, in its current status colors.
 *
 * The dot carries the same fill and border a shaped country would, so a visited
 * microstate reads as visited and a selected one shows its status on the border.
 */
fun DrawScope.drawMicrostateDot(center: Offset, style: CountryStyle, sizes: MarkerSizes) {
    val radius = sizes.dotRadiusPx
    val bounds = Rect(center = center, radius = radius)
    drawCircle(style.fill.brush(bounds), radius = radius, center = center)
    drawCircle(
        brush = style.border.brush(bounds),
        radius = radius,
        center = center,
        style = Stroke(width = style.borderWidth.toPx()),
    )
}

/**
 * Resolves a shading to a brush. The visited+wishlist gradient runs bottom-left to
 * top-right across the shape being painted, so it needs that shape's bounds.
 */
fun MapShading.brush(bounds: Rect): Brush = when (this) {
    is MapShading.Solid -> SolidColor(color)
    MapShading.VisitedWishlist -> Brush.linearGradient(
        colors = listOf(VoyagePalette.visited, VoyagePalette.wishlist),
        start = Offset(bounds.left, bounds.bottom),
        end = Offset(bounds.right, bounds.top),
    )
}
