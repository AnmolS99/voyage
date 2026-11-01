package com.anmol.voyage.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Voyage's two typefaces — the Android counterpart of iOS's `AppFonts`
 * (`ios/voyage/Typography.swift`):
 * - **Newsreader** ([display]) — country names, titles, prompts, celebrations.
 * - **Schibsted Grotesk** ([text]) — buttons, labels, numbers and body copy.
 *
 * The font files live in `shared/data/fonts/` (SIL OFL), which the build
 * already packages as assets, so both apps ship the same bytes. Newsreader
 * ships Medium and SemiBold only; a bolder request resolves to SemiBold.
 */
@Immutable
data class VoyageFonts(val display: FontFamily, val text: FontFamily) {
    companion object {
        /**
         * Newsreader's cap height as a fraction of its size (OS/2 `sCapHeight`
         * 1340 of 2000 units), for centring things on its capitals rather than
         * its line box. iOS reads the same value from `UIFont.capHeight`.
         */
        const val DISPLAY_CAP_HEIGHT = 0.67f

        fun load(assets: AssetManager) = VoyageFonts(
            display = FontFamily(
                Font("fonts/Newsreader-Medium.ttf", assets, FontWeight.Medium),
                Font("fonts/Newsreader-SemiBold.ttf", assets, FontWeight.SemiBold),
            ),
            text = FontFamily(
                Font("fonts/SchibstedGrotesk-Regular.ttf", assets, FontWeight.Normal),
                Font("fonts/SchibstedGrotesk-Medium.ttf", assets, FontWeight.Medium),
                Font("fonts/SchibstedGrotesk-SemiBold.ttf", assets, FontWeight.SemiBold),
                Font("fonts/SchibstedGrotesk-Bold.ttf", assets, FontWeight.Bold),
            ),
        )
    }
}

/**
 * The middle of a line of Newsreader's capitals. A flag beside a country name
 * lines up with it (`Modifier.alignBy(DisplayCapitalsCentre)` on the name,
 * `alignBy { it.measuredHeight / 2 }` on the flag) rather than with the name's
 * line box, whose room below the baseline otherwise lifts the name — iOS
 * `centredOnDisplayCapitals`.
 */
val DisplayCapitalsCentre = HorizontalAlignmentLine(::min)

/** Provides [DisplayCapitalsCentre] for display text set at [fontSize]. */
fun Modifier.displayCapitalsCentre(fontSize: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val centre = placeable[FirstBaseline] - (fontSize.toPx() * VoyageFonts.DISPLAY_CAP_HEIGHT / 2).roundToInt()
    layout(placeable.width, placeable.height, mapOf(DisplayCapitalsCentre to centre)) {
        placeable.place(0, 0)
    }
}

/**
 * The fonts for the current theme. Text set in a Material style of the other
 * family — a number in a headline style, a country name in a title style —
 * passes `fontFamily = LocalVoyageFonts.current.text` (or `.display`).
 */
val LocalVoyageFonts = staticCompositionLocalOf<VoyageFonts> {
    error("VoyageFonts not provided — wrap content in VoyageTheme")
}

/**
 * Material's type scale in Voyage's families: display, headline and
 * `titleLarge` (screen and app-bar titles) are Newsreader; everything smaller
 * is Schibsted Grotesk. Sizes and line heights stay Material's.
 */
fun voyageTypography(fonts: VoyageFonts): Typography {
    val base = Typography()
    fun TextStyle.display(weight: FontWeight) = copy(fontFamily = fonts.display, fontWeight = weight)
    fun TextStyle.text() = copy(fontFamily = fonts.text)
    return Typography(
        displayLarge = base.displayLarge.display(FontWeight.Medium),
        displayMedium = base.displayMedium.display(FontWeight.Medium),
        displaySmall = base.displaySmall.display(FontWeight.Medium),
        headlineLarge = base.headlineLarge.display(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.display(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.display(FontWeight.SemiBold),
        titleLarge = base.titleLarge.display(FontWeight.SemiBold),
        titleMedium = base.titleMedium.text(),
        titleSmall = base.titleSmall.text(),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(),
        labelMedium = base.labelMedium.text(),
        labelSmall = base.labelSmall.text(),
    )
}
