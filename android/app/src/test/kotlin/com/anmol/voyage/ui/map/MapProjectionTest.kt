package com.anmol.voyage.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The projection is what ties what the user sees to what the hit tester answers,
 * so both directions are pinned here: a country drawn at the wrong place and a tap
 * read at the wrong place are the same bug seen from two sides.
 *
 * The view is deliberately taller than the map (1000×2000 gives a 1000×500 map
 * letterboxed by 750px top and bottom), which is what a phone in portrait does.
 */
class MapProjectionTest {

    private val projection = MapProjection(viewWidth = 1000f, viewHeight = 2000f)
    private val tolerance = 1e-3f

    @Test
    fun `the map keeps a 2 to 1 ratio and is centred vertically`() {
        assertEquals(1000f, projection.mapWidth, tolerance)
        assertEquals(500f, projection.mapHeight, tolerance)
        assertEquals(750f, projection.verticalOffset, tolerance)
    }

    @Test
    fun `corners and centre project as equirectangular`() {
        assertEquals(0f, projection.mapX(-180.0), tolerance)
        assertEquals(500f, projection.mapX(0.0), tolerance)
        assertEquals(1000f, projection.mapX(180.0), tolerance)

        assertEquals(0f, projection.mapY(90.0), tolerance)
        assertEquals(250f, projection.mapY(0.0), tolerance)
        assertEquals(500f, projection.mapY(-90.0), tolerance)
    }

    @Test
    fun `letterboxing shifts latitude into view space`() {
        assertEquals(1000f, projection.viewY(0.0), tolerance)
        assertEquals(750f, projection.viewY(90.0), tolerance)
    }

    @Test
    fun `a tap at the view centre reads as null island`() {
        val point = projection.lonLatAt(500f, 1000f, scale = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(0.0, point.lat, 1e-6)
        assertEquals(0.0, point.lon, 1e-6)
    }

    @Test
    fun `projecting then reading a tap round-trips under pan and zoom`() {
        val scale = 3.2f
        val offsetX = 41f
        val offsetY = -25f
        for ((lat, lon) in listOf(59.91 to 10.75, -33.9 to 18.4, 64.9 to -19.0, 0.0 to 179.5)) {
            val (x, y) = projection.transform(
                x = projection.mapX(lon),
                y = projection.viewY(lat),
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            )
            val point = projection.lonLatAt(x, y, scale, offsetX, offsetY)
            assertEquals(lat, point.lat, 1e-4)
            assertEquals(lon, point.lon, 1e-4)
        }
    }

    @Test
    fun `at minimum zoom the map cannot be panned`() {
        assertClamped(0f, 0f, offsetX = 400f, offsetY = -900f, scale = 1f)
    }

    @Test
    fun `panning is limited to how far the scaled map overhangs`() {
        // At 4x the map is 4000x2000: 1500px of horizontal overhang each side, and
        // exactly none vertically, since 2000 is the view height.
        assertClamped(1500f, 0f, offsetX = 2000f, offsetY = 800f, scale = 4f)
        assertClamped(-1500f, 0f, offsetX = -2000f, offsetY = -800f, scale = 4f)
        // At 6x the map is 3000px tall, so 500px of vertical overhang appears.
        assertClamped(120f, 500f, offsetX = 120f, offsetY = 800f, scale = 6f)
    }

    /** Compares components with a tolerance; a clamp to zero can produce `-0.0f`. */
    private fun assertClamped(
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
