package com.anmol.voyage.ui.country

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryAlias
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountrySearchIndex
import com.anmol.voyage.data.FlagEmoji
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.capitalsLine
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.readableContentColor

/**
 * Find a country by name — the Android analogue of the iOS `CountryListView`
 * sheet, reached from the search button over the map.
 *
 * Every row can be marked visited or wishlisted without leaving the list, as on
 * iOS; tapping the row itself selects the country, which closes the sheet and
 * shows it on the map.
 *
 * Matching and ordering live in [CountrySearchIndex], which folds accents so
 * `Türkiye` is reachable from an English keyboard, and finds countries by their
 * other names too — "uk", "england", "burma". A country found only by one of
 * those shows it under its name, so it is clear why it is listed.
 *
 * Chips under the field narrow the list to the visited or wishlisted
 * countries, and a search then looks only among them — see [CountryListFilter].
 *
 * @param initialFilter what the list opens on: All from the dock's +, Visited
 *   from its count. Only All opens the keyboard: the others are for browsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySearchSheet(
    countries: List<GeoJsonCountry>,
    cache: CountryDataCache,
    state: VoyageState,
    onSelect: (GeoJsonCountry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: CountryListFilter = CountryListFilter.ALL,
) {
    // Held here rather than as a defaulted parameter: a default argument is
    // evaluated at the call site, so an experimental one would force every
    // caller to opt in too.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(countries, cache) { CountrySearchIndex.ofCountries(countries, cache) }
    val matches = remember(index, query) { index.search(query) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    // See CountryListFilter.includes.
    var kept by remember { mutableStateOf(initialFilter.members(state.visitedCountries, state.wishlistCountries)) }
    val choose = { chosen: CountryListFilter ->
        if (chosen != filter) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        filter = chosen
        kept = chosen.members(state.visitedCountries, state.wishlistCountries)
    }
    val visited = state.visitedCountries
    val wishlist = state.wishlistCountries
    val results = remember(matches, filter, visited, wishlist, kept) {
        matches.filter { filter.includes(it.item.name, visited, wishlist, kept.orEmpty()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            // The field is the point of the sheet, so open the keyboard with it —
            // unless it opened on a filter, to browse rather than to search.
            // Guarded because the request races the sheet's entry animation.
            if (initialFilter == CountryListFilter.ALL) runCatching { focusRequester.requestFocus() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.search_countries)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.search_clear_query),
                            )
                        }
                    }
                },
                // No IME action is set on purpose: there is nothing to submit, the
                // list filters as you type, and a single-line field already offers
                // "done", which puts the keyboard away and reveals more results.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
            )

            FilterChips(
                selection = filter,
                visitedCount = visited.size,
                wishlistCount = wishlist.size,
                onSelect = choose,
            )

            if (results.isEmpty()) {
                EmptyList(
                    filter = filter,
                    query = query.trim(),
                    onShowAll = { choose(CountryListFilter.ALL) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                ) {
                    items(results, key = { it.item.name }) { match ->
                        CountryRow(
                            country = match.item,
                            matchedAlias = match.matchedAlias,
                            state = state,
                            onSelect = { onSelect(match.item) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * All · Visited · Wishlist. Each chip carries its count and, as on the rows,
 * its status's icon and color: the visited check in green, the wishlist heart in
 * purple. The chosen one fills with that color, as [VisitedChip] does.
 */
@Composable
private fun FilterChips(
    selection: CountryListFilter,
    visitedCount: Int,
    wishlistCount: Int,
    onSelect: (CountryListFilter) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
    ) {
        ListFilterChip(
            label = stringResource(R.string.search_filter_all),
            icon = null,
            tint = VoyagePalette.buttonColor,
            selected = selection == CountryListFilter.ALL,
            onClick = { onSelect(CountryListFilter.ALL) },
        )
        ListFilterChip(
            label = stringResource(R.string.search_filter_visited, visitedCount),
            icon = Icons.Rounded.Check,
            tint = VoyagePalette.buttonVisited,
            selected = selection == CountryListFilter.VISITED,
            onClick = { onSelect(CountryListFilter.VISITED) },
        )
        ListFilterChip(
            label = stringResource(R.string.search_filter_wishlist, wishlistCount),
            icon = Icons.Rounded.Favorite,
            tint = VoyagePalette.wishlist,
            selected = selection == CountryListFilter.WISHLIST,
            onClick = { onSelect(CountryListFilter.WISHLIST) },
        )
    }
}

