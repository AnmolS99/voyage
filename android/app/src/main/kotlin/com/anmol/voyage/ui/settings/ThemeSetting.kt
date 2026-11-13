package com.anmol.voyage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.Daylight
import com.anmol.voyage.state.ThemeMode
import com.anmol.voyage.ui.theme.VoyageDarkColorScheme
import com.anmol.voyage.ui.theme.VoyageLightColorScheme
import com.anmol.voyage.ui.theme.VoyagePalette
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The theme picker: one small preview of the app per mode, ringed in the app's
 * orange when chosen — iOS's `ThemeOption` row. System and Daylight are half
 * light and half dark; Daylight swaps the preview's text for a sun and a moon,
 * and says when it turns over while it is chosen.
 */
@Composable
internal fun ThemeSetting(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Contrast,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ThemeOption(
                    mode = mode,
                    isSelected = mode == selected,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (selected == ThemeMode.Daylight) DaylightTimes()
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .testTag(themeOptionTag(mode)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .border(2.dp, if (isSelected) VoyagePalette.buttonColor else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(PreviewShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape),
        ) {
            val isSplit = mode == ThemeMode.System || mode == ThemeMode.Daylight
            val showsSun = mode == ThemeMode.Daylight
            if (mode != ThemeMode.Dark) ThemeSwatch(isDark = false, showsSun = showsSun)
            if (mode != ThemeMode.Light) {
                ThemeSwatch(
                    isDark = true,
                    showsSun = showsSun,
                    modifier = if (isSplit) Modifier.clip(LowerRightHalf) else Modifier,
                )
            }
        }
        Text(
            text = stringResource(mode.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The app in miniature in one theme: a card on the page with two lines of text
 * and an accent dot — or, for Daylight, a sun on the light one and a moon on the
 * dark one.
 */
@Composable
private fun ThemeSwatch(isDark: Boolean, showsSun: Boolean, modifier: Modifier = Modifier) {
    val scheme = if (isDark) VoyageDarkColorScheme else VoyageLightColorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(6.dp)
            .background(scheme.surface, RoundedCornerShape(5.dp))
            .padding(6.dp),
    ) {
        if (showsSun) {
            Icon(
                imageVector = if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
                modifier = Modifier.size(14.dp).align(if (isDark) Alignment.BottomEnd else Alignment.TopStart),
            )
        } else {
            Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(22.dp, 3.dp).background(scheme.onSurfaceVariant, CircleShape))
                Box(Modifier.size(14.dp, 3.dp).background(scheme.onSurfaceVariant, CircleShape))
            }
            Box(Modifier.size(8.dp).align(Alignment.BottomEnd).background(VoyagePalette.buttonColor, CircleShape))
        }
    }
}

/**
 * When Daylight turns light and dark today, and whose sun it follows — the
 * device's time zone and its city are all the app knows of where the user is.
 */
@Composable
private fun DaylightTimes() {
    val zone = ZoneId.systemDefault()
    val locations = CountryDataCache.shared.timeZoneLocations
    val times = remember(zone) { Daylight.sunTimes(Instant.now(), zone, locations) }
    val city = Daylight.cityName(zone, locations)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SunTime(Icons.Rounded.LightMode, R.string.theme_daylight_light_from, times.sunrise)
            SunTime(Icons.Rounded.DarkMode, R.string.theme_daylight_dark_from, times.sunset)
        }
        Text(
            text = if (city != null) {
                stringResource(R.string.theme_daylight_city, city)
            } else {
                stringResource(R.string.theme_daylight_clock)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A sun or moon and a time; read out as "Light from 08:10". */
@Composable
private fun SunTime(icon: ImageVector, description: Int, time: LocalTime?) {
    val text = time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        ?: stringResource(R.string.theme_daylight_no_time)
    val spoken = stringResource(description, text)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = VoyagePalette.buttonColor, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private val PreviewShape = RoundedCornerShape(10.dp)

/** The half of a preview below its diagonal, where a split one shows dark. */
private val LowerRightHalf = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
}
