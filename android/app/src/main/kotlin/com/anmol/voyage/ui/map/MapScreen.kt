package com.anmol.voyage.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.state.VoyageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Countries plus the lookups built from them, all prepared off the main thread. */
private class MapData(
    val countries: List<GeoJsonCountry>,
    val hitTester: CountryHitTester,
)

/**
 * The Home tab: the flat world map plus the current selection.
 *
 * Parsing the GeoJSON and projecting its ~171k points both happen off the main
 * thread, so the first frame is never blocked behind them; the map appears as
 * soon as its geometry is ready.
 */
@Composable
fun MapScreen(state: VoyageState, modifier: Modifier = Modifier) {
    val cache = remember { CountryDataCache.shared }
    val data by produceState<MapData?>(initialValue = null, cache) {
        value = withContext(Dispatchers.Default) {
            // Both are lazy: `countries` parses on first touch (usually already
            // warm from the prewarm thread), `hitTester` builds its bounding boxes.
            MapData(cache.countries, cache.hitTester)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val projection = remember(maxWidth, maxHeight, density) {
            with(density) { MapProjection(maxWidth.toPx(), maxHeight.toPx()) }
        }

        val loaded = data
        val paths by produceState(emptyList<CountryPaths>(), loaded, projection) {
            val countries = loaded?.countries ?: return@produceState
            value = withContext(Dispatchers.Default) { buildCountryPaths(countries, projection) }
        }

        if (loaded != null) {
            WorldMap(
                countries = loaded.countries,
                paths = paths,
                hitTester = loaded.hitTester,
                state = state,
                projection = projection,
            )
        }

        if (paths.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.selectedCountry?.let { name ->
            SelectionCard(
                name = name,
                country = loaded?.countries?.firstOrNull { it.name == name },
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * Interim selection readout with visited/wishlist toggles.
 *
 * Phase 6 replaces this with the real country-details bottom sheet (flag,
 * highlights checklists, search). It exists now because the visited, wishlist,
 * and both-lists colors are otherwise unreachable from the UI.
 */
@Composable
private fun SelectionCard(
    name: String,
    country: GeoJsonCountry?,
    state: VoyageState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                country?.capital?.let { capital ->
                    Text(
                        text = stringResource(R.string.map_capital, capital.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(
                        label = stringResource(R.string.map_visited),
                        icon = Icons.Rounded.Check,
                        selected = state.isVisited(name),
                        onClick = { state.toggleVisited(name) },
                    )
                    ToggleChip(
                        label = stringResource(R.string.map_wishlist),
                        icon = Icons.Rounded.Favorite,
                        selected = state.isInWishlist(name),
                        onClick = { state.toggleWishlist(name) },
                    )
                }
            }
            IconButton(onClick = state::clearSelection) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.map_clear_selection),
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}
