package com.anmol.voyage.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.tan
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
    fun `rotation clamps latitude where iOS clamps it`() {
        // iOS pins `currentRotationX` to ±.pi / 2.5, which is ±72°.
        assertEquals(72.0, Math.toDegrees(Math.PI / 2.5), 1e-9)

        val camera = GlobeCamera(latitude = 60.0, longitude = 0.0)
        assertEquals(72.0, camera.rotatedBy(deltaLatitude = 50.0, deltaLongitude = 0.0).latitude, 1e-9)
        assertEquals(-72.0, camera.rotatedBy(deltaLatitude = -200.0, deltaLongitude = 0.0).latitude, 1e-9)
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
    fun `every camera is built through the same limits`() {
        // `at` is the one door in, so a drag, a pinch and a flight cannot each
        // develop their own idea of how far the camera may go.
        val extreme = GlobeCamera.at(latitude = 200.0, longitude = 540.0, distance = 99f)
        assertEquals(GlobeCamera.MAX_LATITUDE, extreme.latitude, 1e-9)
        assertEquals(180.0, kotlin.math.abs(extreme.longitude), 1e-9)
        assertEquals(GlobeCamera.MAX_DISTANCE, extreme.distance, 1e-6f)

        val below = GlobeCamera.at(latitude = -91.0, longitude = -190.0, distance = 0.1f)
        assertEquals(-GlobeCamera.MAX_LATITUDE, below.latitude, 1e-9)
        assertEquals(170.0, below.longitude, 1e-9)
        assertEquals(GlobeCamera.MIN_DISTANCE, below.distance, 1e-6f)
    }

    // Pan speed — the iOS curve, which is what makes a drag feel the same

    @Test
    fun `pan speed at the default distance is the iOS base speed`() {
        // iOS `basePanRotationSpeed`: 0.005 radians per point of finger travel.
        assertEquals(
            Math.toDegrees(0.005),
            GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE).degreesPerDp,
            1e-6,
        )
    }

    @Test
    fun `pan speed grows with the square of distance once zoomed out`() {
        val base = GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE).degreesPerDp
        // Twice the distance, four times the speed: the globe stays quick to
        // spin when it is small on screen.
        assertEquals(4.0 * base, GlobeCamera(distance = 8f).degreesPerDp, 1e-6)
        assertEquals(
            2.25 * base,
            GlobeCamera(distance = 1.5f * GlobeCamera.DEFAULT_DISTANCE).degreesPerDp,
            1e-6,
        )
    }

    @Test
    fun `zoomed in, pan speed is capped below the quadratic curve`() {
        val base = GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE).degreesPerDp

        // At distance 2 the quadratic alone would give a quarter of base speed;
        // the finger-tracking cap takes it lower still.
        val near = GlobeCamera(distance = 2f).degreesPerDp
        assertTrue("cap should bind below the reference distance, got $near", near < 0.25 * base)

        // The cap is the tightest at the closest zoom, where fine control matters.
        val closest = GlobeCamera(distance = GlobeCamera.MIN_DISTANCE).degreesPerDp
        assertTrue("closest zoom should be the slowest, got $closest", closest < near)
    }

    @Test
    fun `pan speed is continuous across the reference distance`() {
        val below = GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE - 1e-3f).degreesPerDp
        val at = GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE).degreesPerDp
        val above = GlobeCamera(distance = GlobeCamera.DEFAULT_DISTANCE + 1e-3f).degreesPerDp
        // Only the curve's own slope apart, with no step where the branches meet.
        assertEquals(at, below, 1e-3)
        assertEquals(at, above, 1e-3)
    }

    @Test
    fun `dragging covers less ground when zoomed in`() {
        val far = GlobeCamera(distance = 8f).degreesPerDp
        val near = GlobeCamera(distance = 2f).degreesPerDp
        assertTrue("zoomed in should rotate less per dp", near < far)
    }

    // The idle spin

    @Test
    fun `an untouched globe turns once a minute`() {
        var camera = GlobeCamera(latitude = 20.0, longitude = 0.0)
        // A minute of 120 Hz frames.
        repeat(60 * 120) { camera = camera.autoRotated(1f / 120f) }

        // iOS's `rotateBy(y: 2π)` over 60 s: one full turn, back where it started.
        assertEquals(0.0, camera.longitude, 1e-3)
        assertEquals("the spin should not tilt the globe", 20.0, camera.latitude, 1e-9)
    }

    @Test
    fun `the idle spin turns the way Earth does`() {
        val turned = GlobeCamera(latitude = 0.0, longitude = 0.0).autoRotated(1f)

        // Longitude running backwards is the globe turning eastward under a
        // fixed camera — the same direction dragging right sends it.
        assertEquals(-GlobeCamera.AUTO_ROTATION_DEGREES_PER_SECOND, turned.longitude, 1e-6)
        assertTrue(
            "it should agree with a rightward drag",
            GlobeCamera().rotatedBy(0.0, -1.0).longitude < 0.0,
        )
    }

    // Marker sizing — what keeps dots and the capital star a constant size

    @Test
    fun `a pixel covers less of the world as the camera moves in`() {
        val height = 2400f
        val far = GlobeCamera(distance = 7f).pixelSizeInWorld(height)
        val near = GlobeCamera(distance = 2f).pixelSizeInWorld(height)
        assertTrue("zoomed in, a pixel should cover less world", near < far)

        // Linear in the distance to the globe's near face, which is what makes a
        // marker of a fixed pixel size cover the same pixels at every zoom.
        assertEquals((2f - 1f) / (7f - 1f), near / far, 1e-5f)
    }

    @Test
    fun `a marker of a fixed pixel size spans the same pixels at any zoom`() {
        val height = 2400f
        val radiusPx = 13f
        // Half the viewport, in world units, at the globe's near face.
        fun halfViewport(distance: Float) =
            tan(Math.toRadians(GlobeCamera.FOV_DEGREES / 2.0)).toFloat() * (distance - 1f)

        for (distance in listOf(1.5f, 4f, 9f)) {
            val world = radiusPx * GlobeCamera(distance = distance).pixelSizeInWorld(height)
            val pixels = world / halfViewport(distance) * (height / 2f)
            assertEquals("marker changed size at distance $distance", radiusPx, pixels, 1e-2f)
        }
    }

    @Test
    fun `pixel size reports nothing for a viewport with no height`() {
        assertEquals(0f, GlobeCamera().pixelSizeInWorld(0f), 0f)
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
