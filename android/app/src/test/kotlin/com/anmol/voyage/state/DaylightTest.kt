package com.anmol.voyage.state

import com.anmol.voyage.data.SharedFiles
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Daylight theme's sun. iOS's `DaylightTests` pins the same cases. */
class DaylightTest {
    private val locations = SharedFiles.countryDataCache().timeZoneLocations

    private fun isNight(utc: String, zone: String) =
        Daylight.isNight(Instant.parse(utc), ZoneId.of(zone), locations)

    @Test
    fun `night falls at sunset in the time zone's city`() {
        // Oslo's sun sets at 15:12 local time on the winter solstice (14:12 UTC)
        // and at 22:44 on the summer one (20:44 UTC).
        assertEquals(false, isNight("2026-12-21T13:30:00Z", "Europe/Oslo"))
        assertEquals(true, isNight("2026-12-21T15:00:00Z", "Europe/Oslo"))
        assertEquals(false, isNight("2026-06-21T12:00:00Z", "Europe/Oslo"))
        assertEquals(true, isNight("2026-06-21T23:30:00Z", "Europe/Oslo"))
    }

    @Test
    fun `the same moment is day in one zone and night in another`() {
        assertEquals(false, isNight("2026-03-20T03:00:00Z", "Asia/Kolkata"))
        assertEquals(true, isNight("2026-03-20T03:00:00Z", "America/Los_Angeles"))
    }

    @Test
    fun `polar night and the midnight sun last all day`() {
        assertEquals(true, isNight("2026-12-21T11:00:00Z", "Arctic/Longyearbyen"))
        assertEquals(false, isNight("2026-06-21T23:00:00Z", "Arctic/Longyearbyen"))
    }

    @Test
    fun `sunrise and sunset are the minutes the theme flips`() {
        val oslo = Daylight.sunTimes(Instant.parse("2026-12-21T12:00:00Z"), ZoneId.of("Europe/Oslo"), locations)
        assertEquals(Daylight.SunTimes(LocalTime.of(9, 19), LocalTime.of(15, 12)), oslo)

        val polarNight = Daylight.sunTimes(Instant.parse("2026-12-21T12:00:00Z"), ZoneId.of("Arctic/Longyearbyen"), locations)
        assertEquals(Daylight.SunTimes(null, null), polarNight)

        val noCity = Daylight.sunTimes(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"), locations)
        assertEquals(Daylight.SunTimes(LocalTime.of(7, 0), LocalTime.of(19, 0)), noCity)
    }

    @Test
    fun `a zone's city is named by its ID`() {
        assertEquals("Oslo", Daylight.cityName(ZoneId.of("Europe/Oslo"), locations))
        assertEquals("New York", Daylight.cityName(ZoneId.of("America/New_York"), locations))
        assertEquals(null, Daylight.cityName(ZoneId.of("UTC"), locations))
    }

    @Test
    fun `a zone's older name finds the same city`() {
        assertEquals(locations["Asia/Kolkata"], locations["Asia/Calcutta"])
        assertTrue(locations.values.all { it.size == 2 })
    }

    @Test
    fun `a zone with no city is dark from 19 to 7 on its clock`() {
        assertEquals(false, isNight("2026-01-01T07:00:00Z", "UTC"))
        assertEquals(false, isNight("2026-01-01T18:59:00Z", "UTC"))
        assertEquals(true, isNight("2026-01-01T19:00:00Z", "UTC"))
        assertEquals(true, isNight("2026-01-01T06:59:00Z", "UTC"))
        assertEquals(false, isNight("2026-01-01T06:00:00Z", "GMT+02:00"))
    }
}
