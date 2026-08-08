package com.anmol.voyage.data

/**
 * Ring geometry shared by hit-testing (Phase 4) and, later, triangulation
 * (Phase 7) — the Kotlin home of the iOS helpers that live in
 * `PolygonTriangulator.swift`.
 */
object Polygons {

    /**
     * Ray-casting point-in-polygon test in lon/lat space, a 1:1 port of the iOS
     * `PolygonTriangulator.isPointInPolygon`. Both platforms must answer
     * identically for the same tap, so the crossing rule (`>` on one side, `<`
     * on the other) is kept exactly as written there rather than "tidied".
     */
    fun contains(ring: Ring, lon: Double, lat: Double): Boolean {
        var inside = false
        val count = ring.size
        var j = count - 1
        for (i in 0 until count) {
            val xi = ring.lon(i)
            val yi = ring.lat(i)
            val xj = ring.lon(j)
            val yj = ring.lat(j)
            if ((yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

/**
 * Longitude/latitude extent of a ring, precomputed so a world-wide scan can
 * reject most rings before touching their points.
 */
class BoundingBox(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
) {
    operator fun contains(point: LatLon): Boolean =
        point.lon in minLon..maxLon && point.lat in minLat..maxLat

    companion object {
        /** Null when the ring has no points to bound. */
        fun of(ring: Ring): BoundingBox? {
            if (ring.size == 0) return null
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            for (i in 0 until ring.size) {
                val lon = ring.lon(i)
                val lat = ring.lat(i)
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
            }
            return BoundingBox(minLon, maxLon, minLat, maxLat)
        }
    }
}
