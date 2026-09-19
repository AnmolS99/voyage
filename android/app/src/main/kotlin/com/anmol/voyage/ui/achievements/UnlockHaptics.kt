package com.anmol.voyage.ui.achievements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.anmol.voyage.data.AchievementCatalog
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.VoyageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A confirming buzz whenever something the user marks completes a medal —
 * wherever they marked it, since the country that finishes a continent is
 * usually ticked on Home, not on the Achievements tab.
 *
 * Composed once, by the app shell. It follows the same three marked sets
 * [AchievementsScreen] builds its list from, off the main thread for the same
 * reason. The completed set is remembered across a rotation, so a medal already
 * held is never celebrated twice, and the saved state landing at launch only
 * sets the baseline.
 */
@Composable
fun AchievementUnlockHaptics(state: VoyageState) {
    if (!state.isLoaded) return

    val cache = remember { CountryDataCache.shared }
    val haptics = LocalHapticFeedback.current
    // Ids, not a Set, so the saver needs nothing custom.
    var completed by rememberSaveable { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(cache, state.visitedCountries, state.checkedCities, state.checkedAttractions) {
        val visited = state.visitedCountries
        val cities = state.checkedCities
        val attractions = state.checkedAttractions
        val now = withContext(Dispatchers.Default) {
            AchievementCatalog.of(cache, visited, cities, attractions)
                .filter { it.isCompleted }
                .map { it.id }
        }
        if (unlockedAny(before = completed, after = now)) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        completed = now
    }
}

/**
 * Whether [after] completes a medal [before] had not. A null [before] is the
 * first reading, a baseline rather than a change; losing medals — a reset, an
 * unticked country — is not an unlock.
 */
internal fun unlockedAny(before: List<String>?, after: List<String>): Boolean =
    before != null && after.any { it !in before }
