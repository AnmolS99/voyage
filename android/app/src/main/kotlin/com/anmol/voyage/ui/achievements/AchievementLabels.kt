package com.anmol.voyage.ui.achievements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.anmol.voyage.R
import com.anmol.voyage.data.AchievementKind
import com.anmol.voyage.data.AchievementUnit

/**
 * The user-visible name of an achievement.
 *
 * Titles live here rather than on [AchievementKind] so the catalog stays a plain
 * data type with no resources in it — the same split [AchievementUnit] makes.
 * The wording is iOS's.
 */
@Composable
@ReadOnlyComposable
fun AchievementKind.title(): String = when (this) {
    AchievementKind.Globetrotter -> stringResource(R.string.achievement_globetrotter)
    AchievementKind.CapitalCollector -> stringResource(R.string.achievement_capital_collector)
    AchievementKind.Wonders -> stringResource(R.string.achievement_wonders)
    AchievementKind.ContinentalDrifter -> stringResource(R.string.achievement_continental_drifter)
    // The continent's own name is data, not a resource: it comes from
    // world.geojson, which both platforms read in English.
    is AchievementKind.Explorer -> stringResource(
        R.string.achievement_explorer,
        continent.displayName,
    )
}

/** What the achievement counts — "countries", "capitals", "wonders". */
@Composable
@ReadOnlyComposable
fun AchievementUnit.label(): String = stringResource(
    when (this) {
        AchievementUnit.COUNTRIES -> R.string.achievement_unit_countries
        AchievementUnit.CAPITALS -> R.string.achievement_unit_capitals
        AchievementUnit.WONDERS -> R.string.achievement_unit_wonders
        AchievementUnit.CONTINENTS -> R.string.achievement_unit_continents
    },
)
