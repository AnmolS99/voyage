package com.anmol.voyage.globe

import com.anmol.voyage.data.Ring
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sphere-geometry pipeline on synthetic shapes: lat/lon ⇄ sphere mapping,
 * tap-ray intersection, curvature subdivision, and the miter outline.
 *
 * These mirror the behaviours documented in the iOS `PolygonTriangulator`;
 * [GlobeGeometryWorldTest] runs the same pipeline over the real dataset.
 */
class PolygonTriangulatorTest {

    // lat/lon ⇄ sphere

    @Test
    fun `latLonToSphere maps the cardinal points as iOS does`() {
        val r = 1.0f

        // Equator at the prime meridian sits on +X
        assertVec3(Vec3(1f, 0f, 0f), PolygonTriangulator.latLonToSphere(0.0, 0.0, r))
        // North pole on +Y
        assertVec3(Vec3(0f, 1f, 0f), PolygonTriangulator.latLonToSphere(90.0, 0.0, r))
        // 90°E on -Z (longitude is negated, matching the iOS handedness)
        assertVec3(Vec3(0f, 0f, -1f), PolygonTriangulator.latLonToSphere(0.0, 90.0, r))
        // 90°W on +Z
        assertVec3(Vec3(0f, 0f, 1f), PolygonTriangulator.latLonToSphere(0.0, -90.0, r))
    }

    @Test
    fun `sphereToLatLon inverts latLonToSphere`() {
        var lat = -80.0
        while (lat <= 80.0) {
            var lon = -170.0
            while (lon <= 170.0) {
                val v = PolygonTriangulator.latLonToSphere(lat, lon, 1.0f)
                val back = PolygonTriangulator.sphereToLatLon(
                    Vec3d(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()),
                )
                assertEquals("lat at ($lat, $lon)", lat, back.lat, 5e-3)
                assertEquals("lon at ($lat, $lon)", lon, back.lon, 5e-3)
                lon += 34.0
            }
            lat += 16.0
        }
    }

    @Test
    fun `radius scales the sphere point`() {
        val v = PolygonTriangulator.latLonToSphere(37.0, -122.0, 1.003f)
        val length = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        assertEquals(1.003f, length, 1e-4f)
    }

    // Tap ray → sphere surface

    @Test
    fun `ray through the globe returns the near-side entry point`() {
        val hit = PolygonTriangulator.raySphereSurfaceDirection(
            origin = Vec3d(0.0, 0.0, 3.0),
            direction = Vec3d(0.0, 0.0, -1.0),
        )
        assertNotNull(hit)
        assertEquals(0.0, hit!!.x, 1e-12)
        assertEquals(0.0, hit.y, 1e-12)
        assertEquals(1.0, hit.z, 1e-12)
    }

    @Test
    fun `ray direction length does not change the hit`() {
        val hit = PolygonTriangulator.raySphereSurfaceDirection(
            origin = Vec3d(0.0, 0.0, 3.0),
            direction = Vec3d(0.0, 0.0, -25.0),
        )
        assertNotNull(hit)
        assertEquals(1.0, hit!!.z, 1e-12)
    }

    @Test
    fun `near miss within the limb slack clamps to the horizon`() {
        // Passes 1.02 from center — off the sphere, inside the 1.05 slack
        val hit = PolygonTriangulator.raySphereSurfaceDirection(
            origin = Vec3d(1.02, 0.0, 3.0),
            direction = Vec3d(0.0, 0.0, -1.0),
        )
        assertNotNull(hit)
        assertEquals(1.0, hit!!.x, 1e-12)
        assertEquals(0.0, hit.y, 1e-12)
        assertEquals(0.0, hit.z, 1e-12)
    }

    @Test
    fun `miss beyond the limb slack returns null`() {
        assertNull(
            PolygonTriangulator.raySphereSurfaceDirection(
                origin = Vec3d(1.2, 0.0, 3.0),
                direction = Vec3d(0.0, 0.0, -1.0),
            ),
        )
    }

    @Test
    fun `sphere behind the ray returns null`() {
        assertNull(
            PolygonTriangulator.raySphereSurfaceDirection(
                origin = Vec3d(0.0, 0.0, 3.0),
                direction = Vec3d(0.0, 0.0, 1.0),
            ),
        )
    }

    @Test
    fun `zero-length direction returns null`() {
        assertNull(
            PolygonTriangulator.raySphereSurfaceDirection(
                origin = Vec3d(0.0, 0.0, 3.0),
                direction = Vec3d(0.0, 0.0, 0.0),
            ),
        )
    }

    // Country fill

