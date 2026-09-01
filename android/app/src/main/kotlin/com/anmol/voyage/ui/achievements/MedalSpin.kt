package com.anmol.voyage.ui.achievements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.anmol.voyage.globe.GlobeInertia

/**
 * How far the medal coin has turned about its vertical axis, and what is turning
 * it — a port of the `Coordinator` inside iOS's `MedalOverlayView`.
 *
 * The coin behaves like the globe on purpose: a horizontal drag turns it under
 * the finger, letting go leaves it coasting on the same decay curve
 * ([GlobeInertia.decayed]), and once the coast dies out it settles back into a
 * slow idle turn. Only the axis differs — the coin spins about Y alone, which is
 * why this holds one angle rather than a camera.
 *
 * Like the globe's, the angle is advanced once per rendered frame by whichever
 * of the three is in charge, so no two of them can be writing it at once.
 */
internal class MedalSpin {

    /** Rotation about the Y axis, in degrees, wrapped to one turn. */
    var degrees by mutableFloatStateOf(0f)
        private set

    /** Degrees per second left over from the last flick. */
    private var velocity = 0f

    private var dragging = false

    /** A finger has taken hold: kill any coast, as a touch does on the globe. */
    fun startDrag() {
        dragging = true
        velocity = 0f
    }

    /** Turns the coin by a finger travelling [dp] horizontally. */
    fun dragBy(dp: Float) {
        degrees = wrap(degrees + dp * DEGREES_PER_DP)
    }

    /** Hands the coin the finger's parting speed, in dp per second. */
    fun endDrag(velocityDpPerSecond: Float) {
        dragging = false
        velocity = velocityDpPerSecond * DEGREES_PER_DP
    }

    /**
     * Advances the spin by [dt] seconds.
     *
     * A held coin does not move on its own; a flicked one coasts; an untouched
     * one turns idly. Exactly one of the three writes [degrees] per frame.
     */
    fun advance(dt: Float) {
        if (dragging) return
        if (GlobeInertia.isCoasting(velocity)) {
            degrees = wrap(degrees + velocity * dt)
            velocity = GlobeInertia.decayed(velocity, dt)
        } else {
            velocity = 0f
            degrees = wrap(degrees + IDLE_DEGREES_PER_SECOND * dt)
        }
    }

    private fun wrap(degrees: Float): Float = degrees.mod(FULL_TURN)

    internal companion object {

        const val FULL_TURN = 360f

        /**
         * Degrees of spin per dp of finger travel. iOS's coin turns 0.008 rad
         * per point, and a point and a dp are the same physical size — the same
         * reasoning `GlobeCamera.degreesPerDp` rests on.
         */
        val DEGREES_PER_DP = Math.toDegrees(0.008).toFloat()

        /** iOS spins the idle coin a full turn every 14 seconds. */
        const val IDLE_DEGREES_PER_SECOND = FULL_TURN / 14f
    }
}
