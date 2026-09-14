package com.anmol.voyage.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.VoyageState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The texture pickers, driven through their menus.
 *
 * The two menus list the same styles, so every item is found by a tag scoped to
 * its picker — the thing most likely to go wrong here is picking from the map's
 * menu and changing the globe.
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = VoyageState()

    private fun label(style: GlobeStyle): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(style.labelRes)

    private fun showSettings() {
        composeTestRule.setContent { SettingsScreen(state = state) }
        // The saved state loads on the main thread; a choice made before it lands
        // would be overwritten by the defaults.
        composeTestRule.waitUntil { state.isLoaded }
    }

    private fun open(pickerTag: String) {
        composeTestRule.onNodeWithTag(pickerTag).performClick()
        composeTestRule.waitForIdle()
    }

    private fun choose(pickerTag: String, style: GlobeStyle) {
        open(pickerTag)
        composeTestRule.onNodeWithTag(styleOptionTag(pickerTag, style)).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun choosingAGlobeStyleChangesOnlyTheGlobe() {
        showSettings()

        choose(GLOBE_STYLE_TAG, GlobeStyle.Natural)

        assertEquals(GlobeStyle.Natural, state.globeStyle)
        assertEquals(GlobeStyle.Realistic, state.mapStyle)
    }

    @Test
    fun choosingAMapStyleChangesOnlyTheMap() {
        showSettings()

        choose(MAP_STYLE_TAG, GlobeStyle.Stylized)

        assertEquals(GlobeStyle.Stylized, state.mapStyle)
        assertEquals(GlobeStyle.Realistic, state.globeStyle)
    }

    @Test
    fun eachFieldShowsItsCurrentStyle() {
        showSettings()
        composeTestRule.runOnIdle { state.setMapStyle(GlobeStyle.Stylized) }

        composeTestRule.onNodeWithTag(GLOBE_STYLE_TAG).assertTextContains(label(GlobeStyle.Realistic))
        composeTestRule.onNodeWithTag(MAP_STYLE_TAG).assertTextContains(label(GlobeStyle.Stylized))
    }

    @Test
    fun theMenuListsEveryStyleAndMarksTheCurrentOne() {
        showSettings()

        open(GLOBE_STYLE_TAG)

        for (style in GlobeStyle.entries) {
            composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, style)).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Realistic)).assertIsSelected()
        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Natural)).assertIsNotSelected()
    }

    @Test
    fun choosingAStyleClosesTheMenu() {
        showSettings()

        choose(GLOBE_STYLE_TAG, GlobeStyle.Stylized)

        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Natural)).assertDoesNotExist()
    }
}
