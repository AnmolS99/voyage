package com.anmol.voyage.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.voyageCardColors
import com.anmol.voyage.ui.theme.voyageCardElevation

/**
 * The Settings tab — so far its Appearance section: which Earth texture the globe
 * and the flat map are drawn with, the two style pickers of iOS's `SettingsView`.
 *
 * Each is an exposed dropdown menu, Material's counterpart to iOS's menu picker,
 * so the list can grow as textures are added without the screen changing shape.
 * They are separate preferences, as on iOS, so the globe and the map can differ.
 * A choice goes straight to [VoyageState], which saves it, and Home decodes the
 * new texture the next time it is shown.
 */
@Composable
fun SettingsScreen(state: VoyageState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = voyageCardColors(),
            elevation = voyageCardElevation(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StyleSetting(
                    icon = Icons.Rounded.Public,
                    label = R.string.settings_globe_style,
                    selected = state.globeStyle,
                    onSelect = state::setGlobeStyle,
                    tag = GLOBE_STYLE_TAG,
                )
                StyleSetting(
                    icon = Icons.Rounded.Map,
                    label = R.string.settings_map_style,
                    selected = state.mapStyle,
                    onSelect = state::setMapStyle,
                    tag = MAP_STYLE_TAG,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_appearance_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * One texture preference: a read-only field showing the current style, which
 * opens a menu of every [GlobeStyle] with the current one ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSetting(
    icon: ImageVector,
    @StringRes label: Int,
    selected: GlobeStyle,
    onSelect: (GlobeStyle) -> Unit,
    tag: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(label)) },
            // Tinted with the app's orange, as iOS tints its settings icons.
            leadingIcon = { Icon(icon, contentDescription = null, tint = VoyagePalette.buttonColor) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(tag),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GlobeStyle.entries.forEach { style ->
                val isCurrent = style == selected
                DropdownMenuItem(
                    text = { Text(stringResource(style.labelRes)) },
                    onClick = {
                        onSelect(style)
                        expanded = false
                    },
                    trailingIcon = if (isCurrent) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier
                        .semantics { this.selected = isCurrent }
                        .testTag(styleOptionTag(tag, style)),
                )
            }
        }
    }
}

/** The style's name — iOS's `GlobeStyle.displayName`, as a translatable string. */
@get:StringRes
internal val GlobeStyle.labelRes: Int
    get() = when (this) {
        GlobeStyle.Stylized -> R.string.style_stylized
        GlobeStyle.Natural -> R.string.style_natural
        GlobeStyle.Realistic -> R.string.style_realistic
    }

/** Test tags for the two pickers' fields. */
internal const val GLOBE_STYLE_TAG = "settings_globe_style"
internal const val MAP_STYLE_TAG = "settings_map_style"

/** Test tag for one style's item in a picker's menu. */
internal fun styleOptionTag(pickerTag: String, style: GlobeStyle) = "$pickerTag:${style.name}"
