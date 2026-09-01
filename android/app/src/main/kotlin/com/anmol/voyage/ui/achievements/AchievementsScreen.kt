package com.anmol.voyage.ui.achievements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.Achievement
import com.anmol.voyage.data.AchievementCatalog
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Achievements tab: how much of the world the user has covered, one medal at
 * a time. The Android analogue of iOS's `AchievementsView`.
 *
 * The list itself is [AchievementCatalog]'s, so what counts toward what is the
 * same on both platforms and is asserted by `AchievementTest` rather than by
 * looking at this screen. Everything here is presentation: a progress ring per
 * medal, the items behind it when a card is expanded, and the spinnable medal.
 */
@Composable
fun AchievementsScreen(state: VoyageState, modifier: Modifier = Modifier) {
    val cache = remember { CountryDataCache.shared }

    // Built off the main thread for the same reason Home's country data is: the
    // first read of `countries` parses 3.2 MB of GeoJSON if the launch-time
    // prewarm has not finished. Reading the marked sets here is also what
    // subscribes the screen to them, so ticking a city elsewhere rebuilds this.
    val achievements by produceState<List<Achievement>?>(
        initialValue = null,
        cache,
        state.visitedCountries,
        state.checkedCities,
        state.checkedAttractions,
    ) {
        val visited = state.visitedCountries
        val cities = state.checkedCities
        val attractions = state.checkedAttractions
        value = withContext(Dispatchers.Default) {
            AchievementCatalog.of(cache, visited, cities, attractions)
        }
    }

    val list = achievements
    if (list == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var medalId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            SummaryCard(
                completed = list.count { it.isCompleted },
                total = list.size,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(list, key = { it.id }) { achievement ->
            AchievementCard(
                achievement = achievement,
                isExpanded = expandedId == achievement.id,
                onClick = {
                    expandedId = if (expandedId == achievement.id) null else achievement.id
                },
                onMedalClick = { medalId = achievement.id },
            )
        }
    }

    list.firstOrNull { it.id == medalId }?.let { achievement ->
        MedalOverlay(achievement = achievement, onDismiss = { medalId = null })
    }
}

/** "3 of 10 Achievements Unlocked". */
@Composable
private fun SummaryCard(completed: Int, total: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors(),
        elevation = cardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.achievements_unlocked, completed, total),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.achievements_unlocked_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One achievement: its medal in a progress ring, its name and progress, and —
 * once tapped — what has been visited and what is left.
 *
 * The card and the medal are two separate targets, as on iOS: the card expands,
 * the medal opens the full-screen coin.
 */
@Composable
private fun AchievementCard(
    achievement: Achievement,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onMedalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(achievement)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors(),
        elevation = cardElevation(),
        border = if (achievement.isCompleted) {
            BorderStroke(2.dp, accent.copy(alpha = COMPLETED_BORDER_ALPHA))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(
                        if (isExpanded) R.string.achievement_hide_details
                        else R.string.achievement_show_details,
                    ),
                    onClick = onClick,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProgressMedal(
                    achievement = achievement,
                    accent = accent,
                    onClick = onMedalClick,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = achievement.kind.title(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.achievement_progress,
                            achievement.current,
                            achievement.total,
                            achievement.unit.label(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.achievement_percentage, achievement.percentage),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(
                        animateFloatAsState(
                            targetValue = if (isExpanded) HALF_TURN else 0f,
                            label = "chevron",
                        ).value,
                    ),
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                ItemLists(achievement)
            }
        }
    }
}

/**
 * The medal, ringed by its progress.
 *
 * The ring is drawn rather than assembled from a `CircularProgressIndicator`:
 * iOS's is a plain trimmed circle with a round cap, and Material's indicator now
 * draws a gap and a stop indicator that would put the two platforms visibly out
 * of step.
 */
@Composable
private fun ProgressMedal(
    achievement: Achievement,
    accent: Color,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = achievement.progress,
        label = "achievement-progress",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .size(RING_SIZE)
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(
                    R.string.achievement_show_medal,
                    achievement.kind.title(),
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val diameter = size.minDimension - stroke
            drawCircle(color = track, radius = diameter / 2f, style = Stroke(width = stroke))
            drawArc(
                color = accent,
                // Noon, as iOS's `-90°` rotation puts it.
                startAngle = -QUARTER_TURN,
                sweepAngle = progress * FULL_TURN,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        MedalCoin(
            medal = achievement.medal,
            isEarned = achievement.isCompleted,
            size = COIN_SIZE,
        )
    }
}

/** What has been visited and what is left, shown when a card is expanded. */
@Composable
private fun ItemLists(achievement: Achievement) {
    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (achievement.earned.isNotEmpty()) {
            ItemList(
                title = stringResource(R.string.achievement_earned),
                items = achievement.earned,
                icon = Icons.Rounded.CheckCircle,
                iconTint = VoyagePalette.buttonVisited,
            )
        }
        if (achievement.remaining.isNotEmpty()) {
            ItemList(
                title = stringResource(R.string.achievement_remaining),
                items = achievement.remaining,
                icon = Icons.Rounded.RadioButtonUnchecked,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemList(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconTint: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.achievement_section, title, items.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = items.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // iOS shows four lines of the list and stops; the medal overlay is
            // where a full accounting would belong, not a card in a scroll view.
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * Cards sit on the page, not in it.
 *
 * Material's default card container is `surfaceContainerLow`, which in this
 * theme is a shade of the same warm paper the page background is — the cards
 * disappeared into it. `surface` is the app's card color on both platforms:
 * white on the light page, the dark card grey on the dark one, exactly what
 * iOS's `AppColors.cardBackground` returns.
 */
@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

/** A soft shadow, standing in for iOS's `.shadow(radius: 8, y: 2)`. */
@Composable
private fun cardElevation() = CardDefaults.cardElevation(defaultElevation = 2.dp)

/** Green once earned, the app's orange while it is still in progress. */
private fun accentFor(achievement: Achievement): Color =
    if (achievement.isCompleted) VoyagePalette.buttonVisited else VoyagePalette.buttonColor

private val RING_SIZE = 56.dp

private val RING_STROKE = 4.dp

/** iOS draws the card's coin at 60% of its 56pt slot. */
private val COIN_SIZE = 34.dp

private const val COMPLETED_BORDER_ALPHA = 0.5f

private const val QUARTER_TURN = 90f

private const val HALF_TURN = 180f

private const val FULL_TURN = 360f
