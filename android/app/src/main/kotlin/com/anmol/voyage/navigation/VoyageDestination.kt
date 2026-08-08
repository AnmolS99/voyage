package com.anmol.voyage.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import com.anmol.voyage.R

/**
 * Top-level destinations, one per bottom-bar item. Mirrors the five tabs of the
 * iOS `TabView` in `ios/voyage/ContentView.swift`, in the same order.
 *
 * [labelRes] is the bottom-bar label and has to stay short enough to fit five
 * items on a phone; [titleRes] is the screen's own title, which uses the same
 * wording as iOS.
 */
enum class VoyageDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    /** Placeholder copy; null once the destination has its real screen. */
    @param:StringRes val subtitleRes: Int? = null,
) {
    Home(
        route = "home",
        labelRes = R.string.destination_home,
        titleRes = R.string.destination_home,
        icon = Icons.Rounded.Public,
    ),
    Daily(
        route = "daily",
        labelRes = R.string.destination_daily,
        titleRes = R.string.destination_daily,
        icon = Icons.Rounded.CalendarMonth,
        subtitleRes = R.string.placeholder_daily,
    ),
    Challenges(
        route = "challenges",
        labelRes = R.string.destination_challenges,
        titleRes = R.string.destination_challenges,
        icon = Icons.Rounded.SportsEsports,
        subtitleRes = R.string.placeholder_challenges,
    ),
    Achievements(
        route = "achievements",
        labelRes = R.string.destination_achievements_short,
        titleRes = R.string.destination_achievements,
        icon = Icons.Rounded.EmojiEvents,
        subtitleRes = R.string.placeholder_achievements,
    ),
    Settings(
        route = "settings",
        labelRes = R.string.destination_settings,
        titleRes = R.string.destination_settings,
        icon = Icons.Rounded.Settings,
        subtitleRes = R.string.placeholder_settings,
    ),
    ;

    companion object {
        val start = Home
    }
}
