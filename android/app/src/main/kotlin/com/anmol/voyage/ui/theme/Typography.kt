package com.anmol.voyage.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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
