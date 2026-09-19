package com.anmol.voyage.ui.challenges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.globe.GlobeFlight
import com.anmol.voyage.ui.map.CameraFocus
import com.anmol.voyage.ui.map.CountryStyle
import com.anmol.voyage.ui.map.CountryStyles
import com.anmol.voyage.ui.map.WorldScene

/**
 * The world a challenge is played on — iOS's `GlobeState(inMemory: true)` with
 * `showsCapitalMarker` off and the idle spin stopped.
 *
 * Nothing here touches the user's travels: nothing is visited, and the game
 * paints found countries green and revealed ones red through [highlight], which
 * `CountryStyles` treats as a status. The capital star is never shown — it would
 * give answers away.
 */
class ChallengeWorldScene(private val onTap: (String) -> Unit) : WorldScene {

    override var selectedCountry: String? by mutableStateOf(null)
        private set

    override var cameraFocus: CameraFocus? by mutableStateOf(null)
        private set

    override val showsCapital: Boolean get() = false

    override val isAutoRotating: Boolean get() = false

    private val highlights = mutableStateMapOf<String, Color>()

    private var flights = 0

    override fun styleFor(name: String, hasTexture: Boolean): CountryStyle = CountryStyles.of(
        isVisited = false,
        isWishlist = false,
        isSelected = selectedCountry == name,
        hasTexture = hasTexture,
        highlight = highlights[name],
    )

    override fun onCountryTapped(name: String?) {
        if (name != null) onTap(name)
    }

    override fun onInteraction() = Unit

    /** Outlines [name], flying to [center] when given — iOS's `selectCountry(_:center:)`. */
    fun select(name: String, center: LatLon? = null) {
        selectedCountry = name
        if (center != null) flyTo(center, GlobeFlight.SELECTION_DISTANCE)
    }

    fun deselect() {
        selectedCountry = null
    }

    fun highlight(name: String, color: Color) {
        highlights[name] = color
    }

    /** Flies the globe to [target]; a null [distance] keeps the current zoom. */
    fun flyTo(target: LatLon, distance: Float?) {
        flights += 1
        cameraFocus = CameraFocus(target, distance, request = flights)
    }

    /** Clears the board for a replay — iOS's `resetAllData()` on the game's own state. */
    fun reset() {
        selectedCountry = null
        highlights.clear()
    }
}
