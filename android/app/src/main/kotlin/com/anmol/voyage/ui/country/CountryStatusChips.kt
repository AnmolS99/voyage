package com.anmol.voyage.ui.country

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.anmol.voyage.R
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The visited and wishlist toggles, shared by the selection card and the details
 * sheet so a country is marked the same way wherever it is reached.
 *
 * Selected chips take their color from [VoyagePalette] rather than the Material
 * scheme: green for visited and purple for wishlist are the same status colors
 * the map paints countries with, and they are what iOS shows on its capsule
 * buttons.
 */
@Composable
internal fun VisitedChip(state: VoyageState, country: String, modifier: Modifier = Modifier) {
    val selected = state.isVisited(country)
    StatusChip(
        label = stringResource(R.string.country_visited),
        icon = if (selected) Icons.Rounded.Check else Icons.Rounded.Add,
        selected = selected,
        selectedColor = VoyagePalette.buttonVisited,
        onClick = { state.toggleVisited(country) },
        modifier = modifier,
    )
}

@Composable
internal fun WishlistChip(state: VoyageState, country: String, modifier: Modifier = Modifier) {
    val selected = state.isInWishlist(country)
    StatusChip(
        label = stringResource(R.string.country_wishlist),
        icon = if (selected) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        selected = selected,
        selectedColor = VoyagePalette.wishlist,
        onClick = { state.toggleWishlist(country) },
        modifier = modifier,
    )
}

@Composable
private fun StatusChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColor,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        ),
        modifier = modifier,
    )
}
