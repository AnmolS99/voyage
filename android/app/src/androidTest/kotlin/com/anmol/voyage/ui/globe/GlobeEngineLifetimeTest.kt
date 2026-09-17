package com.anmol.voyage.ui.globe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.anmol.voyage.R
import com.anmol.voyage.VoyageApp
import com.anmol.voyage.destinationTag
import com.anmol.voyage.navigation.VoyageDestination
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.ViewMode
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.settings.GLOBE_STYLE_TAG
import com.anmol.voyage.ui.settings.styleOptionTag
import com.anmol.voyage.ui.theme.VoyageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * One Filament engine per Activity — the Android plan's 7.11.
 *
 * The globe's engine carries the compiled materials, 181 uploaded meshes and a
 * 4096 x 2048 Earth texture, and rebuilding it was what made leaving the Home
 * tab and coming back cost ~35-45 ms of a ~200 ms switch. It survives now
 * because `HomeScreen` is composed outside the `NavHost` and hidden rather than
 * removed, and because the host outlives the globe/map toggle inside it.
 *
 * Neither of those is visible in a screenshot, so the engine counts itself and
 * this asserts the count does not move. The second test covers what a hidden
 * full-screen surface can quietly break instead — the reason Home is layered
 * under the `NavHost` and not over it.
 */
class GlobeEngineLifetimeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = VoyageState()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun showApp() {
        composeTestRule.setContent {
            VoyageTheme(darkTheme = false) { VoyageApp(state = state) }
        }
        // The engine is built with Home, which is composed as soon as the saved
        // state lands — the splash screen holds the real app until then.
        composeTestRule.waitUntil { state.isLoaded }
        composeTestRule.waitForIdle()
    }

    private fun tap(destination: VoyageDestination) {
        composeTestRule.onNodeWithTag(destinationTag(destination)).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun theEngineSurvivesTabSwitchesAndViewModeToggles() {
        showApp()
        val engines = GlobeRenderer.enginesCreated

        tap(VoyageDestination.Settings)
        tap(VoyageDestination.Achievements)
        tap(VoyageDestination.Home)

        assertEquals(
            "leaving the Home tab and coming back rebuilt the engine",
            engines,
            GlobeRenderer.enginesCreated,
        )

        // And the other way out of the globe: the flat map, which takes the
        // surface with it but must leave the engine standing. These two taps
        // are also what proves Home is reachable *under* the NavHost: they only
        // land if the host's empty Home page lets a touch through to it.
        composeTestRule.onNodeWithContentDescription(string(R.string.home_show_map)).performClick()
        composeTestRule.waitForIdle()
        assertEquals("the tap never reached Home", ViewMode.Map, state.viewMode)
        composeTestRule.onNodeWithContentDescription(string(R.string.home_show_globe)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(ViewMode.Globe, state.viewMode)

        assertEquals(
            "toggling to the flat map and back rebuilt the engine",
            engines,
            GlobeRenderer.enginesCreated,
        )
    }

    @Test
    fun aHiddenHomeDoesNotSwallowTouches() {
        showApp()

        tap(VoyageDestination.Settings)

        // Home is still composed, still full-screen and still holding a surface
        // while this screen is up. Layered over it — as this screen was first
        // written — its `AndroidView` takes this tap and the menu never opens,
        // because Compose stops at the first sibling it hits, handler or not.
        composeTestRule.onNodeWithTag(GLOBE_STYLE_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Natural))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(GlobeStyle.Natural, state.globeStyle)
    }
}
