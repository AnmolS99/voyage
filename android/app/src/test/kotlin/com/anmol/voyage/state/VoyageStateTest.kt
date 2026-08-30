package com.anmol.voyage.state

import com.anmol.voyage.data.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared app state: that its mutations mean the same thing as the iOS
 * `GlobeState` methods they were ported from, and that every one of them reaches
 * the store.
 *
 * The state is driven on an unconfined scope, which runs its loading and saving
 * coroutines inline on the test thread — no dispatcher rules, no waiting, and
 * assertions can read the store immediately after a mutation.
 */
class VoyageStateTest {

    private fun stateOver(store: StateStore) = VoyageState(store, CoroutineScope(Dispatchers.Unconfined))

    private fun state() = stateOver(InMemoryStateStore())

    @Test
    fun `saved state is loaded before anything is drawn`() {
        val store = InMemoryStateStore(
            PersistedState(
                visitedCountries = setOf("Norway"),
                wishlistCountries = setOf("Peru"),
                themeMode = ThemeMode.Dark,
            ),
        )
        val state = stateOver(store)

        assertTrue(state.isLoaded)
        assertEquals(setOf("Norway"), state.visitedCountries)
        assertEquals(setOf("Peru"), state.wishlistCountries)
        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `loading state that needs no migration writes nothing back`() {
        val store = InMemoryStateStore(PersistedState(visitedCountries = setOf("Norway")))
        stateOver(store)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `legacy country names are migrated on load and written back once`() {
        val store = InMemoryStateStore(
            PersistedState(
                visitedCountries = setOf("Turkey"),
                checkedCities = mapOf("Cape Verde" to setOf("Mindelo")),
            ),
        )
        val state = stateOver(store)

        assertEquals(setOf("Türkiye"), state.visitedCountries)
        assertEquals(setOf("Mindelo"), state.checkedCitiesFor("Cabo Verde"))
        assertEquals(setOf("Türkiye"), store.state.visitedCountries)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `visited countries can be added, removed and toggled`() {
        val state = state()

        state.addVisit("Norway")
        assertTrue(state.isVisited("Norway"))
        assertEquals(setOf("Norway"), state.visitedCountries)

        state.removeVisit("Norway")
        assertFalse(state.isVisited("Norway"))

        state.toggleVisited("Japan")
        assertTrue(state.isVisited("Japan"))
        state.toggleVisited("Japan")
        assertFalse(state.isVisited("Japan"))
    }

    @Test
    fun `wishlist countries can be added, removed and toggled`() {
        val state = state()

        state.addToWishlist("Peru")
        assertTrue(state.isInWishlist("Peru"))

        state.removeFromWishlist("Peru")
        assertFalse(state.isInWishlist("Peru"))

        state.toggleWishlist("Chile")
        assertTrue(state.isInWishlist("Chile"))
        state.toggleWishlist("Chile")
        assertFalse(state.isInWishlist("Chile"))
    }

    @Test
    fun `a country can be visited and wishlisted at once`() {
        val state = state()
        state.addVisit("Norway")
        state.addToWishlist("Norway")

        assertTrue(state.isVisited("Norway"))
        assertTrue(state.isInWishlist("Norway"))
    }

    @Test
    fun `checked cities are per country and drop out when the last one is unchecked`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.toggleCheckedCity("Oslo", "Norway")
        state.toggleCheckedCity("Bergen", "Norway")
        state.toggleCheckedCity("Lima", "Peru")

        assertEquals(setOf("Oslo", "Bergen"), state.checkedCitiesFor("Norway"))
        assertTrue(state.isCityChecked("Lima", "Peru"))
        assertFalse(state.isCityChecked("Lima", "Norway"))

        state.toggleCheckedCity("Oslo", "Norway")
        assertEquals(setOf("Bergen"), state.checkedCitiesFor("Norway"))

        state.toggleCheckedCity("Bergen", "Norway")
        assertEquals(emptySet<String>(), state.checkedCitiesFor("Norway"))
        // Pruned rather than left as an empty set, exactly as iOS does — an
        // empty entry per country the user ever opened would bloat the document.
        assertFalse("Norway" in store.state.checkedCities)
        assertEquals(setOf("Peru"), store.state.checkedCities.keys)
    }

    @Test
    fun `checked attractions behave the same as checked cities`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.toggleCheckedAttraction("Machu Picchu", "Peru")
        assertTrue(state.isAttractionChecked("Machu Picchu", "Peru"))
        assertEquals(mapOf("Peru" to setOf("Machu Picchu")), state.checkedAttractions)

        state.toggleCheckedAttraction("Machu Picchu", "Peru")
        assertEquals(emptyMap<String, Set<String>>(), store.state.checkedAttractions)
    }

    @Test
    fun `preferences are stored`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.setThemeMode(ThemeMode.Light)
        state.setGlobeStyle(GlobeStyle.Stylized)
        state.setMapStyle(GlobeStyle.Natural)
        state.setViewMode(ViewMode.Globe)

        assertEquals(ThemeMode.Light, state.themeMode)
        assertEquals(GlobeStyle.Stylized, state.globeStyle)
        assertEquals(GlobeStyle.Natural, state.mapStyle)
        assertEquals(ViewMode.Globe, state.viewMode)
        assertEquals(
            PersistedState(
                themeMode = ThemeMode.Light,
                globeStyle = GlobeStyle.Stylized,
                mapStyle = GlobeStyle.Natural,
                viewMode = ViewMode.Globe,
            ),
            store.state,
        )
    }

