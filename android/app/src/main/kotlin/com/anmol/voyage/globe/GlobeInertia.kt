package com.anmol.voyage.globe

import kotlin.math.abs
import kotlin.math.pow

/**
 * Spin-after-drag for the globe: an angular velocity that decays exponentially.
 *
 * A port of iOS `GlobeInertia.swift`, damping constant included, so a flick
 * coasts for the same length of time and sweeps the same arc on both platforms.
 * The one difference is units — iOS stores radians per second because its globe
 * is a node it turns with euler angles, and this stores degrees per second
 * because [GlobeCamera] is in degrees. Exponential decay is scale-free, so the
 * curve is the same either way, and [STOPPED] is iOS's cutoff converted rather
 * than a round number chosen here.
 *
 * The velocities are camera deltas, not finger deltas: [longitude] is the spin a
 * horizontal flick leaves behind and [latitude] the tilt from a vertical one.
 * Turning finger speed into these — the pan curve and the sign flip that keeps
 * the surface under the finger — belongs to the gesture handler, exactly as it
 * does for a drag.
 */
internal class GlobeInertia {

    /** Degrees per second of camera latitude, from a vertical flick. */
    var latitude: Float = 0f

    /** Degrees per second of camera longitude, from a horizontal flick. */
    var longitude: Float = 0f

    /** Whether the globe is still coasting fast enough to be worth stepping. */
    val isActive: Boolean
        get() = isCoasting(latitude) || isCoasting(longitude)

    /**
     * Advances the spin by [dt] seconds and returns the camera it moved to.
     *
     * iOS's `step(dt:)` hands rotation deltas back to a caller that applies them
     * to a node; here the caller has an immutable camera, so applying them is
     * the same act as returning them. Rotating first and decaying after matches
     * the order iOS uses, which is what keeps the first coasting frame after a
     * flick the same speed as the last dragged one.
     *
     * Latitude clamping is [GlobeCamera.rotatedBy]'s business, and velocity is
     * deliberately *not* zeroed when it binds: iOS keeps the same residual spin
     * against the pole, so a diagonal flick that runs into the clamp still
     * coasts sideways for as long as it would have.
     */
    fun step(dt: Float, camera: GlobeCamera): GlobeCamera {
        val moved = camera.rotatedBy(
            deltaLatitude = (latitude * dt).toDouble(),
            deltaLongitude = (longitude * dt).toDouble(),
        )
        latitude = decayed(latitude, dt)
        longitude = decayed(longitude, dt)
        return moved
    }

    fun reset() {
        latitude = 0f
        longitude = 0f
    }

    /**
     * The decay curve itself, apart from the globe that usually spins by it.
     *
     * The achievement medal coasts on these too — iOS's `MedalOverlayView`
     * spins its coin with a `GlobeInertia` of its own, and a medal that slowed
     * down differently from the globe would be a second feel to tune. What the
     * medal cannot reuse is [step], which moves a [GlobeCamera]; the medal turns
     * about one axis and holds a bare angle.
     */
    internal companion object {
        /** Fraction of the velocity left after one second. iOS's `damping`. */
        const val DAMPING = 0.05f

        /** iOS gives up below 0.001 rad/s; this is that speed in degrees. */
        val STOPPED = Math.toDegrees(0.001).toFloat()

        /** What is left of [velocity] after coasting for [dt] seconds. */
        fun decayed(velocity: Float, dt: Float): Float = velocity * DAMPING.pow(dt)

        /** Whether [velocity] is still fast enough to be worth stepping. */
        fun isCoasting(velocity: Float): Boolean = abs(velocity) > STOPPED
    }
}
