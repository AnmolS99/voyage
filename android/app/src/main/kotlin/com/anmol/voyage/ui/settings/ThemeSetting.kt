package com.anmol.voyage.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anmol.voyage.R
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.state.Daylight
import com.anmol.voyage.state.ThemeMode
import com.anmol.voyage.ui.theme.VoyageLightColorScheme
import com.anmol.voyage.ui.theme.VoyagePalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The theme picker: Home in miniature once per mode, ringed in the app's orange
 * when chosen, with what the chosen one does below — iOS's `ThemeOption` row.
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
        Text(
            text = selected.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        ThemePreview(
            mode = mode,
            modifier = Modifier
                .border(2.dp, if (isSelected) VoyagePalette.buttonColor else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(PreviewShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape),
        )
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
 * Home in miniature: the globe rising into the day's warm sky or the night's
 * starry one — iOS's `ThemePreview`, same proportions. System and Daylight are
 * day on the left and night on the right, so the globe carries a terminator;
 * Daylight adds the sun and the moon it follows.
 */
@Composable
private fun ThemePreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (mode != ThemeMode.Dark) drawScene(isDark = false, showsSun = mode == ThemeMode.Daylight)
        if (mode != ThemeMode.Light) {
            clipRect(left = if (mode == ThemeMode.Dark) 0f else size.width / 2) {
                drawScene(isDark = true, showsSun = mode == ThemeMode.Daylight)
            }
        }
    }
}

/** The whole scene in one theme. */
private fun DrawScope.drawScene(isDark: Boolean, showsSun: Boolean) {
    drawRect(if (isDark) Color.Black else VoyageLightColorScheme.background)
    if (isDark) {
        for ((x, y) in Stars) {
            drawCircle(Color.White, radius = 0.7.dp.toPx(), center = Offset(x * size.width, y * size.height), alpha = 0.8f)
        }
    }

    if (showsSun) {
        val radius = size.height * 0.11f
        val center = Offset(size.width * (if (isDark) 0.78f else 0.22f), size.height * 0.3f)
        drawCircle(if (isDark) VoyagePalette.capitalMarkerInset else VoyagePalette.buttonColor, radius, center)
        // The sky again, offset, leaves a crescent
        if (isDark) drawCircle(Color.Black, radius, center + Offset(radius * 0.5f, -radius * 0.3f))
    }

    val radius = size.width * 0.38f
    val globe = Rect(center = Offset(size.width / 2, size.height * 0.42f + radius), radius = radius)
    drawCircle(if (isDark) VoyagePalette.oceanDark else VoyagePalette.ocean, radius, globe.center)
    // A rim of daylight, so the dark ocean stands off the black sky
    if (isDark) drawCircle(VoyagePalette.ocean, radius, globe.center, style = Stroke(1.5.dp.toPx()))
    val land = Path()
    for (outline in Land) {
        outline.forEachIndexed { index, point ->
            val x = globe.left + point.x * globe.width
            val y = globe.top + point.y * globe.height
            if (index == 0) land.moveTo(x, y) else land.lineTo(x, y)
        }
        land.close()
    }
    clipPath(Path().apply { addOval(globe) }) {
        drawPath(land, VoyagePalette.land)
        // Stroked too, with round joins, to soften the corners
        drawPath(land, VoyagePalette.land, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** Stars, as fractions of the preview. */
private val Stars = listOf(
    0.08f to 0.22f, 0.20f to 0.50f, 0.32f to 0.14f, 0.45f to 0.36f,
    0.58f to 0.16f, 0.66f to 0.44f, 0.90f to 0.14f, 0.93f to 0.52f,
)

/**
 * Land as the app icon shows it — Africa under Europe — as outlines in
 * fractions of the globe's bounding square.
 */
private val Land = listOf(
    listOf(Offset(0.40f, 0.06f), Offset(0.52f, 0.03f), Offset(0.62f, 0.07f), Offset(0.60f, 0.13f), Offset(0.50f, 0.15f), Offset(0.44f, 0.17f), Offset(0.36f, 0.14f)), // Europe
    listOf(Offset(0.30f, 0.22f), Offset(0.46f, 0.20f), Offset(0.58f, 0.22f), Offset(0.66f, 0.34f), Offset(0.74f, 0.36f), Offset(0.64f, 0.44f), Offset(0.62f, 0.56f), Offset(0.56f, 0.66f), Offset(0.50f, 0.66f), Offset(0.46f, 0.52f), Offset(0.44f, 0.42f), Offset(0.34f, 0.42f), Offset(0.26f, 0.36f), Offset(0.24f, 0.28f)), // Africa
    listOf(Offset(0.66f, 0.22f), Offset(0.78f, 0.24f), Offset(0.80f, 0.33f), Offset(0.72f, 0.30f)), // Arabia
    listOf(Offset(0.04f, 0.40f), Offset(0.14f, 0.44f), Offset(0.16f, 0.56f), Offset(0.06f, 0.60f)), // South America's shoulder
)

/**
 * What the theme does, under the picker. Daylight says when it turns over today
 * and whose sun that is — the device's time zone and its city are all the app
 * knows of where the user is.
 */
@Composable
private fun ThemeMode.summary(): String {
    when (this) {
        ThemeMode.System -> return stringResource(R.string.theme_system_summary)
        ThemeMode.Light -> return stringResource(R.string.theme_light_summary)
        ThemeMode.Dark -> return stringResource(R.string.theme_dark_summary)
        ThemeMode.Daylight -> Unit
    }
    val zone = ZoneId.systemDefault()
    val locations = CountryDataCache.shared.timeZoneLocations
    val times = remember(zone) { Daylight.sunTimes(Instant.now(), zone, locations) }
    val city = Daylight.cityName(zone, locations)
    val place = if (city != null) stringResource(R.string.theme_daylight_sun_in, city) else stringResource(R.string.theme_daylight_clock)
    val sunrise = times.sunrise
    val sunset = times.sunset
    if (sunrise == null || sunset == null) return stringResource(R.string.theme_daylight_follows, place)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    return stringResource(R.string.theme_daylight_summary, sunrise.format(clock), sunset.format(clock), place)
}

private val PreviewShape = RoundedCornerShape(10.dp)
