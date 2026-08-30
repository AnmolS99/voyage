package com.anmol.voyage.ui.achievements

import com.anmol.voyage.globe.GlobeInertia
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The medal coin's spin, pinned the way the globe's is.
 *
 * The point of these is that the coin and the globe share one feel: the same
 * decay curve, and finger travel measured in dp so a flick turns the coin the
 * same amount on any screen. iOS gets that by handing its coin a `GlobeInertia`;
 * here the curve is shared and the angle is the coin's own.
 */
class MedalSpinTest {

    /** 120 Hz, the rate the globe was measured at on the A55. */
    private val frame = 1f / 120f

    @Test
    fun `an untouched coin turns once every fourteen seconds`() {
        val spin = MedalSpin()

        // A tenth of a turn's worth of frames, so the wrap at 360 stays out of it.
        repeat(168) { spin.advance(frame) }

        assertEquals(MedalSpin.FULL_TURN / 10f, spin.degrees, 0.5f)
    }

    @Test
    fun `a drag turns the coin at iOS's pan speed`() {
        val spin = MedalSpin()
        spin.startDrag()

        spin.dragBy(100f)

        // iOS: 0.008 rad per point, and a dp is a point.
        assertEquals(Math.toDegrees(0.8).toFloat(), spin.degrees, 1e-3f)
    }

    @Test
    fun `a held coin does not drift`() {
        val spin = MedalSpin()
        spin.startDrag()

        repeat(120) { spin.advance(frame) }

        assertEquals(0f, spin.degrees, 0f)
    }

    @Test
    fun `a flick coasts on the globe's decay curve`() {
        val spin = MedalSpin()
        spin.startDrag()
        spin.endDrag(velocityDpPerSecond = 1000f)
        val initial = 1000f * MedalSpin.DEGREES_PER_DP

        // Half a second of coasting, integrated frame by frame.
        var travelled = 0f
        var previous = spin.degrees
        repeat(60) {
            spin.advance(frame)
            travelled += (spin.degrees - previous).mod(MedalSpin.FULL_TURN)
            previous = spin.degrees
        }

        // ∫v₀·damping^t over half a second, which is what the globe would coast.
        val expected = initial * (1f - GlobeInertia.DAMPING.pow(0.5f)) / -ln(GlobeInertia.DAMPING)
        assertEquals(expected, travelled, expected * 0.05f)
    }

    @Test
    fun `a coast hands the coin back to the idle turn`() {
        val spin = MedalSpin()
        spin.startDrag()
        spin.endDrag(velocityDpPerSecond = 2000f)

        // Long enough for the flick to die out completely.
        repeat(1200) { spin.advance(frame) }

        val before = spin.degrees
        repeat(120) { spin.advance(frame) }
        val turned = (spin.degrees - before).mod(MedalSpin.FULL_TURN)

        assertEquals(MedalSpin.IDLE_DEGREES_PER_SECOND, turned, 0.5f)
    }

    @Test
    fun `taking hold of a coasting coin stops it dead`() {
        val spin = MedalSpin()
        spin.startDrag()
        spin.endDrag(velocityDpPerSecond = 2000f)
        spin.advance(frame)

        spin.startDrag()
        val held = spin.degrees
        repeat(60) { spin.advance(frame) }

        assertEquals(held, spin.degrees, 0f)
    }

    @Test
    fun `the angle stays inside one turn`() {
        val spin = MedalSpin()

        repeat(3600) { spin.advance(frame) }

        assertTrue("angle drifted to ${spin.degrees}", spin.degrees in 0f..MedalSpin.FULL_TURN)
        assertTrue(abs(spin.degrees) < MedalSpin.FULL_TURN)
    }
}
