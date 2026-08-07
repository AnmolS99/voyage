package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Locks the Kotlin GeoJSON parser to `shared/fixtures/expected_countries.json`.
 *
 * The iOS suite asserts the same fixture, so the two hand-written parsers cannot
 * drift apart — and neither can silently disagree with the shared dataset.
 */
class GeoJsonParserTest {

    @Test
    fun `parses every country in the fixture, in order`() {
        assertEquals(expected.countryCount, countries.size)
        assertEquals(
            expected.countries.map { it.name },
            countries.map { it.name },
        )
    }

    @Test
    fun `country identity matches the fixture`() {
        expected.countries.zip(countries) { want, got ->
            assertEquals(want.name, want.iso, got.isoCode)
            assertEquals(want.name, want.continent, got.continent)
            assertEquals(want.name, want.isPointCountry, got.isPointCountry)
        }
    }

    @Test
    fun `capitals match the fixture`() {
        expected.countries.zip(countries) { want, got ->
            val capital = want.capital
            if (capital == null) {
                assertNull(want.name, got.capital)
            } else {
                assertNotNull(want.name, got.capital)
                assertEquals(want.name, capital.name, got.capital?.name)
                assertEquals(want.name, capital.lat, got.capital!!.lat, 0.0)
                assertEquals(want.name, capital.lon, got.capital.lon, 0.0)
            }
        }
    }

    @Test
    fun `point countries carry their coordinate and no geometry`() {
        expected.countries.zip(countries) { want, got ->
            val point = want.point
            if (point == null) {
                assertNull(want.name, got.pointCoordinate)
            } else {
                assertEquals(want.name, point.lat, got.pointCoordinate!!.lat, 0.0)
                assertEquals(want.name, point.lon, got.pointCoordinate.lon, 0.0)
                assertTrue(want.name, got.polygons.isEmpty() && got.holes.isEmpty())
            }
        }
    }

    @Test
    fun `ring and point counts match the fixture`() {
        expected.countries.zip(countries) { want, got ->
            assertEquals(want.name, want.polygonPointCounts, got.polygons.map { it.size })
            assertEquals(want.name, want.holePointCounts, got.holes.map { it.size })
        }
        assertEquals(
            expected.totalCoordinateCount,
            countries.sumOf { country ->
                (country.polygons + country.holes).sumOf { it.size }
            },
        )
    }

    @Test
    fun `coordinates match the fixture bounding boxes`() {
        expected.countries.zip(countries) { want, got ->
            val bbox = want.bbox ?: return@zip
            val rings = got.polygons + got.holes
            val lons = rings.flatMap { ring -> (0 until ring.size).map(ring::lon) }
            val lats = rings.flatMap { ring -> (0 until ring.size).map(ring::lat) }
            assertEquals("${want.name} minLon", bbox[0], lons.min(), TOLERANCE)
            assertEquals("${want.name} minLat", bbox[1], lats.min(), TOLERANCE)
            assertEquals("${want.name} maxLon", bbox[2], lons.max(), TOLERANCE)
            assertEquals("${want.name} maxLat", bbox[3], lats.max(), TOLERANCE)
        }
    }

    @Test
    fun `holes are parsed for countries that enclose others`() {
        // South Africa encloses Lesotho; without hole support the enclave would
        // be filled over. Guards the "first ring is the outer boundary" rule.
        val southAfrica = countries.single { it.name == "South Africa" }
        assertEquals(1, southAfrica.holes.size)
        assertTrue(southAfrica.polygons.isNotEmpty())
    }

    @Test
    fun `every country has an ISO code and a continent`() {
        assertEquals(emptyList<String>(), countries.filter { it.isoCode == null }.map { it.name })
        assertEquals(emptyList<String>(), countries.filter { it.continent == null }.map { it.name })
        assertEquals(countries.size, countries.mapNotNull { it.isoCode }.distinct().size)
    }

    @Test
    fun `geometry parses whichever order type and coordinates arrive in`() {
        val square = """[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]"""
        val typeFirst = feature(""""type":"Polygon","coordinates":$square""")
        val coordinatesFirst = feature(""""coordinates":$square,"type":"Polygon"""")

        listOf(typeFirst, coordinatesFirst).forEach { document ->
            val country = GeoJsonParser.parse(document.byteInputStream()).single()
            assertEquals("Testland", country.name)
            assertEquals("TL", country.isoCode)
            assertEquals(listOf(4), country.polygons.map { it.size })
            assertEquals(1.0, country.polygons.single().lon(1), 0.0)
        }
    }

    @Test
    fun `features the app cannot render are skipped`() {
        val unsupported = feature(""""type":"LineString","coordinates":[[0.0,0.0],[1.0,1.0]]""")
        val nameless = """{"features":[{"id":"TL","properties":{},"geometry":""" +
            """{"type":"Point","coordinates":[1.0,2.0]}}]}"""
        val emptyPolygon = feature(""""type":"MultiPolygon","coordinates":[]""")

        listOf(unsupported, nameless, emptyPolygon).forEach { document ->
            assertEquals(document, emptyList<String>(), GeoJsonParser.parse(document.byteInputStream()).map { it.name })
        }
    }

    private fun feature(geometry: String) =
        """{"type":"FeatureCollection","features":[{"type":"Feature","id":"TL",""" +
            """"properties":{"name":"Testland","continent":"Europe"},"geometry":{$geometry}}]}"""

    private companion object {
        const val TOLERANCE = 1e-9

        lateinit var expected: ExpectedCountries
        lateinit var countries: List<GeoJsonCountry>

        @JvmStatic
        @BeforeClass
        fun parseOnce() {
            expected = ExpectedCountries.load()
            countries = GeoJsonParser.parse(SharedFiles.open("shared/data/world.geojson"))
        }
    }
}
