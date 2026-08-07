package com.anmol.voyage.ui.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.VoyageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pan, zoom, and tap on the real map, on a real device.
 *
 * A pinch cannot be injected from a JVM unit test, and the pan/zoom transform is
 * exactly where a projection bug hides: the map still *looks* right while taps
 * resolve to the wrong country. So these assertions are all of the form "after
 * this gesture, tapping here still selects that country" — black-box, and false
 * the moment the draw transform and the inverse transform disagree.
 */
class WorldMapGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = VoyageState()
    private lateinit var projection: MapProjection

    /** Screen position of a lon/lat before any pan or zoom. */
    private fun screenPosition(lat: Double, lon: Double) =
        Offset(projection.mapX(lon), projection.viewY(lat))

    private fun showMap() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val cache = CountryDataCache { name -> assets.open(name) }
        val countries = cache.countries
        val hitTester = cache.hitTester

        composeTestRule.setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val mapProjection = remember(maxWidth, maxHeight, density) {
                    with(density) { MapProjection(maxWidth.toPx(), maxHeight.toPx()) }
                }
                projection = mapProjection
                val paths = remember(mapProjection) {
                    buildCountryPaths(countries, mapProjection)
                }
                WorldMap(
                    countries = countries,
                    paths = paths,
                    hitTester = hitTester,
                    state = state,
                    projection = mapProjection,
                    modifier = Modifier.testTag(MAP),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun tappingACountrySelectsIt() {
        showMap()
        composeTestRule.onNodeWithTag(MAP).performTouchInput {
            click(screenPosition(lat = -10.0, lon = -50.0))
        }
        composeTestRule.waitForIdle()
        assertEquals("Brazil", state.selectedCountry)
    }

    @Test
    fun zoomKeepsThePinchCentreUnderTheFingers() {
        showMap()
        // Anchored on the equator deliberately. On a phone the map is letterboxed,
        // and until it is tall enough to overhang the view the vertical clamp keeps
        // it centred — which legitimately overrides the anchor in y, on iOS too.
        // At latitude 0 the anchor needs no vertical shift, so this isolates the
        // anchor maths from the clamp.
        val uganda = screenPosition(lat = 0.0, lon = 32.5)

        composeTestRule.onNodeWithTag(MAP).performTouchInput {
            // Both fingers spread away from Uganda, making it the pinch centroid. If
            // the zoom anchored anywhere else — the view centre, say — the country
            // slides out from under this point and the tap below misses it.
            pinch(
                start0 = uganda - Offset(40f, 0f),
                end0 = uganda - Offset(160f, 0f),
                start1 = uganda + Offset(40f, 0f),
                end1 = uganda + Offset(160f, 0f),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MAP).performTouchInput { click(uganda) }
        composeTestRule.waitForIdle()
        assertEquals("Uganda", state.selectedCountry)
    }

    @Test
    fun panningIsClampedAtMinimumZoom() {
        showMap()
        val brazil = screenPosition(lat = -10.0, lon = -50.0)

        // Fully zoomed out the map exactly fits the view width, so there is nothing
        // to pan into: a hard drag must leave everything where it was.
        composeTestRule.onNodeWithTag(MAP).performTouchInput {
            swipe(start = brazil, end = brazil + Offset(300f, 0f))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MAP).performTouchInput { click(brazil) }
        composeTestRule.waitForIdle()
        assertEquals("Brazil", state.selectedCountry)
    }

    @Test
    fun panningMovesTheMapOnceZoomedIn() {
        showMap()
        val africa = screenPosition(lat = 8.0, lon = 20.0)

        composeTestRule.onNodeWithTag(MAP).performTouchInput {
            pinch(
                start0 = africa - Offset(20f, 0f),
                end0 = africa - Offset(300f, 0f),
                start1 = africa + Offset(20f, 0f),
                end1 = africa + Offset(300f, 0f),
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MAP).performTouchInput { click(africa) }
        composeTestRule.waitForIdle()
        val beforePan = state.selectedCountry

        // Drag a long way west; whatever was under the finger must have moved on.
        composeTestRule.onNodeWithTag(MAP).performTouchInput {
            swipe(start = africa, end = africa - Offset(400f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MAP).performTouchInput { click(africa) }
        composeTestRule.waitForIdle()

        assertNotEquals(beforePan, state.selectedCountry)
    }

    private companion object {
        const val MAP = "world-map"
    }
}
