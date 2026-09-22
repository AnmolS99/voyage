package com.anmol.voyage.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anmol.voyage.BuildConfig
import com.anmol.voyage.R
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.state.ThemeMode
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.readableWidth
import com.anmol.voyage.ui.theme.voyageCardColors
import com.anmol.voyage.ui.theme.voyageCardElevation

/**
 * The Settings tab — iOS's `SettingsView`, less its tip jar: Appearance, Data and
 * the version.
 *
 * Appearance adds what iOS keeps on Home: the theme. Home's sun/moon button
 * flips light and dark as iOS's does, but only an explicit choice here can hand
 * the decision back to the system — see [ThemeMode.toggled].
 *
 * The texture pickers are exposed dropdown menus, Material's counterpart to
 * iOS's menu picker, so the list can grow as textures are added without the
 * screen changing shape. They are separate preferences, as on iOS, so the globe
 * and the map can differ. Every choice goes straight to [VoyageState], which
 * saves it.
 */
@Composable
fun SettingsScreen(state: VoyageState, modifier: Modifier = Modifier) {
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .readableWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(
            title = R.string.settings_appearance,
            footer = R.string.settings_appearance_footer,
        ) {
            ThemeSetting(selected = state.themeMode, onSelect = state::setThemeMode)
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

        SettingsSection(title = R.string.settings_data, footer = R.string.settings_reset_footer) {
            // Material's destructive action: error-colored, and behind a dialog
            // that says what it will take, as iOS's confirmation dialog does.
            OutlinedButton(
                onClick = { confirmingReset = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag(RESET_TAG),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.settings_reset))
            }
        }

        SettingsSection(title = null, footer = R.string.settings_copyright) {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.resetAllData()
                        confirmingReset = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(RESET_CONFIRM_TAG),
                ) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

/**
 * One grouped card with an optional heading above it and a note below, the
 * shape of an iOS inset-grouped `Section` with its header and footer.
 */
@Composable
private fun SettingsSection(
    @StringRes title: Int?,
    @StringRes footer: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = voyageCardColors(),
            elevation = voyageCardElevation(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
        Text(
            text = stringResource(footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** System, light or dark, as a segmented button row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSetting(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Contrast,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    modifier = Modifier.testTag(themeOptionTag(mode)),
                ) {
                    Text(stringResource(mode.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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

/** The mode's name in the theme picker. */
@get:StringRes
internal val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

/** Test tags for the two pickers' fields. */
internal const val GLOBE_STYLE_TAG = "settings_globe_style"
internal const val MAP_STYLE_TAG = "settings_map_style"

/** Test tag for one style's item in a picker's menu. */
internal fun styleOptionTag(pickerTag: String, style: GlobeStyle) = "$pickerTag:${style.name}"

/** Test tag for one mode's segment in the theme picker. */
internal fun themeOptionTag(mode: ThemeMode) = "settings_theme:${mode.name}"

/** Test tags for the reset button and its dialog's confirmation. */
internal const val RESET_TAG = "settings_reset"
internal const val RESET_CONFIRM_TAG = "settings_reset_confirm"
