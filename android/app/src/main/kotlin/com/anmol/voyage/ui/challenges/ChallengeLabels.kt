package com.anmol.voyage.ui.challenges

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.anmol.voyage.R
import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ChallengeRegion
import com.anmol.voyage.challenges.ChallengeTrophy
import com.anmol.voyage.ui.theme.VoyagePalette

@get:StringRes
internal val ChallengeGameMode.titleRes: Int
    get() = when (this) {
        ChallengeGameMode.ClickCountry -> R.string.challenge_mode_click_country
        ChallengeGameMode.NameCapital -> R.string.challenge_mode_name_capital
        ChallengeGameMode.NameFlag -> R.string.challenge_mode_name_flag
    }

@get:StringRes
internal val ChallengeGameMode.subtitleRes: Int
    get() = when (this) {
        ChallengeGameMode.ClickCountry -> R.string.challenge_mode_click_country_subtitle
        ChallengeGameMode.NameCapital -> R.string.challenge_mode_name_capital_subtitle
        ChallengeGameMode.NameFlag -> R.string.challenge_mode_name_flag_subtitle
    }

/** iOS's SF Symbols `scope`, `building.columns` and `flag.fill`, in Material. */
internal val ChallengeGameMode.icon: ImageVector
    get() = when (this) {
        ChallengeGameMode.ClickCountry -> Icons.Rounded.GpsFixed
        ChallengeGameMode.NameCapital -> Icons.Rounded.AccountBalance
        ChallengeGameMode.NameFlag -> Icons.Rounded.Flag
    }

/** The continent's own name, which is data rather than copy; "World" is copy. */
@Composable
internal fun ChallengeRegion.displayName(): String =
    continent?.displayName ?: stringResource(R.string.challenge_region_world)

@Composable
internal fun ChallengeTrophy.displayName(): String = stringResource(
    when (this) {
        ChallengeTrophy.Bronze -> R.string.challenge_trophy_bronze
        ChallengeTrophy.Silver -> R.string.challenge_trophy_silver
        ChallengeTrophy.Gold -> R.string.challenge_trophy_gold
    },
)

/** Light and dark ends of the tier's gradient, as iOS's `ChallengeTrophy.gradient`. */
private val ChallengeTrophy.colors: Pair<Color, Color>
    get() = when (this) {
        ChallengeTrophy.Bronze -> VoyagePalette.trophyBronzeLight to VoyagePalette.trophyBronzeDark
        ChallengeTrophy.Silver -> VoyagePalette.trophySilverLight to VoyagePalette.trophySilverDark
        ChallengeTrophy.Gold -> VoyagePalette.trophyGoldLight to VoyagePalette.trophyGoldDark
    }

/**
 * A trophy in its tier's gradient, top-leading to bottom-trailing: filled once
 * [earned], an outline at half strength before — always tier-colored, so bronze,
 * silver and gold stay readable either way, as on iOS.
 */
@Composable
internal fun TrophyIcon(
    trophy: ChallengeTrophy,
    earned: Boolean,
    modifier: Modifier = Modifier,
    unearnedAlpha: Float = 0.5f,
) {
    val (light, dark) = trophy.colors
    Icon(
        imageVector = if (earned) Icons.Rounded.EmojiEvents else Icons.Outlined.EmojiEvents,
        contentDescription = null,
        modifier = modifier
            // Offscreen, so the gradient is masked to the glyph alone rather than
            // painted over whatever is behind it.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = if (earned) 1f else unearnedAlpha
            }
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(light, dark),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcIn)
                }
            },
    )
}
