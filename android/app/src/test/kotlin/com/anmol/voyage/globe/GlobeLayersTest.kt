package com.anmol.voyage.globe

import com.anmol.voyage.ui.globe.STAR_FILL_RADIUS
import com.anmol.voyage.ui.globe.STAR_OUTLINE_RADIUS
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far each layer of the globe sits above the textured ocean.
 *
 * A layer raised above the ocean is seen along a slightly different ray than
 * the texture under it, so it slides off the coastline it was drawn for — by
 * its height over the camera's height above the surface, times its distance
 * from the screen centre. At the closest zoom the camera is only 0.1 above the
 * surface, and with fills at 1.003, borders at 1.005 and a 64-segment ocean
 * that dipped 0.001 between vertices, countries visibly floated a few percent
 * off the Earth texture toward the screen edges. iOS pins the same radii in
 * `GlobeLayersTests`.
 */
class GlobeLayersTest {

    /** The lowest point of the ocean: the centroid of its deepest triangle. */
    private val oceanFloor: Float = UvSphere.build().let { sphere ->
        var lowest = Float.MAX_VALUE
        for (t in sphere.indices.indices step 3) {
            var x = 0f
            var y = 0f
            var z = 0f
            for (k in 0 until 3) {
                val v = sphere.indices[t + k]
                x += sphere.positions[v * 3]
                y += sphere.positions[v * 3 + 1]
                z += sphere.positions[v * 3 + 2]
            }
            lowest = minOf(lowest, sqrt(x * x + y * y + z * z) / 3f)
        }
        lowest
    }

    /** Screen drift at the closest zoom, as a fraction of distance from the centre. */
    private fun drift(radius: Float): Float =
        (radius - oceanFloor) / (GlobeCamera.MIN_DISTANCE - GlobeCamera.GLOBE_RADIUS)

    @Test
    fun `fills and borders stay on the texture at the closest zoom`() {
        for (radius in listOf(
            PolygonTriangulator.FILL_RADIUS,
            PolygonTriangulator.OUTLINE_RADIUS,
            PolygonTriangulator.SELECTED_OUTLINE_RADIUS,
        )) {
            assertTrue("layer at $radius drifts ${drift(radius) * 100}%", drift(radius) < 0.015f)
        }
    }

    @Test
    fun `markers stay on the texture at the closest zoom`() {
        assertTrue(drift(STAR_FILL_RADIUS) < 0.025f)
    }

    @Test
    fun `layers stack in draw order`() {
        val stack = listOf(
            oceanFloor,
            GlobeCamera.GLOBE_RADIUS,
            PolygonTriangulator.FILL_RADIUS,
            PolygonTriangulator.OUTLINE_RADIUS,
            PolygonTriangulator.SELECTED_OUTLINE_RADIUS,
            GlobeGeometryCache.DOT_RING_RADIUS,
            GlobeGeometryCache.DOT_FILL_RADIUS,
            STAR_OUTLINE_RADIUS,
            STAR_FILL_RADIUS,
        )
        assertTrue(stack.toString(), stack.zipWithNext().all { (below, above) -> below <= above })
    }

    @Test
    fun `fill triangles clear the ocean despite their chord sag`() {
        // Fill triangles are subdivided until no edge is longer than 2.5°, so a
        // triangle's centre dips at most 1 - cos(circumradius) below its corners.
        val circumradius = Math.toRadians(2.5 / sqrt(3.0))
        val sag = (1 - kotlin.math.cos(circumradius)).toFloat()
        assertTrue(PolygonTriangulator.FILL_RADIUS * (1 - sag) > GlobeCamera.GLOBE_RADIUS)
    }
}
