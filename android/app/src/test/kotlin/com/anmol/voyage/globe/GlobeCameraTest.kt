package com.anmol.voyage.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orbit camera and its inverse.
 *
 * The pairing that matters is [GlobeCamera.position] against
 * [GlobeCamera.latLonAt]: the renderer places the camera with the first and taps
 * are resolved with the second, so a disagreement between them shows up as taps
 * landing on the wrong country — exactly the failure `MapProjectionTest` guards
 * against on the flat map.
 */
class GlobeCameraTest {

    @Test
    fun `camera sits above the point it looks at`() {
        // Null island, on the +x axis, matching latLonToSphere's mapping.
        val equator = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = 4f).position
        assertEquals(4.0, equator.x, 1e-9)
        assertEquals(0.0, equator.y, 1e-9)
        assertEquals(0.0, equator.z, 1e-9)

        val northPole = GlobeCamera(latitude = 90.0, longitude = 0.0, distance = 4f).position
        assertEquals(4.0, northPole.y, 1e-9)
    }

    @Test
    fun `camera direction agrees with the surface mapping`() {
        // The camera at (lat, lon) must look down at the sphere point for the
        // same (lat, lon), or the globe would be off-center by construction.
        for (latitude in listOf(-60.0, -20.0, 0.0, 35.0, 75.0)) {
            for (longitude in listOf(-170.0, -75.0, 0.0, 20.0, 140.0)) {
                val camera = GlobeCamera(latitude, longitude, distance = 3f)
                val surface = PolygonTriangulator.latLonToSphere(latitude, longitude, 3f)
                assertEquals("lat=$latitude lon=$longitude", surface.x.toDouble(), camera.position.x, 1e-4)
                assertEquals("lat=$latitude lon=$longitude", surface.y.toDouble(), camera.position.y, 1e-4)
                assertEquals("lat=$latitude lon=$longitude", surface.z.toDouble(), camera.position.z, 1e-4)
            }
        }
    }

    @Test
    fun `a tap at the center of the screen hits the point the camera looks at`() {
        val camera = GlobeCamera(latitude = 20.0, longitude = -30.0, distance = 4f)
        val hit = camera.latLonAt(x = 540f, y = 1200f, viewportWidth = 1080f, viewportHeight = 2400f)

        assertNotNull(hit)
        assertEquals(20.0, hit!!.lat, 1e-6)
        assertEquals(-30.0, hit.lon, 1e-6)
    }

    @Test
    fun `taps away from the center stay on the globe and move the right way`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = 4f)

        // Higher on the screen is further north.
        val up = camera.latLonAt(540f, 900f, 1080f, 2400f)
        assertNotNull(up)
        assertTrue("tapping above center should move north, got ${up!!.lat}", up.lat > 0.5)

        // Right of center is further east — the globe's east is +longitude.
        val right = camera.latLonAt(700f, 1200f, 1080f, 2400f)
        assertNotNull(right)
        assertTrue("tapping right of center should move east, got ${right!!.lon}", right.lon > 0.5)
    }

    @Test
    fun `a tap in the corner misses the globe`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = 10f)
        assertNull(camera.latLonAt(2f, 2f, 1080f, 2400f))
    }

    @Test
    fun `a tap on a zero-sized viewport is not a hit`() {
        assertNull(GlobeCamera().latLonAt(0f, 0f, 0f, 0f))
    }

    @Test
    fun `rotation clamps latitude short of the poles`() {
        val camera = GlobeCamera(latitude = 80.0, longitude = 0.0)
        assertEquals(89.0, camera.rotatedBy(deltaLatitude = 50.0, deltaLongitude = 0.0).latitude, 1e-9)
        assertEquals(-89.0, camera.rotatedBy(deltaLatitude = -200.0, deltaLongitude = 0.0).latitude, 1e-9)
    }

    @Test
    fun `rotation wraps longitude instead of running off the end`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 170.0)
        assertEquals(-170.0, camera.rotatedBy(0.0, 20.0).longitude, 1e-9)
        assertEquals(170.0, GlobeCamera(longitude = -170.0).rotatedBy(0.0, -20.0).longitude, 1e-9)
    }

    @Test
    fun `zoom clamps to the same distances as iOS`() {
        val camera = GlobeCamera(distance = 4f)
        assertEquals(GlobeCamera.MIN_DISTANCE, camera.zoomedBy(100f).distance, 1e-6f)
        assertEquals(GlobeCamera.MAX_DISTANCE, camera.zoomedBy(0.001f).distance, 1e-6f)
        // A pinch of 1 is no pinch at all.
        assertEquals(4f, camera.zoomedBy(1f).distance, 1e-6f)
    }

    @Test
    fun `dragging covers less ground when zoomed in`() {
        val far = GlobeCamera(distance = 8f).degreesPerPixel(2400f)
        val near = GlobeCamera(distance = 2f).degreesPerPixel(2400f)
        assertTrue("zoomed in should rotate less per pixel", near < far)
        assertEquals(0.0, GlobeCamera().degreesPerPixel(0f), 0.0)
    }

    // Forward projection — where a marker lands on screen

    @Test
    fun `projecting a tap's own point puts it back under the finger`() {
        // The pairing that matters: screenPositionOf must undo latLonAt exactly,
        // or a capital star drifts off its capital as the globe turns.
        val camera = GlobeCamera(latitude = 12.0, longitude = -40.0, distance = 3.0f)
        val width = 1080f
        val height = 2400f

        for (x in listOf(400f, 540f, 700f)) {
            for (y in listOf(1000f, 1200f, 1400f)) {
                val hit = camera.latLonAt(x, y, width, height)
                assertNotNull("tap at ($x, $y) missed the globe", hit)
                val back = camera.screenPositionOf(hit!!.lat, hit.lon, width, height)
                assertNotNull("projecting ($x, $y) back gave nothing", back)
                assertEquals(x, back!!.x, 0.5f)
                assertEquals(y, back.y, 0.5f)
            }
        }
    }

    @Test
    fun `the point the camera looks at projects to the viewport center`() {
        val camera = GlobeCamera(latitude = 25.0, longitude = 100.0)
        val point = camera.screenPositionOf(25.0, 100.0, 1000f, 2000f)

        assertNotNull(point)
        assertEquals(500f, point!!.x, 0.5f)
        assertEquals(1000f, point.y, 0.5f)
    }

    @Test
    fun `a point on the far side does not project`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 0.0)
        // The antipode of what the camera looks at, and a point just past the limb.
        assertNull(camera.screenPositionOf(0.0, 180.0, 1000f, 2000f))
        assertNull(camera.screenPositionOf(0.0, 100.0, 1000f, 2000f))
        assertNotNull(camera.screenPositionOf(0.0, 0.0, 1000f, 2000f))
    }

    @Test
    fun `the horizon tightens as the camera moves in`() {
        // At 60° away the point is visible from far out and gone up close, which
        // is what makes a marker round the limb instead of clinging to it.
        val far = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = GlobeCamera.MAX_DISTANCE)
        val near = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = 1.5f)
        assertNotNull(far.screenPositionOf(0.0, 60.0, 1000f, 2000f))
        assertNull(near.screenPositionOf(0.0, 60.0, 1000f, 2000f))
    }

    @Test
    fun `projection reports nothing for a viewport with no area`() {
        val camera = GlobeCamera()
        assertNull(camera.screenPositionOf(0.0, 0.0, 0f, 0f))
    }

    // Screen scale — what keeps border outlines a constant width on screen

    @Test
    fun `screen scale is 1 at the default distance and shrinks toward the globe`() {
        assertEquals(1f, GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE).screenScale, 1e-6f)
        // iOS `screenScale`: (d - 1) / (4 - 1)
        assertEquals(1f / 3f, GlobeCamera(distance = 2f).screenScale, 1e-6f)
    }

    @Test
    fun `screen scale never binds inside the usable zoom range`() {
        // The clamp sits exactly at the closest zoom, so borders stay a constant
        // width all the way in instead of fattening once past a floor.
        val closest = GlobeCamera(distance = GlobeCamera.MIN_DISTANCE).screenScale
        assertEquals((GlobeCamera.MIN_DISTANCE - 1f) / 3f, closest, 1e-6f)
        assertTrue(
            "the clamp binds before the closest zoom",
            GlobeCamera(distance = GlobeCamera.MIN_DISTANCE + 0.01f).screenScale > closest,
        )
    }

    @Test
    fun `screen scale is capped at 1 when zoomed out`() {
        // Zoomed out, borders keep their base width rather than growing with the
        // distance the formula would otherwise hand back.
        assertEquals(1f, GlobeCamera(distance = GlobeCamera.MAX_DISTANCE).screenScale, 1e-6f)
    }

    // Horizon culling

    @Test
    fun `a mesh on the far side is beyond the horizon`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 0.0)
        // The camera looks at (0, 0), which sits on +X; the antipode is on -X.
        assertTrue(camera.isBeyondHorizon(Vec3(-1f, 0f, 0f), boundingRadius = 0.05f))
        assertFalse(camera.isBeyondHorizon(Vec3(1f, 0f, 0f), boundingRadius = 0.05f))
    }

    @Test
    fun `a bounding sphere reaching past the horizon is kept`() {
        val camera = GlobeCamera(latitude = 0.0, longitude = 0.0)
        // Centered on the far side, but large enough to hold visible geometry —
        // the test has to answer "keep" or borders vanish mid-drag.
        assertFalse(camera.isBeyondHorizon(Vec3(-0.5f, 0f, 0f), boundingRadius = 1.0f))
    }

    @Test
    fun `zooming in pushes the horizon further around the globe`() {
        // At the closest zoom the visible cap shrinks toward a point, so a patch
        // just off-center is already behind the horizon; from far away it is not.
        val patch = Vec3(0.80f, 0.60f, 0f)
        val closest = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = GlobeCamera.MIN_DISTANCE)
        val farthest = GlobeCamera(latitude = 0.0, longitude = 0.0, distance = GlobeCamera.MAX_DISTANCE)
        assertTrue(closest.isBeyondHorizon(patch, 0.01f))
        assertFalse(farthest.isBeyondHorizon(patch, 0.01f))
    }
}
