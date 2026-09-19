package com.anmol.voyage.ui.challenges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ChallengeGameStats
import com.anmol.voyage.challenges.ChallengeRegion
import com.anmol.voyage.challenges.ChallengeTrophy
import com.anmol.voyage.challenges.formatGameTime
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.voyageCardColors
import com.anmol.voyage.ui.theme.voyageCardElevation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Challenges tab: the trophy cabinet and the three game modes — iOS
 * `ChallengesView`. Picking a mode opens its [RegionSelectScreen].
 */
@Composable
fun ChallengesScreen(
    state: VoyageState,
    onSelectMode: (ChallengeGameMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.challengeStats
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "trophies") {
            TrophyShowcase(
                counts = ChallengeTrophy.entries.associateWith(stats::trophyCount),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(ChallengeGameMode.entries, key = { it.rawValue }) { mode ->
            GameModeCard(
                mode = mode,
                gamesPlayed = stats.totalGamesPlayed(mode),
                onClick = { onSelectMode(mode) },
            )
        }
    }
}

/** Bronze, silver and gold, each with how many flawless sweeps have earned it. */
@Composable
private fun TrophyShowcase(counts: Map<ChallengeTrophy, Int>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = voyageCardColors(),
        elevation = voyageCardElevation(),
    ) {
        Row(modifier = Modifier.padding(vertical = 20.dp)) {
            ChallengeTrophy.entries.forEach { trophy ->
                val count = counts.getValue(trophy)
                val name = trophy.displayName()
                val description = stringResource(R.string.challenge_trophy_count, count, name)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrophyIcon(trophy = trophy, earned = count > 0, modifier = Modifier.size(40.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (count > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameModeCard(mode: ChallengeGameMode, gamesPlayed: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = voyageCardColors(),
        elevation = voyageCardElevation(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(VoyagePalette.buttonColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mode.icon, contentDescription = null, tint = VoyagePalette.buttonColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(mode.titleRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(mode.subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = playedLabel(gamesPlayed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * The regions of one game mode, each with its best result — iOS
 * `RegionSelectView`. Tapping a region starts a game there.
 */
@Composable
fun RegionSelectScreen(
    mode: ChallengeGameMode,
    state: VoyageState,
    onPlay: (ChallengeRegion) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = remember { CountryDataCache.shared }
    // Off the main thread for the same reason Achievements does it: the first
    // read of the countries may still be loading them.
    val countryCounts by produceState<Map<ChallengeRegion, Int>?>(null, cache) {
        value = withContext(Dispatchers.Default) {
            ChallengeRegion.entries.associateWith { it.countries(cache.continents).size }
        }
    }
    val stats = state.challengeStats

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.challenge_back),
                )
            }
            Text(stringResource(mode.titleRes), style = MaterialTheme.typography.titleLarge)
        }

        val counts = countryCounts
        if (counts == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ChallengeRegion.entries, key = { it.rawValue }) { region ->
                RegionCard(
                    region = region,
                    countryCount = counts.getValue(region),
                    stats = stats.stats(mode, region),
                    onClick = { onPlay(region) },
                )
            }
        }
    }
}

/**
 * One region: its emoji, the trophy at stake (filled once earned), how many
 * countries a sweep covers, and the best result so far. A flawless region gets
 * the green ring completed achievements have.
 */
@Composable
private fun RegionCard(
    region: ChallengeRegion,
    countryCount: Int,
    stats: ChallengeGameStats,
    onClick: () -> Unit,
) {
    val completed = stats.isPerfect
    val name = region.displayName()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = voyageCardColors(),
        elevation = voyageCardElevation(),
        border = if (completed) BorderStroke(2.dp, VoyagePalette.buttonVisited.copy(alpha = 0.5f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(region.emoji, style = MaterialTheme.typography.headlineLarge)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    TrophyIcon(
                        trophy = region.trophy,
                        earned = completed,
                        unearnedAlpha = 0.55f,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = pluralStringResource(R.plurals.challenge_region_countries, countryCount, countryCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val percentage = stats.bestPercentage
                val time = stats.bestTimeSeconds
                if (percentage != null && time != null) {
                    Text(
                        text = stringResource(
                            R.string.challenge_region_best,
                            percentage,
                            formatGameTime(time),
                            playedLabel(stats.gamesPlayed),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) VoyagePalette.buttonVisited else VoyagePalette.buttonColor,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.challenge_not_played),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** "Played 3 times", or "Not played yet". */
@Composable
private fun playedLabel(gamesPlayed: Int): String = if (gamesPlayed > 0) {
    pluralStringResource(R.plurals.challenge_played_times, gamesPlayed, gamesPlayed)
} else {
    stringResource(R.string.challenge_not_played)
}
