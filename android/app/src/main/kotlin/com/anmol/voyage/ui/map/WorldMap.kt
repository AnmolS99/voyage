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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlin.math.roundToInt

/**
 * The flat world map: an equirectangular Compose `Canvas` port of iOS `MapView`.
 *
 * The globe (Phase 7) and this map have to look and behave identically apart from
 * the projection, so everything that decides *appearance* lives outside this
 * composable — [CountryStyles] for colors, [CapitalMarker] for the capital seal,
 * [MapProjection] for the geometry — leaving drawing and gestures here.
 *
 * @param scene what to paint and where taps go — Home's travels. Challenges are
 *   played on the globe only, as on iOS, so a game's scene never reaches here.
 * @param paths countries pre-projected for the current view size; empty while they
 *   are still being built, which draws the ocean alone.
 * @param texture the Earth image under the countries. Null draws a flat ocean and
 *   land-green countries instead — iOS's fallback when its image is missing.
 * @param relief the shaded-relief layer, hard-lit over the texture and the fills
 *   but under the borders and markers — what the globe's materials do. Null
 *   draws the world unshaded.
 */
@Composable
fun WorldMap(
    countries: List<GeoJsonCountry>,
    paths: List<CountryPaths>,
    hitTester: CountryHitTester,
    scene: WorldScene,
    projection: MapProjection,
    texture: ImageBitmap?,
    modifier: Modifier = Modifier,
    relief: ImageBitmap? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    // Keyed on the projection: a view-size change (rotation, foldable, split
    // screen) invalidates a pan built against the old bounds, so zoom resets with it.
    // The map opens filling the view, centred on the selection as far as it can be.
    var scale by remember(projection) { mutableFloatStateOf(MapProjection.MIN_SCALE) }
    var offset by remember(projection) {
        val focus = scene.selectedCountry?.let { hitTester.center(it) }
        val (x, y) = focus?.let(projection::offsetCentring) ?: Pair(0f, 0f)
        mutableStateOf(Offset(x, y))
    }

    val oceanColor = if (darkTheme) VoyagePalette.oceanDark else VoyagePalette.oceanMap
    val hasTexture = texture != null
    val sizes = rememberMarkerSizes()
    val starPath = rememberCapitalStarPath()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // The map is scaled to *fill* the view, so in landscape — and when
            // zoomed — it is wider than the view, and a Canvas does not clip:
            // unclipped it painted over the navigation rail and under the
            // system bar beside it.
            .clipToBounds()
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
                    if (name != null) scene.onCountryTapped(name)
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
            translate(projection.horizontalOffset, projection.verticalOffset)
        }) {
            // Spans exactly the map rectangle the paths were built in, so the
            // image lines up with the borders — the rect iOS draws it into.
            if (texture != null) {
                drawImage(
                    image = texture,
                    dstSize = IntSize(projection.mapWidth.roundToInt(), projection.mapHeight.roundToInt()),
                )
            }

            val styles = paths.map { scene.styleFor(it.name, hasTexture) }
            for ((country, style) in paths.zip(styles)) {
                drawPath(country.fill, style.fill.brush(country.bounds))
            }

            // Hard light, the blend the globe's materials apply per fragment:
            // neutral gray leaves a color as it is, so the ocean and flat land
            // keep their palette colors and only slopes shade. Over the fills,
            // under the borders — borders and markers are not shaded on the globe.
            if (relief != null) {
                drawImage(
                    image = relief,
                    dstSize = IntSize(projection.mapWidth.roundToInt(), projection.mapHeight.roundToInt()),
                    blendMode = BlendMode.Hardlight,
                )
            }

            for ((country, style) in paths.zip(styles)) {
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
        // fill: they are dots drawn outside the matrix, sized on the map with a
        // screen-space floor, so they stay visible — and tappable — at every zoom
        // level, as on iOS.
        val dotRadius = projection.dotRadius(sizes.dotRadiusPx, scale)
        for (country in countries) {
            if (!country.isPointCountry) continue
            val coord = country.pointCoordinate ?: continue
            val (x, y) = projection.transform(
                x = projection.viewX(coord.lon),
                y = projection.viewY(coord.lat),
                scale = scale,
                offsetX = offset.x,
                offsetY = offset.y,
            )
            drawMicrostateDot(Offset(x, y), scene.styleFor(country.name, hasTexture), dotRadius)
        }

        // The capital seal marks the selected country only, as on the globe.
        val selected = scene.selectedCountry?.takeIf { scene.showsCapital }
        val capital = selected?.let { name -> countries.firstOrNull { it.name == name }?.capital }
        if (capital != null) {
            val (x, y) = projection.transform(
                x = projection.viewX(capital.lon),
                y = projection.viewY(capital.lat),
                scale = scale,
                offsetX = offset.x,
                offsetY = offset.y,
            )
            drawCapitalSeal(Offset(x, y), starPath, sizes)
        }
    }
}

