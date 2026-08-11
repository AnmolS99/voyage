package com.anmol.voyage.ui.country

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDetail
import com.anmol.voyage.state.VoyageState

/**
 * The card over the map showing what is selected — the Android counterpart of
 * the iOS `HomeView` bottom panel.
 *
 * It deliberately is not the details bottom sheet. A modal sheet would scrim the
 * map, hiding the very thing selecting a country changes: the thicker
 * status-colored border and the capital star. So the summary stays inline and
 * non-blocking, the map stays tappable, and "Details" opens the sheet with the
 * highlights checklists — the same split iOS makes between its panel and
 * `CountryExploreView`.
 *
 * @param detail null until the country's highlights have been read off the main
 *   thread; the name renders immediately either way.
 */
@Composable
fun CountrySelectionCard(
    name: String,
    detail: CountryDetail?,
    state: VoyageState,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (detail != null) {
                    FlagText(flag = detail.flag, country = detail.name, fontSize = 30.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    detail?.capital?.let { capital ->
                        Text(
                            text = stringResource(R.string.country_capital, capital),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = state::clearSelection) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.country_clear_selection),
                    )
                }
            }

            // Scrollable so the three chips stay reachable at the largest font
            // scales instead of being clipped.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VisitedChip(state = state, country = name)
                WishlistChip(state = state, country = name)
                AssistChip(
                    onClick = onOpenDetails,
                    label = { Text(stringResource(R.string.country_details)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}
