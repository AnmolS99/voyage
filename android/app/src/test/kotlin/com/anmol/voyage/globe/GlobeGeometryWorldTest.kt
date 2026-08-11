package com.anmol.voyage.globe

import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.GeoJsonParser
import com.anmol.voyage.data.SharedFiles
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The full triangulation pipeline over the real `shared/data/world.geojson` —
 * the geometry every launch of the Filament globe will build.
 *
 * The headline invariant matches what CLAUDE.md records for iOS: earcut
 * handles every ring in the shipped dataset, so the legacy grid fill is never
 * used. If a data regeneration ever breaks that, this fails before a device
 * renders a mangled country.
 */
class GlobeGeometryWorldTest {

    companion object {
        private lateinit var countries: List<GeoJsonCountry>

        @BeforeClass
        @JvmStatic
        fun parseOnce() {
            countries = GeoJsonParser.parse(SharedFiles.open("shared/data/world.geojson"))
        }
    }

    @Test
    fun `every polygon country triangulates without the grid fallback`() {
        val polygonCountries = countries.filter { !it.isPointCountry }
        assertEquals(181, polygonCountries.size)

        for (country in polygonCountries) {
            val mesh = PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
            assertNotNull("${country.name} produced no mesh", mesh)
            assertEquals("${country.name} needed the grid fallback", 0, mesh!!.gridFallbackRingCount)
            assertTrue("${country.name} has no triangles", mesh.indices.size >= 3)
        }
    }

    @Test
    fun `point countries produce no fill mesh`() {
        for (country in countries.filter { it.isPointCountry }) {
            assertNull(country.name, PolygonTriangulator.createCountryGeometry(country.polygons, country.holes))
        }
    }

    @Test
    fun `meshes are well-formed — indices in bounds, vertices on the sphere, UVs paired`() {
        for (country in countries.filter { !it.isPointCountry }) {
            val mesh = PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)!!

            assertEquals(country.name, 0, mesh.indices.size % 3)
            assertEquals(country.name, mesh.vertexCount * 2, mesh.uvs.size)
            for (index in mesh.indices) {
                assertTrue("${country.name} index $index out of bounds", index in 0 until mesh.vertexCount)
            }
            for (v in 0 until mesh.vertexCount) {
                val x = mesh.positions[v * 3]
                val y = mesh.positions[v * 3 + 1]
                val z = mesh.positions[v * 3 + 2]
                assertEquals("${country.name} vertex off the sphere", 1.003f, sqrt(x * x + y * y + z * z), 1e-4f)
            }
        }
    }

    @Test
    fun `enclave holes stay open — South Africa does not fill over Lesotho`() {
        val southAfrica = countries.first { it.name == "South Africa" }
        assertTrue(southAfrica.holes.isNotEmpty())
        val mesh = PolygonTriangulator.createCountryGeometry(southAfrica.polygons, southAfrica.holes)!!

        // Deep inside Lesotho, far from the enclave boundary — the same class of
        // check the Phase 4 map verified with taps. If the hole were filled,
        // some South African triangle would cover this point.
        val lat = -29.5
        val lon = 28.25
        var t = 0
        while (t < mesh.indices.size) {
            val a = vertexLatLon(mesh, mesh.indices[t])
            val b = vertexLatLon(mesh, mesh.indices[t + 1])
            val c = vertexLatLon(mesh, mesh.indices[t + 2])
            assertTrue(
                "South Africa's fill covers the inside of Lesotho",
                !triangleContains(a, b, c, lat = lat, lon = lon),
            )
            t += 3
        }
    }

    @Test
    fun `world outline sectors partition the border rings`() {
        val allRings = countries.filter { !it.isPointCountry }.flatMap { it.polygons }
        val single = PolygonTriangulator.createBorderOutlineGeometry(allRings)
        assertNotNull(single)

        val sectors = PolygonTriangulator.createSectoredOutlineGeometries(allRings)
        assertTrue(sectors.isNotEmpty() && sectors.size <= 12)
        assertEquals(single!!.vertexCount, sectors.sumOf { it.vertexCount })
        assertEquals(single.indices.size, sectors.sumOf { it.indices.size })
    }

    @Test
    fun `world outline miters are finite and within the sharp-corner cap`() {
        val allRings = countries.filter { !it.isPointCountry }.flatMap { it.polygons }
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(allRings)!!

        assertEquals(mesh.positions.size, mesh.miters.size)
        for (v in 0 until mesh.vertexCount) {
            val x = mesh.miters[v * 3]
            val y = mesh.miters[v * 3 + 1]
            val z = mesh.miters[v * 3 + 2]
            val length = sqrt(x * x + y * y + z * z)
            assertTrue(
                "miter length $length at vertex $v out of range",
                length.isFinite() && length >= 1.0f - 1e-2f && length <= 2.0f + 1e-2f,
            )
        }
    }

    /** Reverse-maps a mesh vertex to (lat, lon). */
    private fun vertexLatLon(mesh: CountryMesh, index: Int): Pair<Double, Double> {
        val point = PolygonTriangulator.sphereToLatLon(
            Vec3d(
                mesh.positions[index * 3].toDouble(),
                mesh.positions[index * 3 + 1].toDouble(),
                mesh.positions[index * 3 + 2].toDouble(),
            ),
        )
        return point.lat to point.lon
    }

    /** Point-in-triangle in lon/lat space, orientation-agnostic. */
    private fun triangleContains(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        c: Pair<Double, Double>,
        lat: Double,
        lon: Double,
    ): Boolean {
        fun side(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double =
            (p2.second - p1.second) * (lat - p1.first) - (p2.first - p1.first) * (lon - p1.second)

        val d1 = side(a, b)
        val d2 = side(b, c)
        val d3 = side(c, a)
        val hasNegative = d1 < 0 || d2 < 0 || d3 < 0
        val hasPositive = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNegative && hasPositive)
    }
}
