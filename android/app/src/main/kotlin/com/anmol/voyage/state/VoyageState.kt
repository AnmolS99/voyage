package com.anmol.voyage.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anmol.voyage.data.LatLon
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Shared app state — the Android counterpart of the iOS `GlobeState`, and the
 * single source of truth for what the user has visited, wishlisted, checked off,
 * and selected. Mutate it through its methods rather than keeping copies in
 * screens.
 *
 * Everything that outlives the process is held in one [PersistedState] snapshot
 * and written through a [StateStore]; the selection is deliberately not part of
 * it, matching iOS, where a launch starts with nothing selected. Saves are
 * requested by every mutation and coalesced, so a burst of taps costs one write.
 *
 * State is exposed as Compose state rather than `StateFlow`, because every
 * reader is a composable and a flow would only be collected back into Compose
 * state at each call site. The map reads these properties inside its `Canvas`
 * draw lambda, so a change there re-runs drawing alone — collecting a flow above
 * the canvas would put it back through composition. Note the granularity: the
 * persisted fields share one snapshot, so a theme change invalidates readers of
 * the visited set too; only the selection is independent. Should a non-Compose
 * consumer ever appear — a widget, a sync worker — that snapshot is already the
 * shape a `StateFlow` would carry.
 */
class VoyageState(
    private val store: StateStore = InMemoryStateStore(),
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    /** [viewModelScope] in the app; JVM tests pass an unconfined scope instead. */
    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    /** Conflated: only the newest snapshot is worth writing. */
    private val saveRequests = Channel<PersistedState>(Channel.CONFLATED)

    private var persisted by mutableStateOf(PersistedState())

    /**
     * Whether the saved state has been read back yet. The activity holds the
     * splash screen until it has, so no frame is ever drawn with the wrong theme
     * or an empty map.
     */
    var isLoaded by mutableStateOf(false)
        private set

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

    val visitedCountries: Set<String> get() = persisted.visitedCountries

    val wishlistCountries: Set<String> get() = persisted.wishlistCountries

    val checkedCities: Map<String, Set<String>> get() = persisted.checkedCities

    val checkedAttractions: Map<String, Set<String>> get() = persisted.checkedAttractions

    val viewMode: ViewMode get() = persisted.viewMode

    val globeStyle: GlobeStyle get() = persisted.globeStyle

    val mapStyle: GlobeStyle get() = persisted.mapStyle

    val themeMode: ThemeMode get() = persisted.themeMode

    init {
        scope.launch {
            for (snapshot in saveRequests) store.save(snapshot)
        }
        scope.launch {
            val loaded = try {
                store.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Losing saved state is bad; refusing to start is worse.
                Log.w(TAG, "Could not read saved state", failure)
                PersistedState()
            }
            val migrated = loaded.migrated()
            persisted = migrated
            isLoaded = true
            // Write migrations back so they are paid for once, as iOS does after
            // merging its two stores.
            if (migrated != loaded) requestSave()
        }
    }

    // ---- Selection (not persisted) ----

    fun selectCountry(name: String, center: LatLon? = null) {
        selectedCountry = name
        selectedCountryCenter = center
    }

    fun clearSelection() {
        selectedCountry = null
        selectedCountryCenter = null
    }

    // ---- Visited ----

    fun isVisited(name: String): Boolean = name in persisted.visitedCountries

    fun addVisit(name: String) = mutate { it.copy(visitedCountries = it.visitedCountries + name) }

    fun removeVisit(name: String) = mutate {
        it.copy(visitedCountries = it.visitedCountries - name)
    }

    fun toggleVisited(name: String) {
        if (isVisited(name)) removeVisit(name) else addVisit(name)
    }

    // ---- Wishlist ----

    fun isInWishlist(name: String): Boolean = name in persisted.wishlistCountries

    fun addToWishlist(name: String) = mutate {
        it.copy(wishlistCountries = it.wishlistCountries + name)
    }

    fun removeFromWishlist(name: String) = mutate {
        it.copy(wishlistCountries = it.wishlistCountries - name)
    }

    fun toggleWishlist(name: String) {
        if (isInWishlist(name)) removeFromWishlist(name) else addToWishlist(name)
    }

    // ---- Highlight checklists ----

    fun checkedCitiesFor(country: String): Set<String> = persisted.checkedCities[country].orEmpty()

    fun checkedAttractionsFor(country: String): Set<String> =
        persisted.checkedAttractions[country].orEmpty()

    fun isCityChecked(city: String, country: String): Boolean = city in checkedCitiesFor(country)

    fun isAttractionChecked(attraction: String, country: String): Boolean =
        attraction in checkedAttractionsFor(country)

    fun toggleCheckedCity(city: String, country: String) = mutate {
        it.copy(checkedCities = it.checkedCities.toggling(city, country))
    }

    fun toggleCheckedAttraction(attraction: String, country: String) = mutate {
        it.copy(checkedAttractions = it.checkedAttractions.toggling(attraction, country))
    }

    // ---- Preferences ----

    fun setViewMode(mode: ViewMode) = mutate { it.copy(viewMode = mode) }

    fun toggleViewMode() = setViewMode(
        if (viewMode == ViewMode.Globe) ViewMode.Map else ViewMode.Globe,
    )

    fun setGlobeStyle(style: GlobeStyle) = mutate { it.copy(globeStyle = style) }

    fun setMapStyle(style: GlobeStyle) = mutate { it.copy(mapStyle = style) }

    fun setThemeMode(mode: ThemeMode) = mutate { it.copy(themeMode = mode) }

    /**
     * Clears everything the user has marked, leaving their appearance
     * preferences alone — the same split iOS `resetAllData()` makes.
     */
    fun resetAllData() {
        clearSelection()
        mutate {
            it.copy(
                visitedCountries = emptySet(),
                wishlistCountries = emptySet(),
                checkedCities = emptyMap(),
                checkedAttractions = emptyMap(),
            )
        }
    }

    private fun mutate(transform: (PersistedState) -> PersistedState) {
        val updated = transform(persisted)
        if (updated == persisted) return
        persisted = updated
        requestSave()
    }

    private fun requestSave() {
        saveRequests.trySend(persisted)
    }

    companion object {
        private const val TAG = "VoyageState"

        /** Builds the activity-scoped instance over the real, process-wide store. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { VoyageState(DataStoreStateStore.shared) }
        }
    }
}

/**
 * Adds or removes [item] under [country], dropping the country entirely once
 * nothing is checked — iOS prunes empty sets the same way, and it keeps the
 * saved document from growing a key per country the user ever opened.
 */
private fun Map<String, Set<String>>.toggling(
    item: String,
    country: String,
): Map<String, Set<String>> {
    val current = this[country].orEmpty()
    val updated = if (item in current) current - item else current + item
    return if (updated.isEmpty()) this - country else this + (country to updated)
}
