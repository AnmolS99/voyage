package com.anmol.voyage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anmol.voyage.navigation.VoyageDestination
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.map.MapScreen
import com.anmol.voyage.ui.screens.PlaceholderScreen

/**
 * App shell: a Material 3 [NavigationBar] over a [NavHost], one entry per
 * top-level destination. Later phases replace the remaining placeholder bodies
 * with real screens.
 *
 * [VoyageState] is owned by the activity and passed in from there — it decides
 * the theme, which wraps this shell — and handed to every tab, so they share one
 * source of truth, the role `GlobeState` plays on iOS.
 */
@Composable
fun VoyageApp(state: VoyageState, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: VoyageDestination.start.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // Spelled out rather than left to the defaults, which colour the
                // selected label with the scheme's `secondary` — Voyage's success
                // green, not a chrome accent.
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VoyageDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        colors = itemColors,
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    // Tab switching keeps a single-entry back stack:
                                    // back from any tab returns to Home, then exits.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VoyageDestination.start.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            VoyageDestination.entries.forEach { destination ->
                composable(destination.route) {
                    val subtitleRes = destination.subtitleRes
                    when {
                        destination == VoyageDestination.Home -> MapScreen(state = state)
                        // Destinations a later phase still owns.
                        subtitleRes != null -> PlaceholderScreen(
                            title = stringResource(destination.titleRes),
                            subtitle = stringResource(subtitleRes),
                            icon = destination.icon,
                        )
                    }
                }
            }
        }
    }
}
