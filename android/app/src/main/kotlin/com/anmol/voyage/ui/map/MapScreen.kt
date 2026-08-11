package com.anmol.voyage.ui.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountryDetail
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.country.CountryDetailSheet
import com.anmol.voyage.ui.country.CountrySearchSheet
import com.anmol.voyage.ui.country.CountrySelectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Countries plus the lookups built from them, all prepared off the main thread. */
private class MapData(
    val countries: List<GeoJsonCountry>,
    val hitTester: CountryHitTester,
)

/**
 * The Home tab: the flat world map, the current selection, and the two sheets
 * that reach a country — search to find one, details to explore it.
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

    var showingSearch by rememberSaveable { mutableStateOf(false) }
    var showingDetails by rememberSaveable { mutableStateOf(false) }

    val selectedCountry = state.selectedCountry
    // Assembling a detail reads country_highlights.json the first time, so it is
    // built off the main thread and the card fills in when it lands.
    val detail by produceState<CountryDetail?>(initialValue = null, selectedCountry, cache) {
        val name = selectedCountry
        value = if (name == null) {
            null
        } else {
            withContext(Dispatchers.Default) { CountryDetail.of(cache, name) }
        }
    }

    LaunchedEffect(selectedCountry) {
        // Closing the card (or the map deselecting) takes the details sheet with it.
        if (selectedCountry == null) showingDetails = false
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

        FilledTonalIconButton(
            onClick = { showingSearch = true },
            enabled = loaded != null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.map_search_countries),
            )
        }

        selectedCountry?.let { name ->
            CountrySelectionCard(
                name = name,
                detail = detail,
                state = state,
                onOpenDetails = { showingDetails = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    val countries = data?.countries
    if (showingSearch && countries != null) {
        CountrySearchSheet(
            countries = countries,
            state = state,
            onSelect = { country ->
                // Same as a tap on the map: the center is what the globe (Phase 7)
                // flies its camera to.
                state.selectCountry(country.name, data?.hitTester?.center(country.name))
                showingSearch = false
            },
            onDismiss = { showingSearch = false },
        )
    }

    val shown = detail
    if (showingDetails && shown != null) {
        CountryDetailSheet(
            detail = shown,
            state = state,
            onDismiss = { showingDetails = false },
        )
    }
}
