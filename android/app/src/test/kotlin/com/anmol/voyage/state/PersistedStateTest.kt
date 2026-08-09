package com.anmol.voyage.state

import com.anmol.voyage.data.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved-state document: its JSON shape, and the migrations every read
 * applies.
 *
 * The shape is a contract with future builds of this app — and eventually with
 * the iOS app, if the deferred sync feature happens — so the tests below pin
 * what a document looks like, not just that a round trip works.
 */
class PersistedStateTest {

    private val fullState = PersistedState(
        visitedCountries = setOf("Norway", "Japan"),
        wishlistCountries = setOf("Peru"),
        checkedCities = mapOf("Norway" to setOf("Oslo", "Bergen")),
        checkedAttractions = mapOf("Peru" to setOf("Machu Picchu")),
        viewMode = ViewMode.Globe,
        globeStyle = GlobeStyle.Stylized,
        mapStyle = GlobeStyle.Natural,
        themeMode = ThemeMode.Dark,
    )

    @Test
    fun `every field survives a round trip`() {
        assertEquals(fullState, PersistedStateCodec.decode(PersistedStateCodec.encode(fullState)))
    }

    @Test
    fun `enums are written under their iOS names`() {
        val encoded = PersistedStateCodec.encode(fullState)
        assertTrue(encoded, """"viewMode":"globe"""" in encoded)
        assertTrue(encoded, """"globeStyle":"stylized"""" in encoded)
        assertTrue(encoded, """"mapStyle":"natural"""" in encoded)
        assertTrue(encoded, """"themeMode":"dark"""" in encoded)
    }

    @Test
    fun `defaults are written out, so a first save is a complete document`() {
        val encoded = PersistedStateCodec.encode(PersistedState())
        assertTrue(encoded, """"version":${PersistedState.CURRENT_VERSION}""" in encoded)
        assertTrue(encoded, """"themeMode":"system"""" in encoded)
    }

    @Test
    fun `a document missing fields reads back with defaults`() {
        val decoded = PersistedStateCodec.decode("""{"version":1,"visitedCountries":["Chile"]}""")
        assertEquals(PersistedState(visitedCountries = setOf("Chile")), decoded)
    }

    @Test
    fun `fields from a newer build are ignored rather than fatal`() {
        val decoded = PersistedStateCodec.decode(
            """{"version":99,"visitedCountries":["Chile"],"favouriteBiome":"tundra"}""",
        )
        assertEquals(setOf("Chile"), decoded.visitedCountries)
    }

    @Test
    fun `a read stamps the current version`() {
        assertEquals(PersistedState.CURRENT_VERSION, PersistedStateCodec.decode("{}").version)
    }

    @Test
    fun `renamed countries are migrated on read`() {
        val decoded = PersistedStateCodec.decode(
            """
            {
              "version": 1,
              "visitedCountries": ["Turkey", "Norway"],
              "wishlistCountries": ["Cape Verde"],
              "checkedCities": { "Turkey": ["Istanbul"] },
              "checkedAttractions": { "Cape Verde": ["Pico do Fogo"] }
            }
            """.trimIndent(),
        )

        assertEquals(setOf("Türkiye", "Norway"), decoded.visitedCountries)
        assertEquals(setOf("Cabo Verde"), decoded.wishlistCountries)
        assertEquals(mapOf("Türkiye" to setOf("Istanbul")), decoded.checkedCities)
        assertEquals(mapOf("Cabo Verde" to setOf("Pico do Fogo")), decoded.checkedAttractions)
    }

    @Test
    fun `both spellings of a renamed country merge into one`() {
        val decoded = PersistedStateCodec.decode(
            """{"checkedCities":{"Turkey":["Istanbul"],"Türkiye":["Ankara"]}}""",
        )
        assertEquals(mapOf("Türkiye" to setOf("Istanbul", "Ankara")), decoded.checkedCities)
    }

    @Test
    fun `the rename table matches the names world geojson actually uses`() {
        val names = SharedFiles.countryDataCache().countryNames
        for ((old, current) in PersistedState.RENAMED_COUNTRIES) {
            assertFalse("$old is still a country name in world.geojson", old in names)
            assertTrue("$current is not a country name in world.geojson", current in names)
        }
    }
}
