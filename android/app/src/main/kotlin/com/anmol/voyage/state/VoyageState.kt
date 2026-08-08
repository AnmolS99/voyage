package com.anmol.voyage.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.anmol.voyage.data.LatLon

/**
 * Shared app state — the Android counterpart of the iOS `GlobeState`, and the
 * single source of truth for what the user has visited, wishlisted, and selected.
 * Mutate it through its methods rather than keeping copies in screens.
 *
 * **Phase 4 scope.** This holds only what the map needs. Phase 5 adds the rest
 * (checked cities/attractions, view mode, style prefs, dark mode) together with
 * DataStore persistence and Auto Backup; until then state lives for the lifetime
 * of the activity's [ViewModel] and is deliberately not written to disk.
 */
class VoyageState : ViewModel() {

    /** The tapped country, by name. Null when nothing is selected. */
    var selectedCountry: String? by mutableStateOf(null)
        private set

    /**
     * Geographic center of [selectedCountry]. The map does not recenter on
     * selection (neither does iOS's), but the globe flies its camera here — it is
     * computed at selection time on both platforms, so the value is kept ready
     * for Phase 7 rather than recomputed there.
     */
    var selectedCountryCenter: LatLon? by mutableStateOf(null)
        private set

    var visitedCountries: Set<String> by mutableStateOf(emptySet())
        private set

    var wishlistCountries: Set<String> by mutableStateOf(emptySet())
        private set

    fun selectCountry(name: String, center: LatLon? = null) {
        selectedCountry = name
        selectedCountryCenter = center
    }

    fun clearSelection() {
        selectedCountry = null
        selectedCountryCenter = null
    }

    fun isVisited(name: String): Boolean = name in visitedCountries

    fun isWishlisted(name: String): Boolean = name in wishlistCountries

    fun addVisit(name: String) {
        visitedCountries = visitedCountries + name
    }

    fun removeVisit(name: String) {
        visitedCountries = visitedCountries - name
    }

    fun toggleVisited(name: String) {
        if (isVisited(name)) removeVisit(name) else addVisit(name)
    }

    fun toggleWishlist(name: String) {
        wishlistCountries = if (isWishlisted(name)) {
            wishlistCountries - name
        } else {
            wishlistCountries + name
        }
    }
}
