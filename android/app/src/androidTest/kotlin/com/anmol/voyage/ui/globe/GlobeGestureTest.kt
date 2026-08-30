package com.anmol.voyage.ui.globe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.globe.GlobeFlight
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.NamedCountryMesh
import com.anmol.voyage.globe.PolygonTriangulator
import com.anmol.voyage.globe.UvSphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Rotate and pinch on the real globe, on a real device.
 *
 * A pinch cannot be injected from a JVM unit test — the same reason
 * `WorldMapGestureTest` exists for the flat map. `GlobeCameraTest` already
 * covers the camera math; what these add is the wiring: that the gesture
 * detector on a Filament `SurfaceView` actually receives two-pointer and
 * drag events and moves the camera with them.
 *
 * This is the gap that shipped unverified with the renderer — pinch was tested
 * as math but never as a gesture, because `adb` cannot send a second finger.
 */
class GlobeGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cameras = mutableListOf<GlobeCamera>()

    private val latest: GlobeCamera get() = cameras.lastOrNull() ?: GlobeCamera()

    private fun showGlobe(autoRotating: Boolean = false, focus: LatLon? = null) {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val cache = CountryDataCache { name -> assets.open(name) }
        val hitTester = cache.hitTester
        // Two countries is enough: this exercises gestures, not geometry, and
        // triangulating all 181 would make the test slow for no added coverage.
        val meshes = cache.countries.filter { it.name == "Brazil" || it.name == "Australia" }
            .mapNotNull { country ->
                PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
                    ?.let { NamedCountryMesh(country.name, it) }
            }

        val outlines = PolygonTriangulator.createSectoredOutlineGeometries(
            cache.countries.filter { it.name == "Brazil" || it.name == "Australia" }
                .flatMap { it.polygons },
        )

        composeTestRule.setContent {
            // Held as state so a gesture can end the spin, exactly as
            // `VoyageState.stopAutoRotation` does for the real screen.
            var spinning by remember { mutableStateOf(autoRotating) }
            GlobeSurface(
                ocean = UvSphere.build(segments = 32, rings = 16),
                countries = meshes,
                outlineSectors = outlines,
                microstateDots = emptyList(),
                colorFor = { GlobeCountryFills.of(isVisited = false, isWishlist = false, isSelected = false) },
                oceanColor = Color.Blue,
                backgroundColor = Color.Black,
                hitTester = hitTester,
                onCountryTapped = {},
                focus = focus,
                autoRotating = spinning,
                onInteraction = { spinning = false },
                onCameraChange = { cameras += it },
                modifier = Modifier.fillMaxSize().testTag(GLOBE),
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun pinchingOutwardZoomsIn() {
        showGlobe()
        val before = latest.distance

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            pinch(
                start0 = center + Offset(-60f, 0f),
                end0 = center + Offset(-260f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(260f, 0f),
            )
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "spreading two fingers should reduce camera distance, was $before now ${latest.distance}",
            latest.distance < before,
        )
    }

    @Test
    fun pinchingInwardZoomsOut() {
        showGlobe()
        // Start closer than the default so there is room to zoom out.
        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            pinch(
                start0 = center + Offset(-60f, 0f),
                end0 = center + Offset(-300f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(300f, 0f),
            )
        }
        composeTestRule.waitForIdle()
        val zoomedIn = latest.distance

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            pinch(
                start0 = center + Offset(-300f, 0f),
                end0 = center + Offset(-60f, 0f),
                start1 = center + Offset(300f, 0f),
                end1 = center + Offset(60f, 0f),
            )
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "pinching together should increase camera distance, was $zoomedIn now ${latest.distance}",
            latest.distance > zoomedIn,
        )
    }

    @Test
    fun zoomStaysWithinTheDistanceClamps() {
        showGlobe()
        repeat(6) {
            composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
                pinch(
                    start0 = center + Offset(-20f, 0f),
                    end0 = center + Offset(-400f, 0f),
                    start1 = center + Offset(20f, 0f),
                    end1 = center + Offset(400f, 0f),
                )
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "distance ${latest.distance} escaped the clamp",
            latest.distance >= GlobeCamera.MIN_DISTANCE - 1e-4f,
        )

        // And the far end: pinching together cannot shrink the globe past the
        // point iOS's own gestures stop at.
        repeat(6) {
            composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
                pinch(
                    start0 = center + Offset(-400f, 0f),
                    end0 = center + Offset(-20f, 0f),
                    start1 = center + Offset(400f, 0f),
                    end1 = center + Offset(20f, 0f),
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(GlobeCamera.MAX_DISTANCE, latest.distance, 1e-4f)
    }

    @Test
    fun scrollingZoomsWithAMouseWheel() {
        showGlobe()
        val before = latest.distance

        // Scrolling up (negative delta) zooms in, as on every map.
        composeTestRule.onNodeWithTag(GLOBE).performMouseInput {
            scroll(-3f, ScrollWheel.Vertical)
        }
        composeTestRule.waitForIdle()
        val zoomedIn = latest.distance
        assertTrue("scrolling up should zoom in, was $before now $zoomedIn", zoomedIn < before)

        composeTestRule.onNodeWithTag(GLOBE).performMouseInput {
            scroll(3f, ScrollWheel.Vertical)
        }
        composeTestRule.waitForIdle()
        assertTrue("scrolling down should zoom out", latest.distance > zoomedIn)
    }

    @Test
    fun draggingRotatesTheGlobeUnderTheFinger() {
        showGlobe()

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            swipe(start = center, end = center + Offset(300f, 0f), durationMillis = 200)
        }
        composeTestRule.waitForIdle()

        // Dragging right brings western longitudes into view.
        assertTrue(
            "dragging right should decrease longitude, got ${latest.longitude}",
            latest.longitude < 0.0,
        )
    }

    @Test
    fun draggingDoesNotFlipOverThePole() {
        showGlobe()
        repeat(4) {
            composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
                swipe(start = center, end = center + Offset(0f, 600f), durationMillis = 200)
            }
        }
        composeTestRule.waitForIdle()

        val limit = GlobeCamera.MAX_LATITUDE
        assertTrue("latitude ${latest.latitude} passed the clamp", latest.latitude <= limit)
        assertEquals(limit, latest.latitude, 1.0)
    }

    @Test
    fun aFlickKeepsSpinningAfterTheFingerLifts() {
        showGlobe()

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            swipe(start = center + Offset(-200f, 0f), end = center + Offset(200f, 0f), durationMillis = 100)
        }
        val atLift = latest.longitude

        // The render loop keeps moving the camera with no finger on the screen.
        composeTestRule.waitUntil(timeoutMillis = 2_000) { latest.longitude < atLift - 1.0 }
    }

    @Test
    fun aFlickComesToRestOnItsOwn() {
        showGlobe()

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            swipe(start = center + Offset(-200f, 0f), end = center + Offset(200f, 0f), durationMillis = 100)
        }

        // Well past the ~3 s a flick coasts for; the globe must be still by then.
        Thread.sleep(5_000)
        composeTestRule.waitForIdle()
        val settled = latest.longitude

        Thread.sleep(300)
        composeTestRule.waitForIdle()
        assertEquals("the globe should have stopped", settled, latest.longitude, 1e-6)
    }

    @Test
    fun touchingTheGlobeStopsASpinInFlight() {
        showGlobe()

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            swipe(start = center + Offset(-200f, 0f), end = center + Offset(200f, 0f), durationMillis = 100)
        }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { latest.longitude < 0.0 }

        // A tap, which on iOS calls stopInertia() before hit-testing.
        composeTestRule.onNodeWithTag(GLOBE).performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        val stopped = latest.longitude

        Thread.sleep(300)
        composeTestRule.waitForIdle()
        assertEquals("a tap should stop the spin", stopped, latest.longitude, 1e-6)
    }

    @Test
    fun anUntouchedGlobeTurnsOnItsOwn() {
        showGlobe(autoRotating = true)
        val before = latest.longitude

        // 6°/s, so this is about half a second of spinning.
        composeTestRule.waitUntil(timeoutMillis = 3_000) { latest.longitude < before - 3.0 }
    }

    @Test
    fun theIdleSpinTurnsOnceAMinute() {
        showGlobe(autoRotating = true)
        composeTestRule.waitForIdle()

        val start = latest.longitude
        val startedAt = System.nanoTime()
        Thread.sleep(2_000)
        val travelled = start - latest.longitude
        val seconds = (System.nanoTime() - startedAt) / 1e9

        assertEquals(
            "idle spin ran at ${travelled / seconds} deg/s",
            GlobeCamera.AUTO_ROTATION_DEGREES_PER_SECOND,
            travelled / seconds,
            0.5,
        )
    }

    @Test
    fun touchingTheGlobeEndsTheIdleSpin() {
        showGlobe(autoRotating = true)

        composeTestRule.onNodeWithTag(GLOBE).performTouchInput {
            swipe(start = center, end = center + Offset(120f, 0f), durationMillis = 400)
        }

        // Long enough for the momentum that drag left behind to die out; what is
        // measured after it is the idle spin or nothing.
        Thread.sleep(4_000)
        composeTestRule.waitForIdle()
        val settled = latest.longitude

        // A second of idle spin would be 6°.
        Thread.sleep(1_000)
        composeTestRule.waitForIdle()
        assertEquals("the globe should be still", settled, latest.longitude, 1e-6)
    }

    @Test
    fun selectingAPlaceFliesTheCameraToIt() {
        showGlobe(focus = LatLon(lat = -15.8, lon = -47.9))

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            abs(latest.latitude - (-15.8)) < 0.5 && abs(latest.longitude - (-47.9)) < 0.5
        }
        assertEquals(GlobeFlight.SELECTION_DISTANCE, latest.distance, 0.01f)
    }

    @Test
    fun touchingTheGlobeInterruptsAFlight() {
        showGlobe(focus = LatLon(lat = -15.8, lon = -47.9))

        // Grab the globe while the 0.8 s flight is still under way.
        composeTestRule.onNodeWithTag(GLOBE).performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        val stopped = latest.longitude

        Thread.sleep(1_000)
        composeTestRule.waitForIdle()
        assertEquals("the flight should have been dropped", stopped, latest.longitude, 1e-6)
    }

    private companion object {
        const val GLOBE = "globe"
    }
}
