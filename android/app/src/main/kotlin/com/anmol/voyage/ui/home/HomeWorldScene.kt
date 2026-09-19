package com.anmol.voyage.ui.home

import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.map.CameraFocus
import com.anmol.voyage.ui.map.CountryStyle
import com.anmol.voyage.ui.map.CountryStyles
import com.anmol.voyage.ui.map.WorldScene

/**
 * Home's world: the user's own travels, read straight from [VoyageState].
 *
 * A tap selects the country it lands on and flies the globe there; a tap that
 * misses every country deselects, and brings the idle spin back.
 */
class HomeWorldScene(
    private val state: VoyageState,
    private val hitTester: CountryHitTester,
) : WorldScene {

    override val selectedCountry: String? get() = state.selectedCountry

    override val showsCapital: Boolean get() = true

    override val cameraFocus: CameraFocus?
        get() = state.selectedCountryCenter?.let { CameraFocus(it) }

    override val isAutoRotating: Boolean get() = state.isAutoRotating

    override fun styleFor(name: String, hasTexture: Boolean): CountryStyle = CountryStyles.of(
        isVisited = state.isVisited(name),
        isWishlist = state.isInWishlist(name),
        isSelected = state.selectedCountry == name,
        hasTexture = hasTexture,
    )

    override fun onCountryTapped(name: String?) {
        if (name == null) state.clearSelection() else state.selectCountry(name, hitTester.center(name))
    }

    override fun onInteraction() = state.stopAutoRotation()
}
