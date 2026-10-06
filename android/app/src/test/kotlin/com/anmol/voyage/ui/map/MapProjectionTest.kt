package com.anmol.voyage.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The projection is what ties what the user sees to what the hit tester answers,
 * so both directions are pinned here: a country drawn at the wrong place and a tap
 * read at the wrong place are the same bug seen from two sides.
 *
 * The view is deliberately portrait (1000×2000): the map fills its height, so it
 * is 4000×2000 and overhangs 1500px on each side, which is what a phone does.
 */
class MapProjectionTest {

    private val projection = MapProjection(viewWidth = 1000f, viewHeight = 2000f)
    private val landscape = MapProjection(viewWidth = 2000f, viewHeight = 800f)
    private val tolerance = 1e-3f

    @Test
    fun `in portrait the map fills the height and is centred horizontally`() {
        assertEquals(4000f, projection.mapWidth, tolerance)
        assertEquals(2000f, projection.mapHeight, tolerance)
        assertEquals(-1500f, projection.horizontalOffset, tolerance)
        assertEquals(0f, projection.verticalOffset, tolerance)
    }

    @Test
    fun `in landscape the map fills the width and is centred vertically`() {
        assertEquals(2000f, landscape.mapWidth, tolerance)
        assertEquals(1000f, landscape.mapHeight, tolerance)
        assertEquals(0f, landscape.horizontalOffset, tolerance)
        assertEquals(-100f, landscape.verticalOffset, tolerance)
    }

    @Test
    fun `corners and centre project as equirectangular`() {
        assertEquals(0f, projection.mapX(-180.0), tolerance)
        assertEquals(2000f, projection.mapX(0.0), tolerance)
        assertEquals(4000f, projection.mapX(180.0), tolerance)

        assertEquals(0f, projection.mapY(90.0), tolerance)
        assertEquals(1000f, projection.mapY(0.0), tolerance)
        assertEquals(2000f, projection.mapY(-90.0), tolerance)
    }

    @Test
    fun `centring shifts the map into view space`() {
        assertEquals(500f, projection.viewX(0.0), tolerance)
        assertEquals(1000f, projection.viewY(0.0), tolerance)
        assertEquals(0f, projection.viewY(90.0), tolerance)
        assertEquals(-100f, landscape.viewY(90.0), tolerance)
    }

    @Test
    fun `a tap at the view centre reads as null island`() {
        for (p in listOf(projection, landscape)) {
            val point = p.lonLatAt(p.viewWidth / 2f, p.viewHeight / 2f, scale = 1f, offsetX = 0f, offsetY = 0f)
            assertEquals(0.0, point.lat, 1e-6)
            assertEquals(0.0, point.lon, 1e-6)
        }
    }

    @Test
    fun `projecting then reading a tap round-trips under pan and zoom`() {
        val scale = 3.2f
        val offsetX = 41f
        val offsetY = -25f
        for (p in listOf(projection, landscape)) {
            for ((lat, lon) in listOf(59.91 to 10.75, -33.9 to 18.4, 64.9 to -19.0, 0.0 to 179.5)) {
                val (x, y) = p.transform(
                    x = p.viewX(lon),
                    y = p.viewY(lat),
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )
                val point = p.lonLatAt(x, y, scale, offsetX, offsetY)
                assertEquals(lat, point.lat, 1e-4)
                assertEquals(lon, point.lon, 1e-4)
            }
        }
    }

    @Test
    fun `at minimum zoom the map pans only along its overhang`() {
        // Portrait: 1500px of overhang either side, none above or below.
        assertClamped(projection, 400f, 0f, offsetX = 400f, offsetY = -900f, scale = 1f)
        assertClamped(projection, -1500f, 0f, offsetX = -2000f, offsetY = 900f, scale = 1f)
        // Landscape: 100px above and below, none to the sides.
        assertClamped(landscape, 0f, -100f, offsetX = 400f, offsetY = -900f, scale = 1f)
    }

    @Test
    fun `the view never sees past an edge of the map`() {
        // Whatever the pan, the map's edges stay at or beyond the view's edges.
        for (p in listOf(projection, landscape)) {
            for (scale in listOf(1f, 2.5f, 10f)) {
                for ((ox, oy) in listOf(1e6f to 1e6f, -1e6f to -1e6f)) {
                    val (x, y) = p.clampOffset(ox, oy, scale)
                    val (left, top) = p.transform(p.horizontalOffset, p.verticalOffset, scale, x, y)
                    val (right, bottom) = p.transform(
                        p.horizontalOffset + p.mapWidth,
                        p.verticalOffset + p.mapHeight,
                        scale,
                        x,
                        y,
                    )
                    assertTrue(left <= tolerance && top <= tolerance)
                    assertTrue(right >= p.viewWidth - tolerance && bottom >= p.viewHeight - tolerance)
                }
            }
        }
    }

    @Test
    fun `panning is limited to how far the scaled map overhangs`() {
        // At 2x the portrait map is 8000x4000: 3500px of overhang to each side and
        // 1000px above and below.
        assertClamped(projection, 3500f, 1000f, offsetX = 5000f, offsetY = 1200f, scale = 2f)
        assertClamped(projection, -3500f, -1000f, offsetX = -5000f, offsetY = -1200f, scale = 2f)
        assertClamped(projection, 120f, 500f, offsetX = 120f, offsetY = 500f, scale = 2f)
    }

    /** Compares components with a tolerance; a clamp to zero can produce `-0.0f`. */
    private fun assertClamped(
        projection: MapProjection,
        expectedX: Float,
        expectedY: Float,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
    ) {
        val (x, y) = projection.clampOffset(offsetX, offsetY, scale)
        assertEquals(expectedX, x, tolerance)
        assertEquals(expectedY, y, tolerance)
    }
}
