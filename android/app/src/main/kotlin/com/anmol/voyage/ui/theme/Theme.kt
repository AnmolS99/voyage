package com.anmol.voyage.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 scheme built from Voyage's own palette. Only *chrome* colors live
 * here — country-status colors are read straight from [VoyagePalette] so they
 * stay identical to iOS regardless of theme or dynamic color.
 */
// Material's tonal container roles have no iOS counterpart, so they are derived
// from the palette here rather than added to it — the palette stays a 1:1 mirror
// of AppColors.
private val WarmTintLight = VoyagePalette.buttonColor.copy(alpha = 0.20f)
    .compositeOver(Color.White)
private val WarmTintDark = VoyagePalette.buttonColor.copy(alpha = 0.28f)
    .compositeOver(VoyagePalette.cardDark)

private val VoyageLightColorScheme = lightColorScheme(
    primary = VoyagePalette.buttonColor,
    onPrimary = Color.White,
    primaryContainer = WarmTintLight,
    onPrimaryContainer = VoyagePalette.textPrimaryLight,
    secondary = VoyagePalette.buttonVisited,
    onSecondary = Color.White,
    secondaryContainer = WarmTintLight,
    onSecondaryContainer = VoyagePalette.textPrimaryLight,
    tertiary = VoyagePalette.ocean,
    onTertiary = Color.White,
    tertiaryContainer = VoyagePalette.backgroundLightBottom,
    onTertiaryContainer = VoyagePalette.textPrimaryLight,
    background = VoyagePalette.pageBgLight,
    onBackground = VoyagePalette.textPrimaryLight,
    surface = Color.White,
    onSurface = VoyagePalette.textPrimaryLight,
    surfaceVariant = VoyagePalette.trackLight,
    onSurfaceVariant = VoyagePalette.textSecondaryLight,
    surfaceTint = VoyagePalette.buttonColor,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = VoyagePalette.backgroundLightTop,
    surfaceContainer = VoyagePalette.pageBgLight,
    surfaceContainerHigh = VoyagePalette.backgroundLightBottom,
    surfaceContainerHighest = VoyagePalette.trackLight,
    outline = VoyagePalette.textTertiaryLight,
    outlineVariant = VoyagePalette.trackLight,
    error = VoyagePalette.challengeWrong,
    onError = Color.White,
)

private val VoyageDarkColorScheme = darkColorScheme(
    primary = VoyagePalette.buttonColor,
    onPrimary = Color.White,
    primaryContainer = WarmTintDark,
    onPrimaryContainer = Color.White,
    secondary = VoyagePalette.buttonVisited,
    onSecondary = Color.White,
    secondaryContainer = WarmTintDark,
    onSecondaryContainer = Color.White,
    tertiary = VoyagePalette.ocean,
    onTertiary = Color.White,
    tertiaryContainer = VoyagePalette.cardDarkSecondary,
    onTertiaryContainer = Color.White,
    background = VoyagePalette.pageBgDark,
    onBackground = Color.White,
    surface = VoyagePalette.cardDark,
    onSurface = Color.White,
    surfaceVariant = VoyagePalette.trackDark,
    onSurfaceVariant = VoyagePalette.textSecondaryDark,
    surfaceTint = VoyagePalette.buttonColor,
    surfaceContainerLowest = VoyagePalette.pageBgDark,
    surfaceContainerLow = VoyagePalette.cardDarkSecondary,
    surfaceContainer = VoyagePalette.cardDarkSecondary,
    surfaceContainerHigh = VoyagePalette.cardDark,
    surfaceContainerHighest = VoyagePalette.trackDark,
    outline = VoyagePalette.textTertiaryDark,
    outlineVariant = VoyagePalette.trackDark,
    error = VoyagePalette.challengeWrong,
    onError = Color.White,
)

/**
 * App theme.
 *
 * @param darkTheme follows the system by default; the activity passes the
 *   user's system/light/dark preference (`VoyageState.themeMode`) instead.
 * @param dynamicColor opts into Material You wallpaper colors (Android 12+) for
 *   chrome only. Off by default so the app's warm identity — shared with iOS —
 *   is what users see out of the box.
 */
@Composable
fun VoyageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> VoyageDarkColorScheme
        else -> VoyageLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
