package com.anmol.voyage.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountryDetail
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.EarthTextureCache
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.globe.GlobeGeometry
import com.anmol.voyage.globe.OutlineMesh
import com.anmol.voyage.globe.SelectedOutlineCache
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.ViewMode
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.country.CountryDetailSheet
import com.anmol.voyage.ui.country.CountrySearchSheet
import com.anmol.voyage.ui.country.CountrySelectionCard
import com.anmol.voyage.ui.globe.GlobeCountryFills.toGlobeFill
import com.anmol.voyage.ui.globe.GlobeCountryFills.toGlobeFillOrNull
import com.anmol.voyage.ui.globe.GlobeDotStyle
import com.anmol.voyage.ui.globe.GlobeSurface
import com.anmol.voyage.ui.globe.GlobeSurfaceHost
import com.anmol.voyage.ui.globe.rememberGlobeGeometry
import com.anmol.voyage.ui.globe.rememberGlobeSurfaceHost
import com.anmol.voyage.ui.map.CountryPaths
import com.anmol.voyage.ui.map.MapProjection
import com.anmol.voyage.ui.map.WorldMap
import com.anmol.voyage.ui.map.WorldScene
import com.anmol.voyage.ui.map.buildCountryPaths
import com.anmol.voyage.ui.map.rememberMarkerSizes
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.readableWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Countries plus the lookups built from them, all prepared off the main thread. */
private class HomeData(
    val countries: List<GeoJsonCountry>,
    val hitTester: CountryHitTester,
)

/** A decoded Earth texture: [image] is null only if [style]'s could not be decoded. */
private class EarthTexture(val style: GlobeStyle, val image: Bitmap?)

/**
 * The Earth texture for [style], or null while it is still being decoded.
 *
 * Like the globe's geometry, a texture already in the process-wide cache comes
 * back on the first composition, so returning to Home never flashes a spinner.
 */
@Composable
private fun rememberEarthTexture(style: GlobeStyle): EarthTexture? {
    val cache = remember { EarthTextureCache.shared }
    val texture by produceState(cache.cached(style)?.let { EarthTexture(style, it) }, style, cache) {
        if (value?.style == style) return@produceState
        value = withContext(Dispatchers.IO) { EarthTexture(style, cache.get(style)) }
    }
    // A style change keeps the old value until the new one lands; never show it.
    return texture?.takeIf { it.style == style }
}

/**
 * The Home tab: the world — as a 3D globe or a flat map — the current
 * selection, and the two sheets that reach a country.
 *
 * Both renderers share everything except the projection, which is the point of
 * the consistency rule in CLAUDE.md: the search button, the selection card, the
 * details sheet, and the tap → select → recolor loop are written once here, and
 * only the surface in the middle swaps.
 *
 * Loading the countries, projecting their ~171k points, and loading the globe's
 * meshes all happen off the main thread, so the first frame is never blocked
 * behind them.
 *
 * This screen is composed once for the whole Activity and hidden rather than
 * removed when another tab is chosen — see [visible] and `VoyageApp`. That is
 * what gives the globe one Filament engine per Activity (the Android plan's
 * 7.11): the engine is remembered here, above the globe/map switch, and its
 * `TextureView` is never detached by navigation.
 *
 * @param visible whether this is the tab on screen. A hidden Home draws
 *   nothing, answers no touches and renders no frames; all it keeps is the
 *   globe's surface and everything uploaded to it.
 * @param challengeScene a challenge game's world, shown in place of the user's
 *   own while the game is played. Home's chrome steps aside for the game's, and
 *   the globe — the same one, on the same engine — paints the game instead:
 *   iOS's full-screen game over its own in-memory `GlobeState`, without a second
 *   Filament engine.
 */
