package com.anmol.voyage.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The flat world map: an equirectangular Compose `Canvas` port of iOS `MapView`.
 *
 * The globe (Phase 7) and this map have to look and behave identically apart from
 * the projection, so everything that decides *appearance* lives outside this
 * composable — [CountryStyles] for colors, [CapitalMarker] for the star,
 * [MapProjection] for the geometry — leaving drawing and gestures here.
 *
 * @param paths countries pre-projected for the current view size; empty while they
 *   are still being built, which draws the ocean alone.
 */
@Composable
fun WorldMap(
    countries: List<GeoJsonCountry>,
    paths: List<CountryPaths>,
    hitTester: CountryHitTester,
    state: VoyageState,
    projection: MapProjection,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    // Keyed on the projection: a view-size change (rotation, foldable, split
    // screen) invalidates a pan built against the old bounds, so zoom resets with it.
    var scale by remember(projection) { mutableFloatStateOf(MapProjection.MIN_SCALE) }
    var offset by remember(projection) { mutableStateOf(Offset.Zero) }

    val oceanColor = if (darkTheme) VoyagePalette.oceanDark else VoyagePalette.oceanMap
    val starPath = rememberCapitalStarPath()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(projection) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom)
                        .coerceIn(MapProjection.MIN_SCALE, MapProjection.MAX_SCALE)
                    // Zoom reaches its clamp long before the pinch does, so the
                    // anchor maths uses the scale change that actually happened.
                    val appliedZoom = newScale / scale
                    // Keeping the pinch centroid stationary: it sits this far from
                    // the view centre, and scaling moves it by (1 - zoom) of that.
                    val anchor = Offset(
                        centroid.x - projection.viewWidth / 2f - offset.x,
                        centroid.y - projection.viewHeight / 2f - offset.y,
                    )
                    val panned = offset + pan + anchor * (1f - appliedZoom)
                    val (x, y) = projection.clampOffset(panned.x, panned.y, newScale)
                    scale = newScale
                    offset = Offset(x, y)
                }
            }
            .pointerInput(projection) {
                detectTapGestures { touch ->
                    val point = projection.lonLatAt(
                        touchX = touch.x,
                        touchY = touch.y,
                        scale = scale,
                        offsetX = offset.x,
                        offsetY = offset.y,
                    )
                    val name = hitTester.findCountry(lat = point.lat, lon = point.lon)
                    if (name != null) state.selectCountry(name, hitTester.center(name))
                }
            },
    ) {
        drawRect(oceanColor)

        // Country shapes go through the pan/zoom matrix so their paths stay as
        // built — the same transform iOS applies to its canvas.
        withTransform({
            translate(size.width / 2f + offset.x, size.height / 2f + offset.y)
            scale(scale, scale, pivot = Offset.Zero)
            translate(-size.width / 2f, -size.height / 2f)
            translate(0f, projection.verticalOffset)
        }) {
            for (country in paths) {
                val style = state.styleFor(country.name)
                drawPath(country.fill, style.fill.brush(country.bounds))
                // The matrix magnifies strokes, so divide out the scale to keep
                // borders a constant width on screen.
                val strokeWidth = style.borderWidth.toPx() / scale
                val borderBrush = style.border.brush(country.bounds)
                for (outline in country.outlines) {
                    drawPath(outline, borderBrush, style = Stroke(width = strokeWidth))
                }
            }
        }

        // Microstates and small island nations are Point features with no shape to
        // fill: they are dots in screen space, so they stay visible — and tappable —
        // at every zoom level, as on iOS.
        for (country in countries) {
            if (!country.isPointCountry) continue
            val coord = country.pointCoordinate ?: continue
            val (x, y) = projection.transform(
                x = projection.mapX(coord.lon),
                y = projection.viewY(coord.lat),
                scale = scale,
                offsetX = offset.x,
                offsetY = offset.y,
            )
            drawMicrostateDot(Offset(x, y), state.styleFor(country.name))
        }

        // The capital star marks the selected country only, as on the globe.
        val selected = state.selectedCountry
        val capital = selected?.let { name -> countries.firstOrNull { it.name == name }?.capital }
        if (capital != null) {
            val (x, y) = projection.transform(
                x = projection.mapX(capital.lon),
                y = projection.viewY(capital.lat),
                scale = scale,
                offsetX = offset.x,
                offsetY = offset.y,
            )
            drawCapitalStar(Offset(x, y), starPath)
        }
    }
}

/** The style for one country given the current visited/wishlist/selection state. */
private fun VoyageState.styleFor(name: String): CountryStyle = CountryStyles.of(
    isVisited = isVisited(name),
    isWishlist = isInWishlist(name),
    isSelected = selectedCountry == name,
)

