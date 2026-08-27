package com.anmol.voyage.globe

import com.anmol.voyage.ui.map.CapitalMarker
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The globe's markers: dots for microstates, a star for the selected capital.
 *
 * These are degenerate meshes — every vertex sits at the marker's center until
 * the material pushes it out along its stored offset — so nothing about them can
 * be checked by looking at positions. What matters is the offsets: that they lie
 * in the sphere's tangent plane, that they are the right size, and above all
 * that they are oriented correctly. A mirrored tangent frame still draws a
 * perfectly good *upside-down* star, which is why orientation is pinned here
 * rather than left to the eye.
 */
class MarkerMeshTest {

    @Test
    fun `every disc vertex starts at the marker's center on the sphere`() {
        val mesh = MarkerMeshes.disc(lat = 30.0, lon = -20.0, sphereRadius = 1.006f)

        for (v in 0 until mesh.vertexCount) {
            assertEquals(mesh.center.x, mesh.positions[v * 3], 0f)
            assertEquals(mesh.center.y, mesh.positions[v * 3 + 1], 0f)
            assertEquals(mesh.center.z, mesh.positions[v * 3 + 2], 0f)
        }
        assertEquals(1.006f, length(mesh.center.x, mesh.center.y, mesh.center.z), 1e-5f)
    }

    @Test
    fun `disc offsets lie flat on the sphere at the requested radius`() {
        val ratio = 1.25f
        val mesh = MarkerMeshes.disc(lat = -15.0, lon = 140.0, sphereRadius = 1.006f, radiusRatio = ratio)
        val normal = normalized(mesh.center.x, mesh.center.y, mesh.center.z)

        // Vertex 0 is the fan's hub and has no offset; the rim is the rest.
        for (v in 1 until mesh.vertexCount) {
            val x = mesh.offsets[v * 4]
            val y = mesh.offsets[v * 4 + 1]
            val z = mesh.offsets[v * 4 + 2]
            assertEquals("rim vertex $v is not the marker's radius", ratio, length(x, y, z), 1e-4f)
            val outOfPlane = x * normal[0] + y * normal[1] + z * normal[2]
            assertEquals("rim vertex $v leaves the tangent plane", 0f, outOfPlane, 1e-4f)
        }
    }

    @Test
    fun `the tangent frame points east and north, not their mirrors`() {
        // At (0, 0) the globe's normal is +X, north is +Y, and east — increasing
        // longitude — is -Z under `latLonToSphere`'s handedness. A frame built
        // from the wrong cross-product order passes every other test here and
        // renders the star upside down.
        val corners = floatArrayOf(1f, 0f, 0f, 1f)
        val mesh = MarkerMeshes.star(lat = 0.0, lon = 0.0, sphereRadius = 1f, corners = corners)

        // Corner (1, 0) is one unit east.
        assertEquals(0f, mesh.offsets[4], 1e-5f)
        assertEquals(0f, mesh.offsets[5], 1e-5f)
        assertEquals(-1f, mesh.offsets[6], 1e-5f)

        // Corner (0, 1) is one unit north, with +Y up.
        assertEquals(0f, mesh.offsets[8], 1e-5f)
        assertEquals(1f, mesh.offsets[9], 1e-5f)
        assertEquals(0f, mesh.offsets[10], 1e-5f)
    }

    @Test
    fun `east really is the direction longitude increases`() {
        // Independent of the frame's construction: step a little east on the
        // sphere and check the offset points the same way.
        val lat = 25.0
        val lon = 70.0
        val here = PolygonTriangulator.latLonToSphere(lat, lon, 1f)
        val slightlyEast = PolygonTriangulator.latLonToSphere(lat, lon + 0.5, 1f)
        val mesh = MarkerMeshes.star(lat, lon, 1f, corners = floatArrayOf(1f, 0f))

        val stepX = slightlyEast.x - here.x
        val stepY = slightlyEast.y - here.y
        val stepZ = slightlyEast.z - here.z
        val step = normalized(stepX, stepY, stepZ)
        val offset = normalized(mesh.offsets[4], mesh.offsets[5], mesh.offsets[6])
        val alignment = step[0] * offset[0] + step[1] * offset[1] + step[2] * offset[2]
        assertEquals("the east offset points west", 1f, alignment, 1e-3f)
    }

