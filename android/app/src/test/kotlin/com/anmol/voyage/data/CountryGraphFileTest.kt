package com.anmol.voyage.data

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `country_graph.bin` loads back as exactly what parsing the shared JSON
 * produces, so what [CountryGraphTest] checks is what the app gets.
 */
class CountryGraphFileTest {

    @Test
    fun `the graph loads back unchanged`() {
        val parsed = SharedFiles.parseCountryGraph()
        val bytes = ByteArrayOutputStream().also { CountryGraphFile.write(parsed, it) }.toByteArray()
        val loaded = CountryGraphFile.read(bytes.inputStream())

        assertEquals(parsed.codes, loaded.codes)
        for (a in parsed.codes) {
            assertEquals(a, parsed.neighbors(a), loaded.neighbors(a))
            for (b in parsed.codes) {
                assertEquals("$a-$b", parsed.borderDistanceKm(a, b), loaded.borderDistanceKm(a, b))
            }
        }
    }
}