    @Test
    fun `large polygons are subdivided to follow the curvature`() {
        // A 20°×20° square on the equator — every raw edge is far over the 2.5° budget
        val mesh = PolygonTriangulator.createCountryGeometry(listOf(square(0.0, 0.0, 20.0)))

        assertNotNull(mesh)
        assertEquals(0, mesh!!.gridFallbackRingCount)
        assertTrue(mesh.indices.size / 3 > 2) // far more than the raw two triangles

        var t = 0
        while (t < mesh.indices.size) {
            val a = latLonOfVertex(mesh, mesh.indices[t])
            val b = latLonOfVertex(mesh, mesh.indices[t + 1])
            val c = latLonOfVertex(mesh, mesh.indices[t + 2])
            // The budget is guaranteed in double lon/lat space; the small pad
            // absorbs the float round-trip through the sphere and back
            val longest = maxOf(angular(a, b), angular(b, c), angular(c, a))
            assertTrue("edge of $longest° exceeds the 2.5° budget", longest <= 2.5 + 0.05)
            t += 3
        }
    }

    @Test
    fun `vertices sit on the sphere at the fill radius`() {
        val mesh = PolygonTriangulator.createCountryGeometry(listOf(square(0.0, 0.0, 20.0)))!!

        for (v in 0 until mesh.vertexCount) {
            val x = mesh.positions[v * 3]
            val y = mesh.positions[v * 3 + 1]
            val z = mesh.positions[v * 3 + 2]
            assertEquals(1.003f, sqrt(x * x + y * y + z * z), 1e-4f)
        }
        assertEquals(mesh.vertexCount * 2, mesh.uvs.size)
    }

    @Test
    fun `holes are excluded from the fill`() {
        val outer = square(0.0, 0.0, 20.0)
        val hole = square(8.0, 8.0, 4.0)
        val mesh = PolygonTriangulator.createCountryGeometry(listOf(outer), listOf(hole))!!

        assertEquals(0, mesh.gridFallbackRingCount)
        var t = 0
        while (t < mesh.indices.size) {
            val a = latLonOfVertex(mesh, mesh.indices[t])
            val b = latLonOfVertex(mesh, mesh.indices[t + 1])
            val c = latLonOfVertex(mesh, mesh.indices[t + 2])
            val centroidLon = (a.second + b.second + c.second) / 3
            val centroidLat = (a.first + b.first + c.first) / 3
            // Shrunk by the float round-trip error so boundary triangles don't flake
            val inHole = centroidLon > 8.02 && centroidLon < 11.98 && centroidLat > 8.02 && centroidLat < 11.98
            assertTrue("triangle centroid ($centroidLat, $centroidLon) fills the hole", !inHole)
            t += 3
        }
    }

    @Test
    fun `empty input produces no mesh`() {
        assertNull(PolygonTriangulator.createCountryGeometry(emptyList()))
    }

    // Border outline

    @Test
    fun `outline densifies long segments and pairs every point`() {
        // Each 20° side splits into 8 segments → 32 centerline points, doubled for the strip
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(listOf(square(0.0, 0.0, 20.0)))

        assertNotNull(mesh)
        assertEquals(64, mesh!!.vertexCount)
        // Four floats per vertex against three: the miter's w carries the gradient
        assertEquals(mesh.vertexCount * 4, mesh.miters.size)
        assertEquals(32 * 6, mesh.indices.size)
    }

    @Test
    fun `outline vertices sit on the border centerline at the outline radius`() {
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(listOf(square(0.0, 0.0, 20.0)))!!

        for (v in 0 until mesh.vertexCount step 2) {
            // Paired vertices are identical — the miter is what separates them at render time
            assertEquals(mesh.positions[v * 3], mesh.positions[(v + 1) * 3], 0f)
            assertEquals(mesh.positions[v * 3 + 1], mesh.positions[(v + 1) * 3 + 1], 0f)
            assertEquals(mesh.positions[v * 3 + 2], mesh.positions[(v + 1) * 3 + 2], 0f)

            val x = mesh.positions[v * 3]
            val y = mesh.positions[v * 3 + 1]
            val z = mesh.positions[v * 3 + 2]
            assertEquals(1.005f, sqrt(x * x + y * y + z * z), 1e-4f)
        }
    }

    @Test
    fun `miter lengths stay between 1 and the 2x sharp-corner cap`() {
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(listOf(square(0.0, 0.0, 20.0)))!!

        for (v in 0 until mesh.vertexCount) {
            val x = mesh.miters[v * 4]
            val y = mesh.miters[v * 4 + 1]
            val z = mesh.miters[v * 4 + 2]
            val length = sqrt(x * x + y * y + z * z)
            assertTrue("miter length $length out of range", length >= 1.0f - 1e-3f && length <= 2.0f + 1e-3f)
        }
    }

    @Test
    fun `outline gradient runs bottom-left to top-right of the shape's box`() {
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(listOf(square(0.0, 0.0, 20.0)))!!

        // The parameter is the same (u + v) / 2 the fill's UVs produce, so the
        // globe's border gradient runs the way the flat map's brush does.
        var southWest = Float.POSITIVE_INFINITY
        var northEast = Float.NEGATIVE_INFINITY
        for (v in 0 until mesh.vertexCount) {
            val t = mesh.miters[v * 4 + 3]
            assertTrue("gradient $t outside 0…1", t >= 0f && t <= 1f)
            val lat = latLonOf(mesh.positions, v).lat
            val lon = latLonOf(mesh.positions, v).lon
            // The square spans 0…20 in both axes; its two extreme corners pin
            // the ends of the diagonal.
            if (lat < 0.5 && lon < 0.5) southWest = minOf(southWest, t)
            if (lat > 19.5 && lon > 19.5) northEast = maxOf(northEast, t)
        }
        assertEquals(0f, southWest, 1e-3f)
        assertEquals(1f, northEast, 1e-3f)
    }

