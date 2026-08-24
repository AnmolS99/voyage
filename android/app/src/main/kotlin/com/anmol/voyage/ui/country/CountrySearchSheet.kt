package com.anmol.voyage.ui.country

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountrySearchIndex
import com.anmol.voyage.data.FlagEmoji
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * Find a country by name — the Android analogue of the iOS `CountryListView`
 * sheet, reached from the search button over the map.
 *
 * Every row can be marked visited or wishlisted without leaving the list, as on
 * iOS; tapping the row itself selects the country, which closes the sheet and
 * shows it on the map.
 *
 * Matching and ordering live in [CountrySearchIndex], which folds accents so
 * `Türkiye` is reachable from an English keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySearchSheet(
    countries: List<GeoJsonCountry>,
    state: VoyageState,
    onSelect: (GeoJsonCountry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held here rather than as a defaulted parameter: a default argument is
    // evaluated at the call site, so an experimental one would force every
    // caller to opt in too.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(countries) { CountrySearchIndex.ofCountries(countries) }
    val results = remember(index, query) { index.search(query) }
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            // The field is the point of the sheet, so open the keyboard with it.
            // Guarded because the request races the sheet's entry animation.
            runCatching { focusRequester.requestFocus() }
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

            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_no_results, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                ) {
                    items(results, key = { it.name }) { country ->
                        CountryRow(
                            country = country,
                            state = state,
                            onSelect = { onSelect(country) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: GeoJsonCountry,
    state: VoyageState,
    onSelect: () -> Unit,
) {
    val flag = remember(country) { FlagEmoji.of(country) }
    val capital = country.capital?.name
    val wishlisted = state.isInWishlist(country.name)
    ListItem(
        headlineContent = { Text(country.name) },
        supportingContent = if (capital == null) {
            null
        } else {
            { Text(capital) }
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
