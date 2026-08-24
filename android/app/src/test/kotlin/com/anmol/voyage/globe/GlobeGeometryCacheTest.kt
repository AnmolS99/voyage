package com.anmol.voyage.globe

import com.anmol.voyage.data.GeoJsonParser
import com.anmol.voyage.data.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The globe's geometry is built once per process.
 *
 * This is a performance contract, not a correctness one, which is exactly why
 * it needs a test: nothing looks wrong when it breaks. Holding the geometry in
 * the composable that drew it meant every trip to another tab — or to the flat
 * map — threw away ~300 ms of triangulation and paid it again on the way back.
 * The globe still rendered correctly; it just took half a second to come back.
 */
class GlobeGeometryCacheTest {

    companion object {
        private lateinit var countries: List<com.anmol.voyage.data.GeoJsonCountry>

        @BeforeClass
        @JvmStatic
        fun parseOnce() {
            countries = GeoJsonParser.parse(SharedFiles.open("shared/data/world.geojson"))
        }
    }

    @Test
    fun `the world is triangulated once and reused`() {
        val first = GlobeGeometryCache.get(countries)
        val second = GlobeGeometryCache.get(countries)

        // Identity, not equality: a second build would be correct and slow.
        assertSame("the globe was triangulated twice", first, second)
        assertSame(first.ocean, second.ocean)
        assertSame(first.countries, second.countries)
    }

    @Test
    fun `the cache reports itself ready once built`() {
        GlobeGeometryCache.get(countries)
        assertTrue(GlobeGeometryCache.isReady)
    }

    @Test
    fun `every polygon country gets a mesh`() {
        val geometry = GlobeGeometryCache.get(countries)

        // Point-feature microstates have no fill mesh; everything else does.
        assertEquals(countries.count { !it.isPointCountry }, geometry.countries.size)
        assertTrue(geometry.countries.all { it.mesh.indices.isNotEmpty() })
    }

    @Test
    fun `meshes are addressable by the names the renderer colors by`() {
        val geometry = GlobeGeometryCache.get(countries)
        val names = geometry.countries.map { it.name }.toSet()

        // The renderer looks its material instances up by country name, and
        // VoyageState stores visited/wishlist by the same name.
        assertEquals(geometry.countries.size, names.size)
        assertTrue("Brazil" in names)
        assertTrue("South Africa" in names)
    }
}
