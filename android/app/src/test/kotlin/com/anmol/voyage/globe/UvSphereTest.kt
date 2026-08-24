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
    fun `a sphere needs enough segments to be a sphere`() {
        runCatching { UvSphere.build(segments = 2) }.also { assertTrue(it.isFailure) }
        runCatching { UvSphere.build(rings = 1) }.also { assertTrue(it.isFailure) }
    }
}
