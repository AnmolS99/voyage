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
 * Both renderers draw these **in screen space**, at a fixed size, outside
 * whatever transform moves the world underneath — the flat map outside its
 * pan/zoom matrix, the globe as an overlay above the Filament surface. That is
 * what keeps a microstate tappable at every zoom instead of shrinking to a
 * subpixel, and it is why the globe needs no equivalent of the iOS
 * `capitalMarkerScale` compensation: iOS draws its star as a world-space mesh
 * and has to undo the perspective shrink, and this does not.
 *
 * Living here rather than in either renderer is the same call `CountryStyles`
 * makes: CLAUDE.md requires the globe and the map to draw capitals and dots
 * identically, and one shared drawing path is a stronger guarantee than two that
 * happen to agree.
 */

/** Marker sizes in screen terms, matching the iOS map's points. */
val MICROSTATE_DOT_RADIUS = 5.dp
private val CAPITAL_STAR_RADIUS = 6.dp
private val CAPITAL_STAR_OUTLINE = 1.dp

/**
 * The capital star, sized once per density.
 *
 * The path is in a +Y-is-down space, which is what both callers want: this is
 * screen space, not the globe's +Y-up world. (iOS carries a `yUp` flag on the
 * shared star for exactly that reason — its globe *does* draw the star as
 * geometry in the scene.)
 */
@Composable
fun rememberCapitalStarPath(): Path {
    val density = LocalDensity.current
    return remember(density) { CapitalMarker.starPath(with(density) { CAPITAL_STAR_RADIUS.toPx() }) }
}

/** Draws the capital star centered on [center]. */
fun DrawScope.drawCapitalStar(center: Offset, starPath: Path) {
    translate(left = center.x, top = center.y) {
        drawPath(starPath, SolidColor(VoyagePalette.capitalMarker))
        drawPath(
            path = starPath,
            brush = SolidColor(VoyagePalette.capitalMarkerOutline),
            style = Stroke(width = CAPITAL_STAR_OUTLINE.toPx()),
        )
    }
}

/**
 * Draws one Point-feature country as a dot, in its current status colors.
 *
 * The dot carries the same fill and border a shaped country would, so a visited
 * microstate reads as visited and a selected one shows its status on the border.
 */
fun DrawScope.drawMicrostateDot(center: Offset, style: CountryStyle) {
    val radius = MICROSTATE_DOT_RADIUS.toPx()
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