@Composable
fun HomeScreen(
    state: VoyageState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    challengeScene: WorldScene? = null,
) {
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

    SelectionHaptics(selectedCountry)

    // The globe's engine, owned here rather than by the surface that draws with
    // it: this composition outlives both the flat map and every other tab, so
    // the engine survives a view-mode toggle and a tab switch alike.
    val globeHost = rememberGlobeSurfaceHost()

    BoxWithConstraints(
        // Hidden, not removed — an invisible globe is one whose surface is still
        // attached and whose 181 meshes are still on the GPU. The subtree below
        // stays composed and laid out for exactly that reason; what it must not
        // do is paint over the tab drawn underneath it.
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        val loaded = data
        val scene = challengeScene
            ?: loaded?.let { remember(state, it.hitTester) { HomeWorldScene(state, it.hitTester) } }
        val showsChrome = visible && challengeScene == null
        // A challenge is always played on the globe, as on iOS, whatever view
        // Home was left in.
        val isGlobe = challengeScene != null || state.viewMode == ViewMode.Globe
        val systemInDarkTheme = isSystemInDarkTheme()
        val isDark = state.themeMode.isDark(systemInDarkTheme)

        // Behind the globe only, as on iOS; the map paints its own ocean.
        if (isGlobe && visible) GlobeBackdrop(isDark = isDark)

        // Everything except the globe leaves composition while hidden: none of
        // it owns a surface worth keeping, and a pointer node left behind here
        // would catch taps meant for the screen underneath.
        val worldWidth = maxWidth
        val worldHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showsChrome) {
                        Modifier.worldSemantics(
                            description = worldDescription(isGlobe, visited = state.visitedCountries.size),
                            searchLabel = stringResource(R.string.map_search_countries),
                            onSearch = { showingSearch = true },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (isGlobe) {
                GlobeBody(data = loaded, scene = scene, state = state, host = globeHost, visible = visible)
            } else if (visible) {
                MapBody(data = loaded, scene = scene, state = state, width = worldWidth, height = worldHeight)
            }
        }

        if (showsChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // Home draws under the status bar; its buttons must not.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { state.toggleViewMode() },
                    enabled = loaded != null,
                ) {
                    Icon(
                        imageVector = if (isGlobe) Icons.Rounded.Map else Icons.Rounded.Public,
                        contentDescription = stringResource(
                            if (isGlobe) R.string.home_show_map else R.string.home_show_globe,
                        ),
                    )
                }
                // iOS's sun/moon button: shows where a tap goes, not where you are.
                FilledTonalIconButton(onClick = { state.toggleDarkMode(systemInDarkTheme) }) {
                    Icon(
                        imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = stringResource(
                            if (isDark) R.string.home_light_mode else R.string.home_dark_mode,
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

            // The card rises in with a selection and sinks away with it, so it
            // keeps showing the last country for as long as it is leaving.
            val shownName = rememberLastNonNull(selectedCountry)
            AnimatedVisibility(
                visible = selectedCountry != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .readableWidth(),
            ) {
                shownName?.let { name ->
                    CountrySelectionCard(
                        name = name,
                        // A leaving card keeps its detail only while it still
                        // matches; the next one's never flashes in the old card.
                        detail = detail?.takeIf { it.name == name },
                        state = state,
                        onOpenDetails = { showingDetails = true },
                    )
                }
            }
        }
    }

    val countries = data?.countries
    if (visible && challengeScene == null && showingSearch && countries != null) {
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
    if (visible && challengeScene == null && showingDetails && shown != null) {
        CountryDetailSheet(
            detail = shown,
            state = state,
            onDismiss = { showingDetails = false },
        )
    }
}

/** The 3D globe, or a spinner while its geometry loads. */
@Composable
private fun BoxScope.GlobeBody(
    data: HomeData?,
    scene: WorldScene?,
    state: VoyageState,
    host: GlobeSurfaceHost,
    visible: Boolean,
) {
    val geometry: GlobeGeometry? = rememberGlobeGeometry(data?.countries)
    val texture = rememberEarthTexture(state.globeStyle)
    val hitTester = data?.hitTester

    // The selected country's border is its own mesh, built off the main thread:
    // outlining Russia is tens of thousands of points and would drop a frame on
    // the tap that selected it.
    val selectedName = scene?.selectedCountry
    val selectedOutline by produceState<OutlineMesh?>(null, selectedName, data) {
        val name = selectedName
        val countries = data?.countries
        value = if (name == null || countries == null) {
            null
        } else {
            withContext(Dispatchers.Default) { SelectedOutlineCache.of(countries, name) }
        }
    }

    if (geometry == null || hitTester == null || texture == null || scene == null) {
        if (visible) HomeLoading()
        return
    }
    val hasTexture = texture.image != null

    // Microstates have no shape to fill, so the globe marks them the way the map
    // does — a dot in their status colors. Only the colors are resolved here;
    // the dots themselves are meshes built once with the rest of the geometry.
    val density = LocalDensity.current
    val dotStyles = geometry.microstateDots.map { dot ->
        val style = scene.styleFor(dot.name, hasTexture)
        GlobeDotStyle(
            name = dot.name,
            fill = style.fill.toGlobeFillOrNull(),
            border = style.border.toGlobeFill(),
            borderWidthPx = with(density) { style.borderWidth.toPx() },
        )
    }

    GlobeSurface(
        ocean = geometry.ocean,
        countries = geometry.countries,
        outlineSectors = geometry.outlineSectors,
        microstateDots = geometry.microstateDots,
        colorFor = { name -> scene.styleFor(name, hasTexture).fill.toGlobeFillOrNull() },
        earthTexture = texture.image,
        oceanColor = VoyagePalette.ocean,
        hitTester = hitTester,
        onCountryTapped = scene::onCountryTapped,
        modifier = Modifier.fillMaxSize(),
        host = host,
        visible = visible,
        focus = scene.cameraFocus,
        autoRotating = scene.isAutoRotating,
        onInteraction = scene::onInteraction,
        dotStyles = dotStyles,
        capital = selectedName
            ?.takeIf { scene.showsCapital }
            ?.let { name -> data.countries.firstOrNull { it.name == name } }
            ?.capital
            ?.let { LatLon(lat = it.lat, lon = it.lon) },
        selectedOutline = selectedOutline,
        // A texture only ever changes fills, so it does not come into the border.
        selectedOutlineColor = selectedName?.let { scene.styleFor(it, hasTexture = true).border.toGlobeFill() },
    )
}

/** The flat map, or a spinner while its paths are being projected. */
@Composable
private fun BoxScope.MapBody(data: HomeData?, scene: WorldScene?, state: VoyageState, width: Dp, height: Dp) {
    val density = LocalDensity.current
    val projection = remember(width, height, density) {
        with(density) { MapProjection(width.toPx(), height.toPx()) }
    }

    val paths by produceState(emptyList<CountryPaths>(), data, projection) {
        val countries = data?.countries ?: return@produceState
        value = withContext(Dispatchers.Default) { buildCountryPaths(countries, projection) }
    }

    val texture = rememberEarthTexture(state.mapStyle)

    if (data == null || scene == null || paths.isEmpty() || texture == null) {
        HomeLoading()
        return
    }

    val image = remember(texture.image) { texture.image?.asImageBitmap() }
    WorldMap(
        countries = data.countries,
        paths = paths,
        hitTester = data.hitTester,
        scene = scene,
        projection = projection,
        texture = image,
    )
}

/** Shown by whichever body is still preparing its geometry. */
@Composable
private fun BoxScope.HomeLoading() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

/**
 * [value], or while it is null the last value it was not — what an exit
 * animation keeps drawing. Held outside snapshot state: it only ever changes
 * alongside [value], so it never needs to trigger a recomposition of its own.
 */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

/**
 * A tick each time a country is selected, the Android voice of iOS's
 * `UISelectionFeedbackGenerator` — the same one a challenge's first tap on a
 * country uses. Remembered across a rotation, so coming back to a selection
 * that was already made is silent.
 */
@Composable
private fun SelectionHaptics(selectedCountry: String?) {
    val haptics = LocalHapticFeedback.current
    var ticked by rememberSaveable { mutableStateOf(selectedCountry) }
    LaunchedEffect(selectedCountry) {
        if (selectedCountry != null && selectedCountry != ticked) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        ticked = selectedCountry
    }
}

/**
 * What TalkBack says for the globe or the map: which one it is and how much of
 * the world is marked visited.
 */
@Composable
private fun worldDescription(isGlobe: Boolean, visited: Int): String {
    val view = stringResource(if (isGlobe) R.string.home_globe_description else R.string.home_map_description)
    return "$view. ${pluralStringResource(R.plurals.home_visited_count, visited, visited)}"
}

/**
 * The world's accessibility node: [description], with Search offered as an
 * action.
 *
 * A country cannot be found by touch without sight — they are painted, not laid
 * out — so search is the accessible way to select one, and the selection card
 * that answers it announces itself.
 */
private fun Modifier.worldSemantics(description: String, searchLabel: String, onSearch: () -> Unit): Modifier =
    semantics {
        contentDescription = description
        customActions = listOf(CustomAccessibilityAction(searchLabel) { onSearch(); true })
    }
