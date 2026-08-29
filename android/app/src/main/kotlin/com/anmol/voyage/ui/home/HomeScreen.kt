package com.anmol.voyage.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountryDetail
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.state.ViewMode
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.country.CountryDetailSheet
import com.anmol.voyage.ui.country.CountrySearchSheet
import com.anmol.voyage.ui.country.CountrySelectionCard
import com.anmol.voyage.ui.globe.GlobeCountryFills
import com.anmol.voyage.ui.globe.GlobeDotStyle
import com.anmol.voyage.globe.GlobeGeometry
import com.anmol.voyage.globe.OutlineMesh
import com.anmol.voyage.globe.SelectedOutlineCache
import com.anmol.voyage.ui.globe.GlobeSurface
import com.anmol.voyage.ui.globe.rememberGlobeGeometry
import com.anmol.voyage.ui.map.CountryPaths
import com.anmol.voyage.ui.globe.GlobeCountryFills.toGlobeFill
import com.anmol.voyage.ui.map.CountryStyles
import com.anmol.voyage.ui.map.rememberMarkerSizes
import com.anmol.voyage.ui.map.MapProjection
import com.anmol.voyage.ui.map.WorldMap
import com.anmol.voyage.ui.map.buildCountryPaths
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Countries plus the lookups built from them, all prepared off the main thread. */
private class HomeData(
    val countries: List<GeoJsonCountry>,
    val hitTester: CountryHitTester,
)

/**
 * The Home tab: the world — as a 3D globe or a flat map — the current
 * selection, and the two sheets that reach a country.
 *
 * Both renderers share everything except the projection, which is the point of
 * the consistency rule in CLAUDE.md: the search button, the selection card, the
 * details sheet, and the tap → select → recolor loop are written once here, and
 * only the surface in the middle swaps.
 *
 * Parsing the GeoJSON, projecting its ~171k points, and triangulating the globe
 * all happen off the main thread, so the first frame is never blocked behind
 * them.
 */
@Composable
fun HomeScreen(state: VoyageState, modifier: Modifier = Modifier) {
    val cache = remember { CountryDataCache.shared }
    val data by produceState<HomeData?>(initialValue = null, cache) {
        value = withContext(Dispatchers.Default) {
            // Both are lazy: `countries` parses on first touch (usually already
            // warm from the prewarm thread), `hitTester` builds its bounding boxes.
            HomeData(cache.countries, cache.hitTester)
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
        val loaded = data
        val isGlobe = state.viewMode == ViewMode.Globe

        if (isGlobe) {
            GlobeBody(data = loaded, state = state)
        } else {
            MapBody(data = loaded, state = state, width = maxWidth, height = maxHeight)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = { state.toggleViewMode() }, enabled = loaded != null) {
                Icon(
                    imageVector = if (isGlobe) Icons.Rounded.Map else Icons.Rounded.Public,
                    contentDescription = stringResource(
                        if (isGlobe) R.string.home_show_map else R.string.home_show_globe,
                    ),
                )
            }
            FilledTonalIconButton(onClick = { showingSearch = true }, enabled = loaded != null) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.map_search_countries),
                )
            }
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

/** The 3D globe, or a spinner while its geometry is being triangulated. */
@Composable
private fun BoxScope.GlobeBody(data: HomeData?, state: VoyageState) {
    val background = MaterialTheme.colorScheme.background
    val geometry: GlobeGeometry? = rememberGlobeGeometry(data?.countries)
    val hitTester = data?.hitTester

    // The selected country's border is its own mesh, built off the main thread:
    // outlining Russia is tens of thousands of points and would drop a frame on
    // the tap that selected it.
    val selectedName = state.selectedCountry
    val selectedOutline by produceState<OutlineMesh?>(null, selectedName, data) {
        val name = selectedName
        val countries = data?.countries
        value = if (name == null || countries == null) {
            null
        } else {
            withContext(Dispatchers.Default) { SelectedOutlineCache.of(countries, name) }
        }
    }

    if (geometry == null || hitTester == null) {
        HomeLoading()
        return
    }

    // Microstates have no shape to fill, so the globe marks them the way the map
    // does — a dot in their status colors. Only the colors are resolved here;
    // the dots themselves are meshes built once with the rest of the geometry.
    val density = LocalDensity.current
    val dotStyles = geometry.microstateDots.map { dot ->
        val style = CountryStyles.of(
            isVisited = state.isVisited(dot.name),
            isWishlist = state.isInWishlist(dot.name),
            isSelected = selectedName == dot.name,
        )
        GlobeDotStyle(
            name = dot.name,
            fill = style.fill.toGlobeFill(),
            border = style.border.toGlobeFill(),
            borderWidthPx = with(density) { style.borderWidth.toPx() },
        )
    }

    GlobeSurface(
        ocean = geometry.ocean,
        countries = geometry.countries,
        outlineSectors = geometry.outlineSectors,
        microstateDots = geometry.microstateDots,
        colorFor = { name ->
            GlobeCountryFills.of(
                isVisited = state.isVisited(name),
                isWishlist = state.isInWishlist(name),
                isSelected = state.selectedCountry == name,
            )
        },
        oceanColor = VoyagePalette.ocean,
        backgroundColor = background,
        hitTester = hitTester,
        onCountryTapped = { name ->
            if (name == null) state.clearSelection() else state.selectCountry(name, hitTester.center(name))
        },
        modifier = Modifier.fillMaxSize(),
        dotStyles = dotStyles,
        capital = selectedName
            ?.let { name -> data.countries.firstOrNull { it.name == name } }
            ?.capital
            ?.let { LatLon(lat = it.lat, lon = it.lon) },
        selectedOutline = selectedOutline,
        selectedOutlineColor = selectedName?.let {
            GlobeCountryFills.selectedBorderOf(
                isVisited = state.isVisited(it),
                isWishlist = state.isInWishlist(it),
            )
        },
    )
}

/** The flat map, or a spinner while its paths are being projected. */
@Composable
private fun BoxScope.MapBody(data: HomeData?, state: VoyageState, width: Dp, height: Dp) {
    val density = LocalDensity.current
    val projection = remember(width, height, density) {
        with(density) { MapProjection(width.toPx(), height.toPx()) }
    }

    val paths by produceState(emptyList<CountryPaths>(), data, projection) {
        val countries = data?.countries ?: return@produceState
        value = withContext(Dispatchers.Default) { buildCountryPaths(countries, projection) }
    }

    if (data == null || paths.isEmpty()) {
        HomeLoading()
        return
    }

    WorldMap(
        countries = data.countries,
        paths = paths,
        hitTester = data.hitTester,
        state = state,
        projection = projection,
    )
}

/** Shown by whichever body is still preparing its geometry. */
@Composable
private fun BoxScope.HomeLoading() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}
