package com.anmol.voyage.globe

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ear-clipping triangulator, against shapes with known answers.
 *
 * The port is asserted the same way mapbox/earcut asserts itself: the
 * triangulation's total area must equal the polygon's area (outer ring minus
 * holes). Exhaustive coverage against real geometry comes from
 * [GlobeGeometryWorldTest], which triangulates every country in
 * `world.geojson`.
 */
class EarcutTest {

    @Test
    fun `square becomes two triangles`() {
        val square = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0)
        val triangles = Earcut.triangulate(square)

        assertEquals(6, triangles.size)
        assertEquals(100.0, triangulatedArea(square, triangles), 1e-9)
    }

    @Test
    fun `square with a hole keeps the hole open`() {
        // Outer 10×10 ring, then a 6×6 hole — hole starts at vertex 4
        val data = doubleArrayOf(
            0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0,
            2.0, 2.0, 8.0, 2.0, 8.0, 8.0, 2.0, 8.0,
        )
        val triangles = Earcut.triangulate(data, intArrayOf(4))

        assertTrue(triangles.size >= 3 * 8) // a ring between two squares needs ≥ 8 triangles
        assertEquals(100.0 - 36.0, triangulatedArea(data, triangles), 1e-9)

        // No triangle centroid may land inside the hole
        var t = 0
        while (t < triangles.size) {
            val cx = (data[triangles[t] * 2] + data[triangles[t + 1] * 2] + data[triangles[t + 2] * 2]) / 3
            val cy = (data[triangles[t] * 2 + 1] + data[triangles[t + 1] * 2 + 1] + data[triangles[t + 2] * 2 + 1]) / 3
            assertTrue("centroid ($cx, $cy) is inside the hole", cx <= 2.0 || cx >= 8.0 || cy <= 2.0 || cy >= 8.0)
            t += 3
        }
    }

    @Test
    fun `concave polygon is covered exactly`() {
        // An L-shape: 10×10 square with its top-right 5×5 quadrant missing
        val lShape = doubleArrayOf(
            0.0, 0.0, 10.0, 0.0, 10.0, 5.0, 5.0, 5.0, 5.0, 10.0, 0.0, 10.0,
        )
        val triangles = Earcut.triangulate(lShape)

        assertEquals(75.0, triangulatedArea(lShape, triangles), 1e-9)
    }

    @Test
    fun `winding direction does not matter`() {
        val clockwise = doubleArrayOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        val triangles = Earcut.triangulate(clockwise)

        assertEquals(100.0, triangulatedArea(clockwise, triangles), 1e-9)
    }

    @Test
    fun `degenerate input triangulates to nothing`() {
        assertEquals(0, Earcut.triangulate(doubleArrayOf()).size)
        assertEquals(0, Earcut.triangulate(doubleArrayOf(0.0, 0.0, 1.0, 1.0)).size)
    }

    @Test
    fun `indices always address input vertices`() {
        val data = doubleArrayOf(
            0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0,
            2.0, 2.0, 8.0, 2.0, 8.0, 8.0, 2.0, 8.0,
        )
        val triangles = Earcut.triangulate(data, intArrayOf(4))

        for (index in triangles) {
            assertTrue(index in 0 until data.size / 2)
        }
    }

    /** Sum of unsigned triangle areas, for comparing against the polygon's true area. */
    private fun triangulatedArea(data: DoubleArray, triangles: IntArray): Double {
        var area = 0.0
        var t = 0
        while (t < triangles.size) {
            val ax = data[triangles[t] * 2]
            val ay = data[triangles[t] * 2 + 1]
            val bx = data[triangles[t + 1] * 2]
            val by = data[triangles[t + 1] * 2 + 1]
            val cx = data[triangles[t + 2] * 2]
            val cy = data[triangles[t + 2] * 2 + 1]
            area += abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2
            t += 3
        }
        return area
    }
}
