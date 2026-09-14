package com.anmol.voyage.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test

/**
 * `countries.bin` loads back as exactly what parsing `world.geojson` produces.
 *
 * The app never parses the GeoJSON — it reads what the build wrote with
 * [CountriesFile] — so this round trip is what carries `GeoJsonParserTest`'s
 * fixture through to the countries users actually get.
 */
class CountriesFileTest {

    companion object {
        private lateinit var parsed: List<GeoJsonCountry>
        private lateinit var bytes: ByteArray

        @BeforeClass
        @JvmStatic
        fun writeOnce() {
            parsed = SharedFiles.parseCountries()
            bytes = ByteArrayOutputStream().also { CountriesFile.write(parsed, it) }.toByteArray()
        }
    }

    private fun load() = CountriesFile.read(bytes.inputStream())

    @Test
    fun `every country loads back, in order, with its properties`() {
        val loaded = load()

        assertEquals(parsed.map { it.name }, loaded.map { it.name })
        parsed.zip(loaded) { want, got ->
            assertEquals(want.name, want.isoCode, got.isoCode)
            assertEquals(want.name, want.continent, got.continent)
            assertEquals(want.name, want.capital, got.capital)
            assertEquals(want.name, want.isPointCountry, got.isPointCountry)
            assertEquals(want.name, want.pointCoordinate, got.pointCoordinate)
        }
    }

    @Test
    fun `coordinates load back bit for bit`() {
        // Exact rather than within a tolerance: hit-testing has to answer as the
        // parsed doubles would, which are the doubles iOS parses from the same text.
        load().zip(parsed) { got, want ->
            assertEquals(want.name, want.polygons.size, got.polygons.size)
            assertEquals(want.name, want.holes.size, got.holes.size)
            (want.polygons + want.holes).zip(got.polygons + got.holes) { a, b ->
                assertArrayEquals(want.name, a.lonLat, b.lonLat, 0.0)
            }
        }
    }

    @Test
    fun `a file from another format version is refused`() {
        val otherVersion = bytes.copyOf().also { it[4]++ }
        assertThrows(IOException::class.java) { CountriesFile.read(otherVersion.inputStream()) }
    }

    @Test
    fun `a truncated file is refused`() {
        val truncated = bytes.copyOf(bytes.size - 1)
        assertThrows(IOException::class.java) { CountriesFile.read(truncated.inputStream()) }
    }
}