@Composable
private fun ListFilterChip(
    label: String,
    icon: ImageVector?,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val onTint = readableContentColor(tint)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            iconColor = tint,
            selectedContainerColor = tint,
            selectedLabelColor = onTint,
            selectedLeadingIconColor = onTint,
        ),
    )
}

/**
 * Nothing listed: a search that found nothing, or a filter with nothing in it
 * yet. Either way a filtered list offers the way back to every country.
 */
@Composable
private fun EmptyList(filter: CountryListFilter, query: String, onShowAll: () -> Unit) {
    val (title, body) = when {
        query.isNotEmpty() -> null to stringResource(
            when (filter) {
                CountryListFilter.ALL -> R.string.search_no_results
                CountryListFilter.VISITED -> R.string.search_no_visited_results
                CountryListFilter.WISHLIST -> R.string.search_no_wishlist_results
            },
            query,
        )
        filter == CountryListFilter.VISITED ->
            stringResource(R.string.search_no_visited_title) to stringResource(R.string.search_no_visited_body)
        else ->
            stringResource(R.string.search_no_wishlist_title) to stringResource(R.string.search_no_wishlist_body)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (filter != CountryListFilter.ALL) {
            TextButton(onClick = onShowAll, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(
                        if (query.isNotEmpty()) R.string.search_all_countries else R.string.search_show_all,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: GeoJsonCountry,
    matchedAlias: CountryAlias?,
    state: VoyageState,
    onSelect: () -> Unit,
) {
    val flag = remember(country) { FlagEmoji.of(country) }
    // The alias that found the country explains the match better than its capital.
    val supporting = matchedAlias?.let { matchedAliasLabel(it) }
        ?: capitalsLine(country.capitalNames)
    val wishlisted = state.isInWishlist(country.name)
    ListItem(
        headlineContent = { Text(country.name) },
        supportingContent = if (supporting == null) {
            null
        } else {
            { Text(supporting) }
        },
        leadingContent = { FlagText(flag = flag, country = country.name, fontSize = 28.sp) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusToggle(
                    icon = Icons.Rounded.Check,
                    checked = state.isVisited(country.name),
                    checkedTint = VoyagePalette.buttonVisited,
                    contentDescription = stringResource(
                        R.string.country_toggle_visited,
                        country.name,
                    ),
                    onToggle = { state.toggleVisited(country.name) },
                )
                StatusToggle(
                    icon = if (wishlisted) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    checked = wishlisted,
                    checkedTint = VoyagePalette.wishlist,
                    contentDescription = stringResource(
                        R.string.country_toggle_wishlist,
                        country.name,
                    ),
                    onToggle = { state.toggleWishlist(country.name) },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onSelect),
    )
}

/**
 * Why a search listed a country its name does not explain, worded by what the
 * alias is to it: "Also known as UK", "Formerly Burma", "Includes Scotland",
 * "Country code GB". Shared with the challenges' guess suggestions.
 */
@Composable
fun matchedAliasLabel(alias: CountryAlias): String = stringResource(
    when (alias.kind) {
        CountryAlias.Kind.NAME -> R.string.search_also_known_as
        CountryAlias.Kind.FORMER_NAME -> R.string.search_formerly
        CountryAlias.Kind.PART -> R.string.search_includes
        CountryAlias.Kind.CODE -> R.string.search_country_code
    },
    alias.text,
)

/**
 * A row's visited/wishlist toggle. [IconToggleButton] carries the checked state
 * into its semantics, so TalkBack announces the status rather than leaving it to
 * the icon's shape.
 */
@Composable
private fun StatusToggle(
    icon: ImageVector,
    checked: Boolean,
    checkedTint: Color,
    contentDescription: String,
    onToggle: () -> Unit,
) {
    IconToggleButton(checked = checked, onCheckedChange = { onToggle() }) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (checked) checkedTint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
