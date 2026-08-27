package com.anmol.voyage.ui.globe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.anmol.voyage.data.CountryDataCache
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

    private fun showGlobe() {
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

        assertTrue("latitude ${latest.latitude} passed the pole", latest.latitude <= 89.0)
        assertEquals(89.0, latest.latitude, 1.0)
    }

    private companion object {
        const val GLOBE = "globe"
    }
}
