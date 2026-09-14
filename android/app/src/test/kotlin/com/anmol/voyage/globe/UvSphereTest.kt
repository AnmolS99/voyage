package com.anmol.voyage.globe

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ocean sphere.
 *
 * It carries a rendering responsibility beyond being blue: because it is opaque
 * and sits inside the country fills, the depth buffer uses it to hide the far
 * hemisphere. A sphere with holes in it, or one whose radius crept past the
 * fills, would let the far side of the world show through — so radius and
 * watertightness are what these assert.
 */
class UvSphereTest {

    @Test
    fun `every vertex sits on the sphere`() {
        val sphere = UvSphere.build(radius = 1.0f, segments = 32, rings = 16)
        for (v in 0 until sphere.vertexCount) {
            val x = sphere.positions[v * 3]
            val y = sphere.positions[v * 3 + 1]
            val z = sphere.positions[v * 3 + 2]
            assertEquals(1.0f, sqrt(x * x + y * y + z * z), 1e-5f)
        }
    }

    @Test
    fun `the sphere stays inside the country fills`() {
        // Country fills are built at 1.003; an ocean at or above that would
        // z-fight with them instead of hiding the far hemisphere behind them.
        assertTrue(UvSphere.build().positions.let { positions ->
            (0 until positions.size / 3).all { v ->
                val x = positions[v * 3]
                val y = positions[v * 3 + 1]
                val z = positions[v * 3 + 2]
                sqrt(x * x + y * y + z * z) < 1.003f
            }
        })
    }

    @Test
    fun `indices are in bounds and form whole triangles`() {
        val sphere = UvSphere.build(segments = 24, rings = 12)
        assertEquals(0, sphere.indices.size % 3)
        for (index in sphere.indices) {
            assertTrue("index $index out of bounds", index in 0 until sphere.vertexCount)
        }
    }

    @Test
    fun `poles contribute one triangle per segment instead of two`() {
        val segments = 16
        val rings = 8
        val sphere = UvSphere.build(segments = segments, rings = rings)

        // Every quad is two triangles except the top and bottom rows, which
        // degenerate to one each at the pole.
        val expected = (rings * segments * 2 - segments * 2) * 3
        assertEquals(expected, sphere.indices.size)
    }

    @Test
    fun `uvs are paired with vertices`() {
        val sphere = UvSphere.build(segments = 8, rings = 4)
        assertEquals(sphere.vertexCount * 2, sphere.uvs.size)
    }

    @Test
    fun `every triangle faces outward`() {
        // Filament culls clockwise faces. Wound the other way, the sphere drops
        // its near hemisphere and the camera sees the far one from inside — a
        // flat ocean hides that, a textured one shows the wrong half of the world.
        val sphere = UvSphere.build(segments = 24, rings = 12)
        val p = sphere.positions
        for (triangle in 0 until sphere.indices.size / 3) {
            val (a, b, c) = (0..2).map { sphere.indices[triangle * 3 + it] * 3 }
            val abX = p[b] - p[a]; val abY = p[b + 1] - p[a + 1]; val abZ = p[b + 2] - p[a + 2]
            val acX = p[c] - p[a]; val acY = p[c + 1] - p[a + 1]; val acZ = p[c + 2] - p[a + 2]
            val normalX = abY * acZ - abZ * acY
            val normalY = abZ * acX - abX * acZ
            val normalZ = abX * acY - abY * acX
            val outward = normalX * (p[a] + p[b] + p[c]) +
                normalY * (p[a + 1] + p[b + 1] + p[c + 1]) +
                normalZ * (p[a + 2] + p[b + 2] + p[c + 2])
            assertTrue("triangle $triangle faces inward", outward > 0f)
        }
    }

    @Test
    fun `uvs are each vertex's place on an equirectangular image`() {
        // Checked by round trip rather than by restating the formula: the
        // latitude and longitude a UV names must put a point exactly where
        // `latLonToSphere` — which places every country — puts it.
        val sphere = UvSphere.build(segments = 36, rings = 18)
        for (v in 0 until sphere.vertexCount) {
            val lat = 90.0 - sphere.uvs[v * 2 + 1] * 180.0
            val lon = sphere.uvs[v * 2] * 360.0 - 180.0
            val expected = PolygonTriangulator.latLonToSphere(lat, lon, 1f)
            assertEquals("vertex $v x", expected.x, sphere.positions[v * 3], 1e-4f)
            assertEquals("vertex $v y", expected.y, sphere.positions[v * 3 + 1], 1e-4f)
            assertEquals("vertex $v z", expected.z, sphere.positions[v * 3 + 2], 1e-4f)
        }
    }

    @Test
    fun `a sphere needs enough segments to be a sphere`() {
        runCatching { UvSphere.build(segments = 2) }.also { assertTrue(it.isFailure) }
        runCatching { UvSphere.build(rings = 1) }.also { assertTrue(it.isFailure) }
    }
}
