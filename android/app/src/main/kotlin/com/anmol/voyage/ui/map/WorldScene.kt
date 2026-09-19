package com.anmol.voyage.ui.map

import androidx.compose.runtime.Stable
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.globe.GlobeFlight

/**
 * What the globe and the map show, and what a tap on them does.
 *
 * Home's scene is the user's own travels (`HomeWorldScene`); a challenge game
 * swaps in one of its own while it is played, which is iOS's
 * `GlobeState(inMemory: true)` — the game colors countries green and red on the
 * very same globe without touching what the user has visited. Both renderers
 * read one of these instead of `VoyageState`, which is also what keeps the game
 * and Home looking alike: every color still comes from [CountryStyles].
 *
 * Implementations back these with Compose state, so reading them from
 * composition or a draw lambda subscribes the reader.
 */
@Stable
interface WorldScene {

    /** The outlined country, or null. */
    val selectedCountry: String?

    /** Whether the selected country's capital is marked with a star. */
    val showsCapital: Boolean

    /** Where the globe's camera was last asked to fly. The flat map never recenters. */
    val cameraFocus: CameraFocus?

    /** Whether the globe turns on its own while nothing else is moving it. */
    val isAutoRotating: Boolean

    /** How [name] is painted right now. */
    fun styleFor(name: String, hasTexture: Boolean): CountryStyle

    /**
     * A tap on the world: a country, or null for one that missed every country
     * on the globe. The map only reports taps that hit.
     */
    fun onCountryTapped(name: String?)

    /** A drag or a zoom on the globe began. */
    fun onInteraction()
}

/**
 * A request to fly the globe's camera onto [target].
 *
 * The globe flies once per distinct value, so a scene that wants to fly to the
 * same place twice — a replayed game going back to its region — bumps
 * [request]. A null [distance] keeps the current zoom, as iOS's `CameraTarget`
 * does.
 */
data class CameraFocus(
    val target: LatLon,
    val distance: Float? = GlobeFlight.SELECTION_DISTANCE,
    val request: Int = 0,
)