    @Test
    fun `outline bounding sphere encloses every vertex`() {
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(listOf(square(30.0, 40.0, 12.0)))!!

        for (v in 0 until mesh.vertexCount) {
            val dx = mesh.positions[v * 3] - mesh.center.x
            val dy = mesh.positions[v * 3 + 1] - mesh.center.y
            val dz = mesh.positions[v * 3 + 2] - mesh.center.z
            assertTrue(
                "vertex $v outside the bounding sphere",
                sqrt(dx * dx + dy * dy + dz * dz) <= mesh.boundingRadius + 1e-4f,
            )
        }
        // A 12° patch is far smaller than the globe it sits on — the whole point
        // of the sphere is that Filament and the horizon test can skip it.
        assertTrue("bounding radius ${mesh.boundingRadius} is not local", mesh.boundingRadius < 0.3f)
    }

    @Test
    fun `selected outline sits above the shared borders`() {
        val square = listOf(square(0.0, 0.0, 10.0))
        val shared = PolygonTriangulator.createBorderOutlineGeometry(square)!!
        val selected = PolygonTriangulator.createBorderOutlineGeometry(
            polygons = square,
            radius = PolygonTriangulator.SELECTED_OUTLINE_RADIUS,
        )!!

        // iOS raises its overlay with a shader uniform; here it is baked into the
        // radius, and the 0.001 gap is the one iOS raises by.
        assertEquals(0.001f, radiusOf(selected, 0) - radiusOf(shared, 0), 1e-5f)
    }

    private fun radiusOf(mesh: OutlineMesh, vertex: Int): Float {
        val x = mesh.positions[vertex * 3]
        val y = mesh.positions[vertex * 3 + 1]
        val z = mesh.positions[vertex * 3 + 2]
        return sqrt(x * x + y * y + z * z)
    }

    private fun latLonOf(positions: FloatArray, vertex: Int) = PolygonTriangulator.sphereToLatLon(
        Vec3d(
            positions[vertex * 3].toDouble(),
            positions[vertex * 3 + 1].toDouble(),
            positions[vertex * 3 + 2].toDouble(),
        ).normalized(),
    )

    @Test
    fun `sectored outlines partition the rings`() {
        // One ring near 170°W, one at the prime meridian, one near 170°E
        val rings = listOf(square(0.0, -175.0, 4.0), square(0.0, -2.0, 4.0), square(0.0, 171.0, 4.0))
        val sectors = PolygonTriangulator.createSectoredOutlineGeometries(rings)

        assertEquals(3, sectors.size)
        val single = PolygonTriangulator.createBorderOutlineGeometry(rings)!!
        assertEquals(single.vertexCount, sectors.sumOf { it.vertexCount })
    }

    @Test
    fun `sectors split by latitude as well as longitude`() {
        // Two rings sharing a longitude band, one far north and one far south.
        // Bucketing by longitude alone would merge them into a slab spanning the
        // globe, which the horizon test can never cull.
        val rings = listOf(square(60.0, 0.0, 4.0), square(-70.0, 0.0, 4.0))
        val sectors = PolygonTriangulator.createSectoredOutlineGeometries(rings)

        assertEquals(2, sectors.size)
        for (sector in sectors) {
            assertTrue("sector radius ${sector.boundingRadius} spans the globe", sector.boundingRadius < 0.5f)
        }
    }

    // Helpers

    /** A closed square ring from (lat, lon) spanning `size` degrees, counter-clockwise. */
    private fun square(lat: Double, lon: Double, size: Double): Ring = Ring(
        doubleArrayOf(
            lon, lat,
            lon + size, lat,
            lon + size, lat + size,
            lon, lat + size,
            lon, lat,
        ),
    )

    private fun assertVec3(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-6f)
        assertEquals(expected.y, actual.y, 1e-6f)
        assertEquals(expected.z, actual.z, 1e-6f)
    }

    /** Reverse-maps a mesh vertex to (lat, lon). */
    private fun latLonOfVertex(mesh: CountryMesh, index: Int): Pair<Double, Double> {
        val point = PolygonTriangulator.sphereToLatLon(
            Vec3d(
                mesh.positions[index * 3].toDouble(),
                mesh.positions[index * 3 + 1].toDouble(),
                mesh.positions[index * 3 + 2].toDouble(),
            ),
        )
        return point.lat to point.lon
    }

    /** True angular length in degrees between two (lat, lon) pairs, as the subdivision measures it. */
    private fun angular(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val midLat = (a.first + b.first) / 2 * Math.PI / 180
        val dLon = (b.second - a.second) * cos(midLat)
        val dLat = b.first - a.first
        return sqrt(dLon * dLon + dLat * dLat)
    }
}
