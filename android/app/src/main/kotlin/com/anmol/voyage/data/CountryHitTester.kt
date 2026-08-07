package com.anmol.voyage.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tap-to-country lookup for the map (Phase 4) and later the globe (Phase 7) —
 * the Kotlin port of iOS `CountryHitTester`. Both views must resolve a tap to
 * the same country, so the search strategy lives here rather than in either view.
 *
 * Per-ring bounding boxes are precomputed because an exact scan would otherwise
 * test all ~171k boundary points on every tap.
 *
 * Takes its countries as a constructor argument instead of reaching for a
 * singleton, so tests can build one over any dataset. Callers in the app go
 * through [CountryDataCache.hitTester].
 */
class CountryHitTester(countries: List<GeoJsonCountry>) {

    private class RingEntry(val countryName: String, val ring: Ring, val bounds: BoundingBox)

    private val polygonEntries: List<RingEntry>
    private val holeEntries: List<RingEntry>
    private val pointCountries: List<Pair<String, LatLon>>
    private val countriesByName: Map<String, GeoJsonCountry>

    init {
        val polygons = mutableListOf<RingEntry>()
        val holes = mutableListOf<RingEntry>()
        val points = mutableListOf<Pair<String, LatLon>>()
        val byName = HashMap<String, GeoJsonCountry>(countries.size)

        fun entriesFor(name: String, rings: List<Ring>, into: MutableList<RingEntry>) {
            for (ring in rings) {
                val bounds = BoundingBox.of(ring) ?: continue
                into.add(RingEntry(name, ring, bounds))
            }
        }

        for (country in countries) {
            byName[country.name] = country
            if (country.isPointCountry) {
                country.pointCoordinate?.let { points.add(country.name to it) }
                continue
            }
            entriesFor(country.name, country.polygons, polygons)
            entriesFor(country.name, country.holes, holes)
        }

        polygonEntries = polygons
        holeEntries = holes
        pointCountries = points
        countriesByName = byName
    }

    /**
     * The country at a lat/lon: point countries first, then exact polygon
     * containment, then an expanding-radius search so countries smaller than a
     * fingertip stay tappable.
     */
    fun findCountry(lat: Double, lon: Double): String? {
        for ((name, coord) in pointCountries) {
            val dLat = lat - coord.lat
            val dLon = lon - coord.lon
            if (sqrt(dLat * dLat + dLon * dLon) < POINT_HIT_RADIUS) return name
        }

        findCountryExact(lat, lon)?.let { return it }

        for (radius in SEARCH_RADII) {
            for (i in 0 until POINTS_PER_RADIUS) {
                val angle = i * (2.0 * PI / POINTS_PER_RADIUS)
                val name = findCountryExact(
                    lat = lat + radius * sin(angle),
                    lon = lon + radius * cos(angle),
                )
                if (name != null) return name
            }
        }

        return null
    }

    /**
     * The country whose polygon strictly contains the point, with enclave holes
     * excluded — so a tap inside Lesotho never resolves to South Africa.
     */
    fun findCountryExact(lat: Double, lon: Double): String? {
        val point = LatLon(lat = lat, lon = lon)
        for (entry in polygonEntries) {
            if (point !in entry.bounds) continue
            if (!Polygons.contains(entry.ring, lon = lon, lat = lat)) continue
            val inHole = holeEntries.any { hole ->
                hole.countryName == entry.countryName &&
                    point in hole.bounds &&
                    Polygons.contains(hole.ring, lon = lon, lat = lat)
            }
            if (!inHole) return entry.countryName
        }
        return null
    }

    /** Geographic center of a country (mean of its boundary points, antimeridian-aware). */
    fun center(of: String): LatLon? {
        val country = countriesByName[of] ?: return null

        // Point countries carry their center directly.
        if (country.isPointCountry) return country.pointCoordinate

        var count = 0
        var latSum = 0.0
        var lonSum = 0.0
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (ring in country.polygons) {
            for (i in 0 until ring.size) {
                val lon = ring.lon(i)
                latSum += ring.lat(i)
                lonSum += lon
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                count++
            }
        }
        if (count == 0) return null

        // Countries straddling the antimeridian (Fiji, Russia): a longitude range
        // wider than 180° means the mean of the raw values lands on the wrong side
        // of the planet, so shift the negative half by +360° before averaging.
        val avgLon = if (maxLon - minLon > 180) {
            var shifted = 0.0
            for (ring in country.polygons) {
                for (i in 0 until ring.size) {
                    val lon = ring.lon(i)
                    shifted += if (lon < 0) lon + 360 else lon
                }
            }
            val mean = shifted / count
            if (mean > 180) mean - 360 else mean
        } else {
            lonSum / count
        }

        return LatLon(lat = latSum / count, lon = avgLon)
    }

    private companion object {
        /** Degrees of slack around a microstate's dot, matching iOS. */
        const val POINT_HIT_RADIUS = 0.8

        val SEARCH_RADII = doubleArrayOf(0.5, 1.0, 2.0, 3.0)
        const val POINTS_PER_RADIUS = 8
    }
}
