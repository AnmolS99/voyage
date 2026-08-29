package com.anmol.voyage.globe

import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spin-after-drag physics, pinned against the iOS model it is ported from.
 *
 * These are the numbers a flick actually produces — how long it coasts, how far
 * it travels — rather than a restatement of the implementation, so a change to
 * the damping constant on one platform and not the other shows up here.
 */
class GlobeInertiaTest {

    /** 120 Hz, the rate the globe was measured at on the A55. */
    private val frame = 1f / 120f

    @Test
    fun `velocity decays to a twentieth of itself each second`() {
        val inertia = GlobeInertia()
        inertia.longitude = 200f

        repeat(120) { inertia.step(frame, GlobeCamera()) }

        // iOS `damping`: 0.05 of the velocity survives one second.
        assertEquals(10f, inertia.longitude, 0.05f)
    }

    @Test
    fun `the first coasting frame carries on at the flick's speed`() {
        val inertia = GlobeInertia()
        inertia.longitude = 180f

        val moved = inertia.step(frame, GlobeCamera(latitude = 0.0, longitude = 0.0))

        // Rotate first, decay after — so the frame after the finger lifts is the
        // same speed as the last frame under it, with no visible step.
        assertEquals(180.0 * frame, moved.longitude, 1e-6)
    }

    @Test
    fun `a flick's total travel is its speed divided by the decay rate`() {
        val inertia = GlobeInertia()
        inertia.longitude = 300f

        var camera = GlobeCamera(latitude = 0.0, longitude = 0.0)
        var travelled = 0.0
        var frames = 0
        while (inertia.isActive && frames < 10_000) {
            val next = inertia.step(frame, camera)
            travelled += next.longitude - camera.longitude
            camera = next
            frames++
        }

        // The integral of v0·damping^t is v0 / ln(1/damping) — about a third of
        // the speed the finger left at, in degrees. Stepping frame by frame
        // overshoots that by ~1%, since each frame moves at the speed it started
        // with; the tolerance covers the discretisation, not a different curve.
        assertEquals(300.0 / ln(20.0), travelled, 2.0)
    }

    @Test
    fun `a flick stops on its own within a few seconds`() {
        val inertia = GlobeInertia()
        inertia.longitude = 300f

        var frames = 0
        while (inertia.isActive && frames < 10_000) {
            inertia.step(frame, GlobeCamera())
            frames++
        }

        val seconds = frames * frame
        assertTrue("a flick should not coast forever, ran $seconds s", seconds < 6f)
        assertTrue("a flick should coast for more than an instant, ran $seconds s", seconds > 1f)
    }

    @Test
    fun `a spin slower than iOS gives up on is not active`() {
        val inertia = GlobeInertia()
        assertFalse("a still globe is not coasting", inertia.isActive)

        // iOS stops below 0.001 rad/s.
        inertia.longitude = Math.toDegrees(0.0009).toFloat()
        assertFalse("below the cutoff should be inactive", inertia.isActive)

        inertia.longitude = Math.toDegrees(0.0011).toFloat()
        assertTrue("above the cutoff should be active", inertia.isActive)
    }

    @Test
    fun `both axes coast independently`() {
        val inertia = GlobeInertia()
        inertia.latitude = 30f
        inertia.longitude = -60f

        val moved = inertia.step(0.5f, GlobeCamera(latitude = 0.0, longitude = 0.0))

        assertEquals(15.0, moved.latitude, 1e-6)
        assertEquals(-30.0, moved.longitude, 1e-6)
    }

    @Test
    fun `coasting into the pole keeps the sideways spin`() {
        val inertia = GlobeInertia()
        inertia.latitude = 400f
        inertia.longitude = 100f

        var camera = GlobeCamera(latitude = 60.0, longitude = 0.0)
        repeat(30) { camera = inertia.step(frame, camera) }

        // The latitude clamp stops the tilt without killing the spin: a diagonal
        // flick that runs into the pole keeps sliding sideways, as on iOS.
        assertEquals(GlobeCamera.MAX_LATITUDE, camera.latitude, 1e-9)
        assertTrue("longitude should still be moving", abs(camera.longitude) > 0.0)
        assertTrue("the spin should still be live", inertia.isActive)
    }

    @Test
    fun `reset stops the globe dead`() {
        val inertia = GlobeInertia()
        inertia.latitude = 50f
        inertia.longitude = 50f

        inertia.reset()

        assertFalse(inertia.isActive)
        val camera = GlobeCamera(latitude = 10.0, longitude = 10.0)
        assertEquals(camera, inertia.step(frame, camera))
    }
}
