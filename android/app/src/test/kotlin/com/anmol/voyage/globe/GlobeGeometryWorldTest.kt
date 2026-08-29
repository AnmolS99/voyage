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
        assertTrue(sectors.isNotEmpty() && sectors.size <= 12 * 4)
        assertEquals(single!!.vertexCount, sectors.sumOf { it.vertexCount })
        assertEquals(single.indices.size, sectors.sumOf { it.indices.size })
    }

    @Test
    fun `world outline miters are finite and within the sharp-corner cap`() {
        val allRings = countries.filter { !it.isPointCountry }.flatMap { it.polygons }
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(allRings)!!

        assertEquals(mesh.vertexCount * 4, mesh.miters.size)
        for (v in 0 until mesh.vertexCount) {
            val x = mesh.miters[v * 4]
            val y = mesh.miters[v * 4 + 1]
            val z = mesh.miters[v * 4 + 2]
            val length = sqrt(x * x + y * y + z * z)
            assertTrue(
                "miter length $length at vertex $v out of range",
                length.isFinite() && length >= 1.0f - 1e-2f && length <= 2.0f + 1e-2f,
            )
        }
    }

    @Test
    fun `horizon culling never hides a border the camera can see`() {
        val sectors = PolygonTriangulator.createSectoredOutlineGeometries(allBorderRings())

        // The safety property of GlobeRenderer.cullFarSideOutlineSectors: a
        // hidden sector must contain no vertex on the visible cap, or borders
        // vanish mid-drag. Swept over the whole globe, not one viewpoint.
        for (latitude in -80..80 step 20) {
            for (longitude in -180 until 180 step 30) {
                val camera = GlobeCamera(latitude.toDouble(), longitude.toDouble())
                val eye = camera.position.normalized()
                for (sector in sectors) {
                    if (!camera.isBeyondHorizon(sector.center, sector.boundingRadius)) continue
                    for (v in 0 until sector.vertexCount) {
                        val dot = sector.positions[v * 3] * eye.x +
                            sector.positions[v * 3 + 1] * eye.y +
                            sector.positions[v * 3 + 2] * eye.z
                        assertTrue(
                            "a vertex visible from ($latitude, $longitude) was culled",
                            dot < 1.0 / camera.distance,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `horizon culling drops a real share of the world's border vertices`() {
        val sectors = PolygonTriangulator.createSectoredOutlineGeometries(allBorderRings())
        val total = sectors.sumOf { it.vertexCount }

        // The measurement that justifies the lat × lon grid over plain
        // longitude-only bucketing, which sheds 0.0% here — see
        // `createSectoredOutlineGeometries`. Guards the grid against being
        // quietly simplified back into slabs that never cull.
        var samples = 0
        var culledFraction = 0.0
        for (latitude in -60..60 step 30) {
            for (longitude in -180 until 180 step 30) {
                val camera = GlobeCamera(latitude.toDouble(), longitude.toDouble())
                val culled = sectors
                    .filter { camera.isBeyondHorizon(it.center, it.boundingRadius) }
                    .sumOf { it.vertexCount }
                culledFraction += culled.toDouble() / total
                samples++
            }
        }
        val average = culledFraction / samples
        assertTrue("horizon culling only sheds ${(average * 100).toInt()}% of border vertices", average > 0.30)

        val longitudeOnly = PolygonTriangulator.createSectoredOutlineGeometries(
            polygons = allBorderRings(),
            latitudeBands = 1,
        )
        val camera = GlobeCamera(latitude = 20.0, longitude = 0.0)
        assertEquals(
            "a pole-to-pole slab should never fall entirely behind the horizon",
            0,
            longitudeOnly.count { camera.isBeyondHorizon(it.center, it.boundingRadius) },
        )
    }

    @Test
    fun `every country is drawn by exactly one path`() {
        // Both renderers split the world the same way: a country either gets a
        // shape (globe fill mesh / map path — `!isPointCountry`) or a dot (globe
        // overlay / map marker — `pointCoordinate != null`). The two predicates
        // are not each other's negation, and nothing makes them agree: the parser
        // sets `isPointCountry` from a `renderAs: "point"` property but only sets
        // `pointCoordinate` for a `Point` geometry. Flag a *polygon* feature
        // `renderAs: "point"` and it falls through both filters — no shape, no
        // dot, invisible on the globe and on the map, with nothing failing.
        //
        // The shipped dataset has no such feature. This is here so a regenerated
        // one that does fails loudly instead.
        val invisible = countries.filter { it.isPointCountry && it.pointCoordinate == null }
        assertTrue(
            "these countries would render as neither shape nor dot: ${invisible.map { it.name }}",
            invisible.isEmpty(),
        )

        val dotted = countries.count { it.pointCoordinate != null }
        assertEquals(25, dotted)
        assertEquals(countries.size, dotted + countries.count { !it.isPointCountry })
    }

    private fun allBorderRings() = countries.filter { !it.isPointCountry }.flatMap { it.polygons }

    @Test
    fun `world outline vertex count stays within the budget the culling assumes`() {
        val allRings = countries.filter { !it.isPointCountry }.flatMap { it.polygons }
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(allRings)!!

        // Recorded so a geometry-detail change shows up as a number rather than a
        // frame-rate report: the outline mesh is the scene's dominant cost, which
        // is why the sectors exist at all.
        assertTrue(
            "outline vertex count ${mesh.vertexCount} outside the expected range",
            mesh.vertexCount in 300_000..500_000,
        )
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