    @Test
    fun `north really is the direction latitude increases`() {
        val lat = -40.0
        val lon = -130.0
        val here = PolygonTriangulator.latLonToSphere(lat, lon, 1f)
        val slightlyNorth = PolygonTriangulator.latLonToSphere(lat + 0.5, lon, 1f)
        val mesh = MarkerMeshes.star(lat, lon, 1f, corners = floatArrayOf(0f, 1f))

        val step = normalized(
            slightlyNorth.x - here.x,
            slightlyNorth.y - here.y,
            slightlyNorth.z - here.z,
        )
        val offset = normalized(mesh.offsets[4], mesh.offsets[5], mesh.offsets[6])
        val alignment = step[0] * offset[0] + step[1] * offset[1] + step[2] * offset[2]
        assertEquals("the north offset points south", 1f, alignment, 1e-3f)
    }

    @Test
    fun `the gradient runs bottom-left to top-right across the marker`() {
        // The same diagonal `CountryStyles` documents for the flat map, so a
        // microstate on both lists reads the same way on both renderers.
        val corners = floatArrayOf(-1f, -1f, 1f, 1f)
        val mesh = MarkerMeshes.star(lat = 10.0, lon = 10.0, sphereRadius = 1f, corners = corners)

        assertEquals(0f, mesh.offsets[7], 1e-5f)
        assertEquals(1f, mesh.offsets[11], 1e-5f)
        // The hub sits halfway along it.
        assertEquals(0.5f, mesh.offsets[3], 1e-5f)

        for (v in 0 until mesh.vertexCount) {
            val t = mesh.offsets[v * 4 + 3]
            assertTrue("gradient $t outside 0…1", t >= 0f && t <= 1f)
        }
    }

    @Test
    fun `a disc is a closed fan around its hub`() {
        val mesh = MarkerMeshes.disc(lat = 0.0, lon = 0.0, sphereRadius = 1f)
        val rim = mesh.vertexCount - 1

        assertEquals(rim * 3, mesh.indices.size)
        for (triangle in 0 until rim) {
            assertEquals("triangle $triangle does not start at the hub", 0, mesh.indices[triangle * 3])
        }
        // The last triangle wraps back to the first rim vertex, closing the disc.
        assertEquals(1, mesh.indices[mesh.indices.size - 1])
    }

    @Test
    fun `the star is built from the shape the map draws`() {
        val corners = CapitalMarker.starVertices(outerRadius = 1f, yUp = true)
        val mesh = MarkerMeshes.star(lat = 48.0, lon = 2.0, sphereRadius = 1.0066f, corners = corners)

        // Ten corners plus the hub, and every corner within the star's radius.
        assertEquals(11, mesh.vertexCount)
        for (v in 1 until mesh.vertexCount) {
            val magnitude = length(mesh.offsets[v * 4], mesh.offsets[v * 4 + 1], mesh.offsets[v * 4 + 2])
            assertTrue("corner $v is outside the star's radius", magnitude <= 1f + 1e-4f)
        }
    }

    @Test
    fun `the star's points face north`() {
        // CapitalMarker starts at straight up; with yUp that must reach north,
        // not south. This is the assertion that would have caught the star being
        // drawn upside down.
        val corners = CapitalMarker.starVertices(outerRadius = 1f, yUp = true)
        val mesh = MarkerMeshes.star(lat = 0.0, lon = 0.0, sphereRadius = 1f, corners = corners)

        // Offset slot 1 is the first corner — the one pointing straight up.
        assertEquals(1f, mesh.offsets[5], 1e-4f)
        assertTrue("the topmost point is not the star's outer radius", abs(mesh.offsets[4]) < 1e-4f)
    }

    private fun length(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    private fun normalized(x: Float, y: Float, z: Float): FloatArray {
        val magnitude = length(x, y, z)
        return floatArrayOf(x / magnitude, y / magnitude, z / magnitude)
    }
}
