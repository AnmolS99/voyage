package com.anmol.voyage.ui.country

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDetail
import com.anmol.voyage.data.capitalsLine
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.DisplayCapitalsCentre
import com.anmol.voyage.ui.theme.LocalVoyageFonts
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.displayCapitalsCentre
import com.anmol.voyage.ui.theme.isDarkNow

/**
 * Country details: flag, name, capital and continent, the visited/wishlist
 * toggles, and the two highlights checklists — the Android analogue of the iOS
 * `CountryExploreView`, as a Material 3 bottom sheet.
 *
 * The checklists write straight through [VoyageState], so a tick is saved the
 * moment it is made and is still there when the sheet is opened again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryDetailSheet(
    detail: CountryDetail,
    state: VoyageState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held here rather than as a defaulted parameter: a default argument is
    // evaluated at the call site, so an experimental one would force every
    // caller to opt in too.
    val sheetState = rememberModalBottomSheetState()
    val isDark = state.themeMode.isDarkNow()
    val art = rememberJourneyArt(detail.isoCode, isDark)
    val place = rememberJourneyPlace(detail.isoCode)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Fully open, the sheet stops below the status bar with the globe showing
        // above it, as iOS's sheet does, instead of running to the screen's top
        // and padding its own content down with a bare band. The content keeps
        // only the side insets: the column below pads for the navigation bar.
        modifier = modifier.statusBarsPadding(),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
        // With art, the handle is drawn over it so the art reaches the sheet's top.
        dragHandle = if (art == null) ({ BottomSheetDefaults.DragHandle() }) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (art != null) {
                Box {
                    JourneyHeader(
                        art = art,
                        flag = detail.flag,
                        countryName = detail.name,
                        place = place,
                        isDark = isDark,
                    )
                    BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // With art, the name and flag sit on it, and the capitals are badged in
                // the cities list — as on iOS.
                if (art == null) Header(detail)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisitedChip(state = state, country = detail.name)
                    WishlistChip(state = state, country = detail.name)
                }

                if (detail.cities.isNotEmpty()) {
                    ChecklistSection(
                        title = stringResource(R.string.country_cities),
                        icon = Icons.Rounded.LocationCity,
                        items = detail.cities,
                        isChecked = { state.isCityChecked(it, detail.name) },
                        onToggle = { state.toggleCheckedCity(it, detail.name) },
                        // iOS badges the capitals in the cities list; the two lists are
                        // built independently, so they are not always the first entries.
                        badgedItems = detail.capitals,
                    )
                }

                if (detail.attractions.isNotEmpty()) {
                    ChecklistSection(
                        title = stringResource(R.string.country_attractions),
                        icon = Icons.Rounded.Star,
                        items = detail.attractions,
                        isChecked = { state.isAttractionChecked(it, detail.name) },
                        onToggle = { state.toggleCheckedAttraction(it, detail.name) },
                    )
                }

                if (!detail.hasHighlights) {
                    Text(
                        text = stringResource(R.string.country_no_highlights),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(detail: CountryDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlagText(flag = detail.flag, country = detail.name, fontSize = 44.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineSmall,
            )
            subtitleOf(detail)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Paris · Europe" ("La Paz, Sucre · South America"), or whichever half exists. */
@Composable
private fun subtitleOf(detail: CountryDetail): String? {
    val capital = capitalsLine(detail.capitals)
    val continent = detail.continent
    return when {
        capital != null && continent != null ->
            stringResource(R.string.country_subtitle, capital, continent)

        capital != null -> stringResource(R.string.country_capital, capital)
        else -> continent
    }
}

/**
 * One highlights list: a header with its progress, then the items as checkable
 * rows on a card.
 */
@Composable
private fun ChecklistSection(
    title: String,
    icon: ImageVector,
    items: List<String>,
    isChecked: (String) -> Boolean,
    onToggle: (String) -> Unit,
    badgedItems: List<String> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The icon centres on the title's capitals, not its line box, and the
        // count sits on the title's baseline — as on iOS.
        val titleSize = MaterialTheme.typography.titleMedium.fontSize
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .alignBy { it.measuredHeight / 2 },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = LocalVoyageFonts.current.display,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .alignBy(DisplayCapitalsCentre)
                    .displayCapitalsCentre(titleSize),
            )
            Text(
                text = stringResource(
                    R.string.country_checked_count,
                    items.count(isChecked),
                    items.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                // The title's capitals' centre, measured from this text's own
                // baseline, puts the two on one baseline.
                modifier = Modifier
                    .alignBy(DisplayCapitalsCentre)
                    .displayCapitalsCentre(titleSize),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            // A step up from the sheet's own container, which is
            // `surfaceContainerLow` — otherwise the card would vanish into it.
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    ChecklistRow(
                        item = item,
                        checked = isChecked(item),
                        isBadged = item in badgedItems,
                        onToggle = { onToggle(item) },
                    )
                    if (index < items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    item: String,
    checked: Boolean,
    isBadged: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item) },
        leadingContent = {
            Checkbox(
                checked = checked,
                // Null hands the click to the row, so the whole line toggles and
                // screen readers announce one target instead of two.
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = VoyagePalette.buttonVisited,
                    checkmarkColor = Color.White,
                ),
            )
        },
        trailingContent = if (isBadged) {
            {
                Icon(
                    imageVector = Icons.Rounded.AccountBalance,
                    contentDescription = stringResource(R.string.country_capital_badge),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
    )
}
