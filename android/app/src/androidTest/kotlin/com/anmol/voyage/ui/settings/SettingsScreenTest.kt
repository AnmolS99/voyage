package com.anmol.voyage.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.anmol.voyage.R
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.ThemeMode
import com.anmol.voyage.state.VoyageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Settings tab driven as a user would: the theme's segments, the texture
 * pickers' thumbnails and their full-screen preview, the reset behind its
 * dialog, and the privacy policy link.
 *
 * The two pickers show the same styles, so every thumbnail is found by a tag
 * scoped to its picker — the thing most likely to go wrong here is tapping the
 * map's and changing the globe.
 *
 * The preview's globe turns by itself until touched, so Compose is never idle
 * while it is open: those tests drive the clock by hand.
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

    private fun choose(pickerTag: String, style: GlobeStyle) {
        composeTestRule.onNodeWithTag(styleOptionTag(pickerTag, style)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    /** Opens [pickerTag]'s preview and stops the clock, which its spin never lets idle. */
    private fun openPreview(pickerTag: String) {
        composeTestRule.onNodeWithTag(previewButtonTag(pickerTag)).performScrollTo()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(previewButtonTag(pickerTag)).performClick()
        composeTestRule.mainClock.advanceTimeBy(PREVIEW_SETTLE_MS)
        composeTestRule.onNodeWithTag(STYLE_PREVIEW_TAG).assertIsDisplayed()
    }

    private fun tapInPreview(tag: String) {
        composeTestRule.onNodeWithTag(tag).performClick()
        composeTestRule.mainClock.advanceTimeBy(PREVIEW_SETTLE_MS)
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
    fun theTerrainSwitchTurnsShadingOffAndOnAgain() {
        showSettings()
        assertTrue(state.showsTerrain)

        composeTestRule.onNodeWithTag(TERRAIN_TAG).performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(false, state.showsTerrain) }

        composeTestRule.onNodeWithTag(TERRAIN_TAG).performClick()
        composeTestRule.runOnIdle { assertTrue(state.showsTerrain) }
    }

    @Test
    fun eachPickerMarksItsCurrentStyle() {
        showSettings()
        composeTestRule.runOnIdle { state.setMapStyle(GlobeStyle.Stylized) }

        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Realistic)).assertIsSelected()
        composeTestRule.onNodeWithTag(styleOptionTag(MAP_STYLE_TAG, GlobeStyle.Stylized)).assertIsSelected()
        composeTestRule.onNodeWithTag(styleOptionTag(MAP_STYLE_TAG, GlobeStyle.Realistic)).assertIsNotSelected()
    }

    @Test
    fun everyStyleHasAThumbnailAndTheCurrentOneIsMarked() {
        showSettings()

        for (style in GlobeStyle.entries) {
            composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, style))
                .assertIsDisplayed()
                .assertTextContains(label(style))
        }
        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Realistic)).assertIsSelected()
        composeTestRule.onNodeWithTag(styleOptionTag(GLOBE_STYLE_TAG, GlobeStyle.Natural)).assertIsNotSelected()
    }

    @Test
    fun choosingAThemeSetsItAndMarksItSelected() {
        showSettings()

        composeTestRule.onNodeWithTag(themeOptionTag(ThemeMode.System)).assertIsSelected()
        composeTestRule.onNodeWithTag(themeOptionTag(ThemeMode.Dark)).performClick()

        assertEquals(ThemeMode.Dark, state.themeMode)
        composeTestRule.onNodeWithTag(themeOptionTag(ThemeMode.Dark)).assertIsSelected()
        composeTestRule.onNodeWithTag(themeOptionTag(ThemeMode.System)).assertIsNotSelected()
    }

    @Test
    fun resettingAfterConfirmingClearsWhatWasMarked() {
        showSettings()
        composeTestRule.runOnIdle {
            state.addVisit("France")
            state.addToWishlist("Japan")
            state.setGlobeStyle(GlobeStyle.Natural)
        }

        composeTestRule.onNodeWithTag(RESET_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RESET_CONFIRM_TAG).performClick()
        composeTestRule.waitForIdle()

        assertTrue(state.visitedCountries.isEmpty())
        assertTrue(state.wishlistCountries.isEmpty())
        // Preferences are not data, as on iOS.
        assertEquals(GlobeStyle.Natural, state.globeStyle)
        composeTestRule.onNodeWithTag(RESET_CONFIRM_TAG).assertDoesNotExist()
    }

    @Test
    fun cancellingTheResetKeepsEverything() {
        showSettings()
        composeTestRule.runOnIdle { state.addVisit("France") }

        composeTestRule.onNodeWithTag(RESET_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_cancel),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(setOf("France"), state.visitedCountries)
        composeTestRule.onNodeWithTag(RESET_CONFIRM_TAG).assertDoesNotExist()
    }

    @Test
    fun thePreviewOpensOnTheCurrentStyleAndUsesAnother() {
        showSettings()
        openPreview(GLOBE_STYLE_TAG)

        // Already the globe's style: nothing to use.
        composeTestRule.onNodeWithTag(previewStyleTag(GlobeStyle.Realistic)).assertIsSelected()
        composeTestRule.onNodeWithTag(STYLE_PREVIEW_USE_TAG).assertIsNotEnabled()

        tapInPreview(previewStyleTag(GlobeStyle.Natural))
        composeTestRule.onNodeWithTag(STYLE_PREVIEW_USE_TAG).assertIsEnabled()
        tapInPreview(STYLE_PREVIEW_USE_TAG)

        composeTestRule.onNodeWithTag(STYLE_PREVIEW_TAG).assertDoesNotExist()
        assertEquals(GlobeStyle.Natural, state.globeStyle)
        assertEquals(GlobeStyle.Realistic, state.mapStyle)
    }

    @Test
    fun closingThePreviewChangesNothing() {
        showSettings()
        openPreview(MAP_STYLE_TAG)

        tapInPreview(previewStyleTag(GlobeStyle.Stylized))
        tapInPreview(STYLE_PREVIEW_CLOSE_TAG)

        composeTestRule.onNodeWithTag(STYLE_PREVIEW_TAG).assertDoesNotExist()
        assertEquals(GlobeStyle.Realistic, state.mapStyle)
        assertEquals(GlobeStyle.Realistic, state.globeStyle)
    }

    @Test
    fun thePrivacyPolicyRowOpensThePolicy() {
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) { SettingsScreen(state = state) }
        }

        composeTestRule.onNodeWithTag(PRIVACY_POLICY_TAG).performScrollTo().performClick()

        assertEquals(listOf(PRIVACY_POLICY_URL), opened)
    }

    private companion object {
        /** Longer than the preview's springs take to open or close. */
        const val PREVIEW_SETTLE_MS = 2_000L
    }
}
