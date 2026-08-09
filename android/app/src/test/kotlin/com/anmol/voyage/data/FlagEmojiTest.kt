package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors the iOS `flagEmojiFromCode`, over the ISO codes the shipped data uses. */
class FlagEmojiTest {

    private val cache = SharedFiles.countryDataCache()

    @Test
    fun `an ISO code becomes its flag`() {
        assertEquals("🇺🇸", FlagEmoji.of("US"))
        assertEquals("🇫🇷", FlagEmoji.of("FR"))
        assertEquals("🇳🇴", FlagEmoji.of("NO"))
    }

    @Test
    fun `codes are matched regardless of case`() {
        assertEquals(FlagEmoji.of("US"), FlagEmoji.of("us"))
        assertEquals(FlagEmoji.of("JP"), FlagEmoji.of("jP"))
    }

    @Test
    fun `anything that is not a pair of letters falls back to the globe`() {
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of(null))
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of(""))
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of("U"))
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of("USA"))
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of("U1"))
        assertEquals(FlagEmoji.FALLBACK, FlagEmoji.of("--"))
    }

    @Test
    fun `every country in the dataset has a real flag`() {
        val countries = cache.countries
        assertEquals(206, countries.size)
        for (country in countries) {
            val flag = FlagEmoji.of(country)
            assertEquals("${country.name} has no flag", 2, flag.codePointCount(0, flag.length))
        }
    }
}
