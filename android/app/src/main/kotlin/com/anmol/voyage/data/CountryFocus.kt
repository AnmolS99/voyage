package com.anmol.voyage.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What selecting a country frames: the smallest circle on the sphere — a cap —
 * around its main landmass, as a [center] and an angular [radiusDegrees].
 *
 * The globe turns [radiusDegrees] into a camera distance for the screen it is
 * on (`GlobeCamera.distanceToFit`), so Luxembourg fills the view and Russia
 * fits inside it instead of every country landing at one fixed zoom.
 *
 * A port of iOS `CountryFocus`, down to the iteration count: both platforms
 * frame a country identically, and `CountryHitTesterTest` and iOS
 * `CountryFocusTests` pin the same values.
 */
data class CountryFocus(val center: LatLon, val radiusDegrees: Double) {

    companion object {
        /**
         * Rings smaller than this fraction of the country's largest ring are left
         * out of the frame: the US frames the contiguous states and Alaska, not
         * Hawaii; Portugal its mainland, not the Azores.
         */
        const val MAIN_LANDMASS_FRACTION = 0.1

        /** Steps of the Bădoiu–Clarkson enclosing-ball iteration. */
        const val ITERATIONS = 100

        fun of(country: GeoJsonCountry): CountryFocus? {
            if (country.isPointCountry) {
                return country.pointCoordinate?.let { CountryFocus(it, 0.0) }
            }
            val rings = country.polygons.filter { it.size > 0 }
            if (rings.isEmpty()) return null
            val areas = rings.map(::approximateArea)
            val largest = areas.max()
            val main = rings.filterIndexed { i, _ -> areas[i] >= MAIN_LANDMASS_FRACTION * largest }
            return enclosingCap(main)
        }

        /**
         * Shoelace area in degrees², scaled by the cosine of the ring's mean
         * latitude. Only ever compared between rings of one country, so the
         * rough correction for converging meridians is all it needs.
         */
        private fun approximateArea(ring: Ring): Double {
            var twice = 0.0
            var latSum = 0.0
            for (i in 0 until ring.size) {
                val j = (i + 1) % ring.size
                twice += ring.lon(i) * ring.lat(j) - ring.lon(j) * ring.lat(i)
                latSum += ring.lat(i)
            }
            return abs(twice) / 2 * cos(Math.toRadians(latSum / ring.size))
        }

        /**
         * The smallest enclosing ball of the points as unit vectors, found with
         * Bădoiu–Clarkson (step toward the farthest point by 1/(k+1)) and pushed
         * back onto the sphere. Working in 3D makes it antimeridian-safe for
         * free, and the cap's center is not dragged toward detailed coastlines
         * the way a mean of boundary points is.
         */
        private fun enclosingCap(rings: List<Ring>): CountryFocus {
            val count = rings.sumOf { it.size }
            val xs = DoubleArray(count)
            val ys = DoubleArray(count)
            val zs = DoubleArray(count)
            var n = 0
            for (ring in rings) {
                for (i in 0 until ring.size) {
                    val lat = Math.toRadians(ring.lat(i))
                    val lon = Math.toRadians(ring.lon(i))
                    xs[n] = cos(lat) * cos(lon)
                    ys[n] = cos(lat) * sin(lon)
                    zs[n] = sin(lat)
                    n++
                }
            }

            var cx = xs.average()
            var cy = ys.average()
            var cz = zs.average()
            for (k in 1..ITERATIONS) {
                var far = 0
                var farthest = -1.0
                for (i in 0 until count) {
                    val dx = xs[i] - cx
                    val dy = ys[i] - cy
                    val dz = zs[i] - cz
                    val d = dx * dx + dy * dy + dz * dz
                    if (d > farthest) {
                        farthest = d
                        far = i
                    }
                }
                val step = 1.0 / (k + 1)
                cx += (xs[far] - cx) * step
                cy += (ys[far] - cy) * step
                cz += (zs[far] - cz) * step
            }

            val length = sqrt(cx * cx + cy * cy + cz * cz)
            cx /= length
            cy /= length
            cz /= length
            var smallestDot = 1.0
            for (i in 0 until count) {
                smallestDot = minOf(smallestDot, xs[i] * cx + ys[i] * cy + zs[i] * cz)
            }

            return CountryFocus(
                center = LatLon(
                    lat = Math.toDegrees(asin(cz.coerceIn(-1.0, 1.0))),
                    lon = Math.toDegrees(atan2(cy, cx)),
                ),
                radiusDegrees = Math.toDegrees(acos(max(-1.0, smallestDot))),
            )
        }
    }
}
