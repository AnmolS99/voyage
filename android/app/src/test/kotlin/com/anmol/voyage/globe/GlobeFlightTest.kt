package com.anmol.voyage.globe

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera flight that selecting a country starts, against the iOS `flyTo` it
 * is ported from: where it lands, how long it takes, which way round it goes.
 */
class GlobeFlightTest {

    private val frame = 1f / 120f

    /** Runs a flight to completion and returns where it left the camera. */
    private fun fly(flight: GlobeFlight): GlobeCamera {
        var camera = GlobeCamera()
        var frames = 0
        while (!flight.isFinished && frames < 1_000) {
            camera = flight.step(frame)
            frames++
        }
        return camera
    }

    @Test
    fun `a flight lands on the place, at the selection distance`() {
        val flight = GlobeFlight(GlobeCamera(), latitude = -15.8, longitude = -47.9)

        val landed = fly(flight)

        assertEquals(-15.8, landed.latitude, 1e-9)
        assertEquals(-47.9, landed.longitude, 1e-9)
        // iOS passes 2.8 when it centers on a selected country.
        assertEquals(GlobeFlight.SELECTION_DISTANCE, landed.distance, 1e-5f)
    }

    @Test
    fun `a flight takes iOS's eight tenths of a second`() {
        val flight = GlobeFlight(GlobeCamera(), latitude = 40.0, longitude = 100.0)

        var seconds = 0f
        while (!flight.isFinished && seconds < 10f) {
            flight.step(frame)
            seconds += frame
        }

        assertEquals(GlobeFlight.DURATION_SECONDS, seconds, frame * 1.5f)
    }

    @Test
    fun `a flight crossing the date line goes the short way`() {
        val flight = GlobeFlight(GlobeCamera(longitude = 170.0), latitude = 0.0, longitude = -170.0)

        // 20° east, not 340° back across the whole world. Sampled part-way,
        // because both routes end at the same place. Half way over is the date
        // line itself, which wraps to -180 — the same meridian as +180.
        val part = flight.step(GlobeFlight.DURATION_SECONDS / 2f)
        assertEquals(180.0, abs(part.longitude), 1e-6)

        assertEquals(-170.0, fly(flight).longitude, 1e-9)
    }

    @Test
    fun `a flight eases in and out rather than starting at full speed`() {
        val quarter = GlobeFlight(GlobeCamera(longitude = 0.0), latitude = 0.0, longitude = 100.0)
            .step(GlobeFlight.DURATION_SECONDS * 0.25f).longitude
        val half = GlobeFlight(GlobeCamera(longitude = 0.0), latitude = 0.0, longitude = 100.0)
            .step(GlobeFlight.DURATION_SECONDS * 0.5f).longitude
        val threeQuarters = GlobeFlight(GlobeCamera(longitude = 0.0), latitude = 0.0, longitude = 100.0)
            .step(GlobeFlight.DURATION_SECONDS * 0.75f).longitude

        assertTrue("a quarter of the way in, less than a quarter covered: $quarter", quarter < 25.0)
        assertEquals("the curve is symmetric about its middle", 50.0, half, 1e-6)
        assertTrue("three quarters in, more than three quarters covered: $threeQuarters", threeQuarters > 75.0)
    }

    @Test
    fun `a flight to the pole stops where a drag would`() {
        val landed = fly(GlobeFlight(GlobeCamera(), latitude = 89.9, longitude = 0.0))

        assertEquals(GlobeCamera.MAX_LATITUDE, landed.latitude, 1e-9)
    }

    @Test
    fun `no frame of a flight puts the camera inside the globe`() {
        // The corner iOS gets wrong: the straight line its animation draws between
        // two camera positions dips under the surface when a flight crosses the
        // equator at close zoom.
        val flight = GlobeFlight(
            GlobeCamera(latitude = -70.0, longitude = 0.0, distance = GlobeCamera.MIN_DISTANCE),
            latitude = 70.0,
            longitude = 0.0,
            distance = GlobeCamera.MIN_DISTANCE,
        )

        repeat(120) {
            val camera = flight.step(frame)
            assertTrue(
                "camera at ${camera.distance} is inside the globe",
                camera.distance >= GlobeCamera.MIN_DISTANCE - 1e-6f,
            )
        }
    }

    @Test
    fun `a flight is not finished until it is`() {
        val flight = GlobeFlight(GlobeCamera(), latitude = 10.0, longitude = 10.0)

        flight.step(GlobeFlight.DURATION_SECONDS / 2f)
        assertFalse("half way is not finished", flight.isFinished)

        flight.step(GlobeFlight.DURATION_SECONDS)
        assertTrue(flight.isFinished)
    }

    @Test
    fun `the timing curve is Core Animation's ease in ease out`() {
        assertEquals(0.0, EaseInEaseOut.of(0f), 0.0)
        assertEquals(1.0, EaseInEaseOut.of(1f), 0.0)
        assertEquals(0.5, EaseInEaseOut.of(0.5f), 1e-6)

        // cubic-bezier(0.42, 0, 0.58, 1), sampled: slow at the ends, quick in the
        // middle, and never going backwards.
        assertTrue(EaseInEaseOut.of(0.25f) < 0.25)
        assertTrue(EaseInEaseOut.of(0.75f) > 0.75)
        var previous = 0.0
        for (step in 0..100) {
            val value = EaseInEaseOut.of(step / 100f)
            assertTrue("the curve went backwards at $step", value >= previous - 1e-9)
            previous = value
        }
    }
}