    @Test
    fun `the view mode toggles between the globe and the map`() {
        val state = state()
        val initial = state.viewMode

        state.toggleViewMode()
        assertTrue(state.viewMode != initial)
        state.toggleViewMode()
        assertEquals(initial, state.viewMode)
    }

    @Test
    fun `every mutation reaches the store`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.addVisit("Norway")
        state.addToWishlist("Peru")
        state.toggleCheckedCity("Oslo", "Norway")
        state.toggleCheckedAttraction("Machu Picchu", "Peru")

        assertEquals(
            PersistedState(
                visitedCountries = setOf("Norway"),
                wishlistCountries = setOf("Peru"),
                checkedCities = mapOf("Norway" to setOf("Oslo")),
                checkedAttractions = mapOf("Peru" to setOf("Machu Picchu")),
            ),
            store.state,
        )
    }

    @Test
    fun `a mutation that changes nothing is not written`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.addVisit("Norway")
        val writes = store.saveCount

        state.removeVisit("Iceland")
        state.addVisit("Norway")

        assertEquals(writes, store.saveCount)
    }

    @Test
    fun `selection is remembered in memory only`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.selectCountry("Norway", LatLon(lat = 61.0, lon = 8.0))
        assertEquals("Norway", state.selectedCountry)
        assertEquals(LatLon(lat = 61.0, lon = 8.0), state.selectedCountryCenter)
        assertEquals(0, store.saveCount)

        state.clearSelection()
        assertNull(state.selectedCountry)
        assertNull(state.selectedCountryCenter)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `the globe turns on its own until something is selected`() {
        val state = state()

        // iOS `GlobeState.isAutoRotating` starts true on every launch.
        assertTrue("a fresh globe should be turning", state.isAutoRotating)

        state.selectCountry("Norway", LatLon(lat = 61.0, lon = 8.0))
        assertFalse("selecting should stop the spin", state.isAutoRotating)

        state.clearSelection()
        assertTrue("deselecting should start it again", state.isAutoRotating)
    }

    @Test
    fun `dragging the globe stops the spin for good`() {
        val state = state()

        state.stopAutoRotation()
        assertFalse(state.isAutoRotating)

        // Selecting and deselecting is the only way back, as on iOS: nothing
        // resumes the spin just because the finger left the screen.
        state.selectCountry("Peru")
        assertFalse(state.isAutoRotating)
        state.clearSelection()
        assertTrue(state.isAutoRotating)
    }

    @Test
    fun `auto-rotation is never written to the store`() {
        val store = InMemoryStateStore()
        val state = stateOver(store)

        state.stopAutoRotation()

        // iOS does not persist it either — the globe greets you spinning.
        assertEquals(0, store.saveCount)
        assertTrue(stateOver(store).isAutoRotating)
    }

    @Test
    fun `resetting clears what the user marked but keeps their preferences`() {
        val store = InMemoryStateStore(
            PersistedState(
                visitedCountries = setOf("Norway"),
                wishlistCountries = setOf("Peru"),
                checkedCities = mapOf("Norway" to setOf("Oslo")),
                checkedAttractions = mapOf("Peru" to setOf("Machu Picchu")),
                themeMode = ThemeMode.Dark,
                globeStyle = GlobeStyle.Stylized,
            ),
        )
        val state = stateOver(store)
        state.selectCountry("Norway")

        state.resetAllData()

        assertEquals(emptySet<String>(), state.visitedCountries)
        assertEquals(emptySet<String>(), state.wishlistCountries)
        assertEquals(emptyMap<String, Set<String>>(), state.checkedCities)
        assertEquals(emptyMap<String, Set<String>>(), state.checkedAttractions)
        assertNull(state.selectedCountry)
        assertTrue("a reset globe turns again, as iOS's does", state.isAutoRotating)
        assertEquals(ThemeMode.Dark, state.themeMode)
        assertEquals(GlobeStyle.Stylized, state.globeStyle)
        assertEquals(PersistedState(themeMode = ThemeMode.Dark, globeStyle = GlobeStyle.Stylized), store.state)
    }
}
