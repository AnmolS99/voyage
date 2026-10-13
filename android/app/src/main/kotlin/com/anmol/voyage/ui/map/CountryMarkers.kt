package com.anmol.voyage.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The two markers drawn on top of the world: the capital seal, and the dots that
 * stand in for countries too small to have a shape.
 *
 * The map draws the capital seal **at a fixed size in screen terms**, regardless
 * of its pan and zoom, as iOS's map does. The globe sizes it on the globe
 * instead, gently compensated for zoom (`GlobeCamera.capitalMarkerRadius`, the
 * port of iOS's `capitalMarkerScale`), so it reads as printed on the surface.
 *
 * The microstate dot is the exception. Both renderers give it a fixed size *on
 * the world* — `CountryHitTester.POINT_HIT_RADIUS` of arc, as iOS does — so
 * zooming in grows it with the land around it rather than shrinking it against
 * that land; [MarkerSizes.dotRadiusPx] is only its floor, for when it would
 * otherwise shrink to a speck zoomed out (see `MapProjection.dotRadius` and
 * `GlobeCamera.dotRadiusInWorld`).
 *
 * They are *drawn* differently, and have to be. The map paints them onto its
 * `Canvas` with the helpers below, outside its pan/zoom matrix. The globe builds
 * them as meshes in the Filament scene (see `MarkerMeshes`), because an overlay
 * above the render surface draws from its own copy of the camera and trails the
 * globe by a frame while dragging. What the two share is the shape and its
 * proportions ([CapitalMarker]) and the colors ([CountryStyles], `VoyagePalette`).
 */

/**
 * Marker sizes in pixels at the current density, from the `dp` values the iOS
 * map specifies in points.
 *
 * A dot's border width is not here: it comes from [CountryStyle.borderWidth],
 * because it thickens with selection like any other border.
 */
class MarkerSizes(val dotRadiusPx: Float, val sealRadiusPx: Float)

private val MICROSTATE_DOT_RADIUS = 5.dp

/** The seal's outer edge, rim included — iOS `MapView`'s 6 pt. */
private val CAPITAL_SEAL_RADIUS = 6.dp

@Composable
fun rememberMarkerSizes(): MarkerSizes {
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            MarkerSizes(
                dotRadiusPx = MICROSTATE_DOT_RADIUS.toPx(),
                sealRadiusPx = CAPITAL_SEAL_RADIUS.toPx(),
            )
        }
    }
}

/**
 * The seal's cut-out star as a path, for the map. In a +Y-is-down space, which is
 * what a `Canvas` wants; the globe asks [CapitalMarker] for +Y-up corners instead.
 */
@Composable
fun rememberCapitalStarPath(): Path {
    val sizes = rememberMarkerSizes()
    return remember(sizes) {
        CapitalMarker.starPath(sizes.sealRadiusPx * CapitalMarker.STAR_TO_SEAL_RATIO)
    }
}

/**
 * Draws the capital seal centered on [center]: a terracotta disc whose ink rim
 * ends exactly at [MarkerSizes.sealRadiusPx], with the cream star on top. The
 * same layers, in the same proportions, the globe builds as meshes.
 */
fun DrawScope.drawCapitalSeal(center: Offset, starPath: Path, sizes: MarkerSizes) {
    val rimWidth = sizes.sealRadiusPx * CapitalMarker.RIM_TO_SEAL_RATIO
    val rimCenterline = sizes.sealRadiusPx - rimWidth / 2f
    drawCircle(VoyagePalette.capitalMarker, radius = rimCenterline, center = center)
    drawCircle(
        color = VoyagePalette.capitalMarkerOutline,
        radius = rimCenterline,
        center = center,
        style = Stroke(width = rimWidth),
    )
    translate(left = center.x, top = center.y) {
        drawPath(starPath, SolidColor(VoyagePalette.capitalMarkerInset))
    }
}

/**
 * Draws one Point-feature country as a dot, in its current status colors.
 *
 * The dot carries the same fill and border a shaped country would, so a visited
 * microstate reads as visited and a selected one shows its status on the border.
 */
fun DrawScope.drawMicrostateDot(center: Offset, style: CountryStyle, radius: Float) {
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
 * An unpainted shading is fully transparent, which Skia skips without drawing.
 */
fun MapShading.brush(bounds: Rect): Brush = when (this) {
    is MapShading.Solid -> SolidColor(color)
    MapShading.None -> SolidColor(Color.Transparent)
    MapShading.VisitedWishlist -> Brush.linearGradient(
        colors = listOf(VoyagePalette.visited, VoyagePalette.wishlist),
        start = Offset(bounds.left, bounds.bottom),
        end = Offset(bounds.right, bounds.top),
    )
}
