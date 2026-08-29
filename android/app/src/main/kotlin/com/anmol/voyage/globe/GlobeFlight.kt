package com.anmol.voyage.globe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A one-shot camera flight: 0.8 seconds from wherever the globe is onto a place,
 * eased in and out.
 *
 * A port of iOS `GlobeView.Coordinator.flyTo`, which does the same thing as an
 * implicitly animated `SCNTransaction`. The duration, the timing curve and the
 * distance it settles at are that method's; what is written out longhand here is
 * the interpolation Core Animation was doing for it — see [step].
 *
 * Stepped by the render loop like everything else that moves the globe, so a
 * flight, a coasting flick and the idle spin can never be writing the camera at
 * the same time.
 */
internal class GlobeFlight(
    private val from: GlobeCamera,
    latitude: Double,
    longitude: Double,
    distance: Float = SELECTION_DISTANCE,
) {

    /** Where the flight ends, already clamped to the camera's limits. */
    private val destination = GlobeCamera.at(latitude, longitude, distance)

    /**
     * Degrees of longitude to cover, the short way round.
     *
     * iOS normalises its target rotation to within ±π of the current one before
     * animating; this is that, in degrees. Without it a flight from 170°E to
     * 170°W would take the 340° scenic route.
     */
    private val longitudeTravel = shortWayRound(from.longitude, destination.longitude)

    private var elapsed = 0f

    val isFinished: Boolean
        get() = elapsed >= DURATION_SECONDS

    /**
     * Advances the flight by [dt] seconds and returns where the camera now is.
     *
     * Longitude turns at an even angular rate, as iOS's animated `eulerAngles`
     * does. Latitude and distance move together instead of separately, because
     * what iOS animates is the camera's *position*, and Core Animation
     * interpolates a position linearly: the camera cuts a chord across the
     * (latitude, distance) plane rather than swinging around an arc. Lerping the
     * angle and the distance apart would look almost the same — a couple of
     * degrees and a tenth of a unit off at the midpoint of a typical flight — but
     * this is what iOS actually does.
     *
     * The one place the chord is not followed is the corner where it would pass
     * *inside* the globe: crossing the equator at close zoom, the straight line
     * between two camera positions can dip under the surface, and it does on iOS
     * too. [GlobeCamera.at] clamps that back out. No ordinary flight comes near
     * the clamp.
     */
    fun step(dt: Float): GlobeCamera {
        elapsed = (elapsed + dt).coerceAtMost(DURATION_SECONDS)
        val fraction = EaseInEaseOut.of(elapsed / DURATION_SECONDS)

        val fromRadians = from.latitude * PI / 180.0
        val toRadians = destination.latitude * PI / 180.0
        val height = lerp(from.distance * sin(fromRadians), destination.distance * sin(toRadians), fraction)
        val depth = lerp(from.distance * cos(fromRadians), destination.distance * cos(toRadians), fraction)

        return GlobeCamera.at(
            latitude = atan2(height, depth) * 180.0 / PI,
            longitude = from.longitude + longitudeTravel * fraction,
            distance = hypot(height, depth).toFloat(),
        )
    }

    private fun lerp(from: Double, to: Double, fraction: Double) = from + (to - from) * fraction

    companion object {
        /** iOS `SCNTransaction.animationDuration` in `flyTo`. */
        const val DURATION_SECONDS = 0.8f

        /** Where selecting a country settles the camera. iOS passes this too. */
        const val SELECTION_DISTANCE = 2.8f

        private fun shortWayRound(from: Double, to: Double): Double {
            val direct = to - from
            return when {
                direct > 180.0 -> direct - 360.0
                direct < -180.0 -> direct + 360.0
                else -> direct
            }
        }
    }
}

/**
 * Core Animation's `kCAMediaTimingFunctionEaseInEaseOut`, which is the cubic
 * Bézier through (0.42, 0) and (0.58, 1).
 *
 * Written out rather than taken from `androidx.compose.animation.core`, so the
 * globe package stays free of Compose and testable on the JVM — the same reason
 * its geometry emits plain float buffers instead of Filament objects.
 */
internal object EaseInEaseOut {

    private const val X1 = 0.42
    private const val Y1 = 0.0
    private const val X2 = 0.58
    private const val Y2 = 1.0

    // Bézier from (0,0) to (1,1), as polynomials in t.
    private const val CX = 3.0 * X1
    private const val BX = 3.0 * (X2 - X1) - CX
    private const val AX = 1.0 - CX - BX
    private const val CY = 3.0 * Y1
    private const val BY = 3.0 * (Y2 - Y1) - CY
    private const val AY = 1.0 - CY - BY

    private fun x(t: Double) = ((AX * t + BX) * t + CX) * t

    private fun y(t: Double) = ((AY * t + BY) * t + CY) * t

    private fun dxdt(t: Double) = (3.0 * AX * t + 2.0 * BX) * t + CX

    /**
     * The eased fraction at [fraction] of the way through.
     *
     * The curve is parametric, so the parameter that reaches a given time has to
     * be solved for. Newton converges in a handful of steps everywhere the slope
     * is healthy; bisection picks up the ends, where it is not.
     */
    fun of(fraction: Float): Double {
        val target = fraction.toDouble().coerceIn(0.0, 1.0)
        if (target <= 0.0 || target >= 1.0) return target

        var t = target
        repeat(8) {
            val error = x(t) - target
            if (abs(error) < EPSILON) return y(t)
            val slope = dxdt(t)
            if (abs(slope) < EPSILON) return@repeat
            t -= error / slope
        }

        var low = 0.0
        var high = 1.0
        t = target
        while (high - low > EPSILON) {
            val current = x(t)
            if (abs(current - target) < EPSILON) break
            if (current < target) low = t else high = t
            t = (low + high) / 2.0
        }
        return y(t)
    }

    private const val EPSILON = 1e-7
}
