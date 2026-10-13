package com.anmol.voyage.ui.achievements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.anmol.voyage.R
import com.anmol.voyage.data.Achievement
import com.anmol.voyage.data.MedalTier
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The three colors a coin is struck in: its face runs from [center] to [edge],
 * and [rim] is the ring, the edge band and the reverse's star.
 */
internal data class MedalMetal(val center: Color, val edge: Color, val rim: Color)

/**
 * The metal of each medal, and of none — iOS's `MedalArtwork.colors`. A medal
 * not yet earned is one flat dark face with the symbol a darker silhouette.
 */
internal fun metalOf(tier: MedalTier?): MedalMetal = when (tier) {
    null -> MedalMetal(VoyagePalette.medalShadowFace, VoyagePalette.medalShadowFace, VoyagePalette.medalShadowSymbol)
    MedalTier.Bronze -> MedalMetal(VoyagePalette.medalBronzeCenter, VoyagePalette.medalBronzeEdge, VoyagePalette.medalBronzeRim)
    MedalTier.Silver -> MedalMetal(VoyagePalette.medalSilverCenter, VoyagePalette.medalSilverEdge, VoyagePalette.medalSilverRim)
    MedalTier.Gold -> MedalMetal(VoyagePalette.medalGoldCenter, VoyagePalette.medalGoldEdge, VoyagePalette.medalGoldRim)
}

/**
 * The progress ring, tick and percentage color: the theme orange short of
 * bronze, then the medal's own — iOS's `MedalArtwork.accent`.
 */
internal fun accentOf(tier: MedalTier?): Color = when (tier) {
    null -> VoyagePalette.buttonColor
    MedalTier.Bronze -> VoyagePalette.medalBronzeAccent
    MedalTier.Silver -> VoyagePalette.medalSilverAccent
    MedalTier.Gold -> VoyagePalette.medalGoldAccent
}

/** "Bronze", "Silver", "Gold". */
@Composable
@ReadOnlyComposable
internal fun MedalTier.label(): String = stringResource(
    when (this) {
        MedalTier.Bronze -> R.string.medal_tier_bronze
        MedalTier.Silver -> R.string.medal_tier_silver
        MedalTier.Gold -> R.string.medal_tier_gold
    },
)

/** "Silver medal unlocked!" — a whole sentence per medal, for translators. */
@Composable
@ReadOnlyComposable
internal fun MedalTier.unlockedTitle(): String = stringResource(
    when (this) {
        MedalTier.Bronze -> R.string.achievement_unlocked_title_bronze
        MedalTier.Silver -> R.string.achievement_unlocked_title_silver
        MedalTier.Gold -> R.string.achievement_unlocked_title_gold
    },
)

/** "12 more for silver", or "Gold medal" once there is nothing left — iOS's `medalStatus`. */
@Composable
@ReadOnlyComposable
internal fun Achievement.medalStatus(): String = when (nextTier) {
    null -> stringResource(R.string.achievement_status_gold)
    MedalTier.Bronze -> stringResource(R.string.achievement_status_next_bronze, remainingToNextTier)
    MedalTier.Silver -> stringResource(R.string.achievement_status_next_silver, remainingToNextTier)
    MedalTier.Gold -> stringResource(R.string.achievement_status_next_gold, remainingToNextTier)
}
