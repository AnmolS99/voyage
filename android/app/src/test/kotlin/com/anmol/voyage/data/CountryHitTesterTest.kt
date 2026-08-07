package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Tap-to-country lookup, against the real `shared/data/world.geojson`.
 *
 * These are the cases the iOS app had to get right — enclaves, islands,
 * microstates that are a single point, and countries that straddle the
 * antimeridian — so a divergence in the Kotlin port shows up here rather than as
 * a user tapping Lesotho and selecting South Africa.
 */
class CountryHitTesterTest {

    companion object {
        private lateinit var hitTester: CountryHitTester
        private lateinit var countries: List<GeoJsonCountry>

        @BeforeClass
        @JvmStatic
        fun parseOnce() {
            val cache = SharedFiles.countryDataCache()
            countries = cache.countries
            hitTester = cache.hitTester
        }
    }

    @Test
    fun `interior points resolve to their country`() {
        assertEquals("Norway", hitTester.findCountry(lat = 59.91, lon = 10.75))
        assertEquals("Algeria", hitTester.findCountry(lat = 25.0, lon = 2.0))
        assertEquals("South Africa", hitTester.findCountry(lat = -26.2, lon = 28.0))
    }

    @Test
    fun `islands are hit like any other country`() {
        assertEquals("Iceland", hitTester.findCountry(lat = 64.9, lon = -19.0))
    }

    @Test
    fun `a tap inside an enclave selects the enclave, not the country around it`() {
        // Lesotho is a hole in South Africa's polygon; without hole handling this
        // returns South Africa, which is the bug the hole check exists to prevent.
        assertEquals("Lesotho", hitTester.findCountry(lat = -29.5, lon = 28.3))
        assertEquals("Lesotho", hitTester.findCountryExact(lat = -29.5, lon = 28.3))
    }

    @Test
    fun `point countries are hit from their coordinate`() {
        assertEquals("Singapore", hitTester.findCountry(lat = 1.29, lon = 103.85))
        assertEquals("Vatican City", hitTester.findCountry(lat = 41.90, lon = 12.45))
    }

    @Test
    fun `point countries win over the country enclosing them`() {
        // The 0.8° hit radius around a microstate's dot is wide enough that a tap on
        // Rome resolves to Vatican City. iOS behaves the same way: the dot has to
        // stay tappable at low zoom, and that costs a little of its neighbour.
        assertEquals("Vatican City", hitTester.findCountry(lat = 41.89, lon = 12.50))
    }

    @Test
    fun `taps near a coast fall back to an expanding search`() {
        // 1.5° out in the Atlantic. Exact containment finds nothing, so the
        // expanding-radius search is what keeps a near-miss usable.
        assertNull(hitTester.findCountryExact(lat = 39.0, lon = -10.5))
        assertEquals("Portugal", hitTester.findCountry(lat = 39.0, lon = -10.5))
    }

    @Test
    fun `open ocean selects nothing`() {
        assertNull(hitTester.findCountry(lat = -30.0, lon = -140.0))
    }

    @Test
    fun `country centers land inside the country`() {
        val center = hitTester.center("Algeria")
        assertEquals("Algeria", center?.let { hitTester.findCountry(it.lat, it.lon) })
    }

    @Test
    fun `centers of antimeridian countries stay on the right side of the planet`() {
        // Fiji's boundary points run from -180 to 180, so a naive mean lands near
        // 0° — in Africa. The shifted average keeps it in the Pacific.
        val fiji = hitTester.center("Fiji")!!
        assertTrue("Fiji center longitude was ${fiji.lon}", fiji.lon > 170.0)
        assertEquals(-17.41, fiji.lat, 0.01)

        val russia = hitTester.center("Russia")!!
        assertTrue("Russia center longitude was ${russia.lon}", russia.lon in 90.0..110.0)
    }

    @Test
    fun `point countries report their own coordinate as their center`() {
        val singapore = countries.first { it.name == "Singapore" }
        assertEquals(singapore.pointCoordinate, hitTester.center("Singapore"))
    }

    @Test
    fun `an unknown country has no center`() {
        assertNull(hitTester.center("Atlantis"))
    }
}
