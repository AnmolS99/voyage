package com.anmol.voyage.ui.achievements

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.anmol.voyage.data.Achievement
import com.anmol.voyage.data.AchievementCatalog
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.MedalUnlock
import com.anmol.voyage.state.VoyageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Celebrates a medal — bronze, silver or gold — the moment something the user
 * marks earns it, wherever they marked it, since the country that earns a
 * continent's medal is usually ticked on Home, not on the Achievements tab. The
 * phone buzzes, and the medal opens in [MedalOverlay] with confetti, one at a
 * time when a single tick earns several.
 *
 * Composed once, by the app shell. It follows the same three marked sets
 * [AchievementsScreen] builds its list from, off the main thread for the same
 * reason. What is completed and what is still waiting to be shown are both
 * remembered across a rotation, so a medal already held is never celebrated
 * twice, and the saved state landing at launch only sets the baseline.
 */
@Composable
fun AchievementUnlockCelebration(state: VoyageState) {
    if (!state.isLoaded) return

    val cache = remember { CountryDataCache.shared }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Lists of a Serializable, so the savers need nothing custom.
    var unlocked by rememberSaveable { mutableStateOf<List<MedalUnlock>?>(null) }
    var waiting by rememberSaveable { mutableStateOf<List<MedalUnlock>>(emptyList()) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }

    LaunchedEffect(cache, state.visitedCountries, state.checkedCities, state.checkedAttractions) {
        val visited = state.visitedCountries
        val cities = state.checkedCities
        val attractions = state.checkedAttractions
        val all = withContext(Dispatchers.Default) {
            AchievementCatalog.of(cache, visited, cities, attractions)
        }
        val now = all.flatMap { it.unlocks }
        val new = newlyUnlocked(before = unlocked, after = now)
        if (new.isNotEmpty()) {
            if (!context.playUnlockVibration()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        // A medal lost again before its turn — the country unticked at once —
        // is no longer worth celebrating, and one overtaken by a higher medal of
        // the same achievement gives way to it.
        val upgraded = new.map { it.id }.toSet()
        waiting = waiting.filter { it in now && it.id !in upgraded } + new
        unlocked = now
        achievements = all
    }

    val next = waiting.firstOrNull() ?: return
    val shown = achievements.firstOrNull { it.id == next.id } ?: return
    // Keyed, so the next medal in line springs up and bursts afresh rather than
    // taking over this one's coin mid-spin.
    key(next) {
        MedalOverlay(
            achievement = shown,
            celebrating = true,
            onDismiss = { waiting = waiting.drop(1) },
        )
    }
}

/**
 * The medals [after] holds that [before] did not, in [after]'s order — each list
 * being every medal held (`Achievement.unlocks`, bronze up). A null [before] is
 * the first reading, a baseline rather than a change; losing medals — a reset,
 * an unticked country — unlocks nothing. A jump of several tiers at once
 * announces only the highest. iOS's `AchievementCatalog.newlyUnlocked`.
 */
internal fun newlyUnlocked(before: List<MedalUnlock>?, after: List<MedalUnlock>): List<MedalUnlock> {
    if (before == null) return emptyList()
    val new = after.filter { it !in before }
    return new.filter { unlock -> new.none { it.id == unlock.id && it.tier > unlock.tier } }
}

/**
 * The unlock's own pattern: a quick rise into two firm clicks, about a third of
 * a second.
 *
 * Not `HapticFeedbackType.Confirm`, which is what this was first. On the
 * Galaxy A55 One UI plays `CONFIRM` as a ~130 ms tap all but identical to the
 * ~120 ms selection tick that comes just before it, so a medal went by
 * unnoticed. A composition is distinct wherever it can be played.
 *
 * Played as touch feedback, so it follows the system's touch-vibration
 * setting and intensity exactly as `performHapticFeedback` does. That needs
 * `VibrationAttributes`, API 33; below that, or on a vibrator without these
 * primitives, this returns false and the caller falls back to a long press.
 */
private fun Context.playUnlockVibration(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return false
    val supported = vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    )
    if (!vibrator.hasVibrator() || !supported) return false

    val effect = VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
        .compose()
    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    return true
}
