package com.anmol.voyage.state

import com.anmol.voyage.data.LatLon
import java.io.InputStream
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Whether the sun is down, for [ThemeMode.Daylight]. A port of iOS's `Daylight`,
 * pinned to the same cases by `DaylightTest` and `DaylightTests`.
 *
 * The app asks for no location, so "here" is the reference city of the device's
 * time zone (`timezone_locations.json`, shared with iOS) — right to within the
 * zone's width, and it follows the user when they travel.
 */
object Daylight {
    /** Latitude and longitude of each time zone's city, keyed by zone ID. */
    @OptIn(ExperimentalSerializationApi::class)
    fun parseLocations(input: InputStream): Map<String, List<Double>> =
        input.use { Json.decodeFromStream<Map<String, List<Double>>>(it) }

    /**
     * Where the zone's city is, or null if it has none (`UTC`, `GMT+2`). Also
     * where re-selecting Home turns the globe to — the best guess at where the
     * user is.
     */
    fun location(zone: ZoneId, locations: Map<String, List<Double>>): LatLon? =
        locations[zone.id]?.let { LatLon(lat = it[0], lon = it[1]) }

    /** A zone with no city falls back to 07:00–19:00 on its clock. */
    fun isNight(now: Instant, zone: ZoneId, locations: Map<String, List<Double>>): Boolean {
        val location = location(zone, locations) ?: return now.atZone(zone).hour !in 7..18
        return isNight(now, latitude = location.lat, longitude = location.lon)
    }

    /** When the theme turns light and dark today; null where the sun does not rise or set. */
    data class SunTimes(val sunrise: LocalTime?, val sunset: LocalTime?)

    /**
     * Today's sunrise and sunset on the zone's clock, found by asking [isNight]
     * for each minute of the day — so they are exactly when the theme flips.
     */
    fun sunTimes(now: Instant, zone: ZoneId, locations: Map<String, List<Double>>): SunTimes {
        val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone)
        var sunrise: LocalTime? = null
        var sunset: LocalTime? = null
        var wasNight = isNight(midnight.toInstant(), zone, locations)
        for (minute in 1L until 24 * 60) {
            val time = midnight.plusMinutes(minute)
            val night = isNight(time.toInstant(), zone, locations)
            if (night && !wasNight && sunset == null) sunset = time.toLocalTime()
            if (!night && wasNight && sunrise == null) sunrise = time.toLocalTime()
            wasNight = night
        }
        return SunTimes(sunrise, sunset)
    }

    /** The zone's city as its ID names it — "Oslo" for `Europe/Oslo` — or null if it has none. */
    fun cityName(zone: ZoneId, locations: Map<String, List<Double>>): String? =
        if (location(zone, locations) != null) zone.id.substringAfterLast('/').replace('_', ' ') else null

    /**
     * Whether the sun is below the horizon, from the Astronomical Almanac's
     * low-precision formulas (good to a minute or two of sunrise). Asking for
     * the sun's height rather than for sunrise and sunset times keeps polar
     * night and the midnight sun from being special cases.
     */
    fun isNight(now: Instant, latitude: Double, longitude: Double): Boolean {
        val days = now.epochSecond / 86_400.0 - 10_957.5 // since noon on 1 January 2000
        val anomaly = Math.toRadians(357.528 + 0.9856003 * days)
        val sunLongitude = Math.toRadians(
            280.460 + 0.9856474 * days + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly),
        )
        val tilt = Math.toRadians(23.439)
        val declination = asin(sin(tilt) * sin(sunLongitude))
        val rightAscension = atan2(cos(tilt) * sin(sunLongitude), cos(sunLongitude))
        val hourAngle = Math.toRadians(280.46061837 + 360.98564736629 * days + longitude) - rightAscension
        val lat = Math.toRadians(latitude)
        val height = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        return height < sin(Math.toRadians(SUNRISE_HEIGHT))
    }

    /** The sun's centre at sunrise: its radius plus refraction below the horizon. */
    private const val SUNRISE_HEIGHT = -0.833
}
