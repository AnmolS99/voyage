package com.anmol.voyage.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralized color palette for the app — the Android counterpart of the iOS
 * `AppColors` enum (`ios/voyage/ColorPalette.swift`).
 *
 * Every value here is the exact same color as its iOS twin (the iOS file stores
 * fractional sRGB components; the hex literals below are those components
 * scaled to 8 bits). Colors are defined once here and referenced throughout the
 * codebase — never hardcode a hex value elsewhere, and when a color changes on
 * one platform, change it on the other in the same PR.
 *
 * Country-status colors (land / visited / wishlist / selected variants) are
 * *semantic*: they must never be replaced by Material You dynamic colors, which
 * apply to app chrome only. See [VoyageTheme].
 *
 * Note on ordering: colors that derive from another color are declared after it,
 * because properties of a Kotlin object initialize in declaration order.
 */
object VoyagePalette {

    // region UI button colors

    /** Primary button color — warm orange #D98C59 */
    val buttonColor = Color(0xFFD98C59)

    /** Visited/success button green */
    val buttonVisited = Color(0xFF4CB266)

    // endregion

    // region Map/globe colors

    /** Ocean blue — #2F86A6 */
    val ocean = Color(0xFF2F86A6)

    /** Land green (unvisited) — #34BE82 */
    val land = Color(0xFF34BE82)

    /** Land when selected (brighter green) */
    val landSelected = Color(0xFF73D999)

    /** Visited yellow — #F2F013 */
    val visited = Color(0xFFF2F013)

    /** Visited when selected (brighter yellow) */
    val visitedSelected = Color(0xFFFFFF4C)

    /** Wishlist purple */
    val wishlist = Color(0xFF9966CC)

    /** Wishlist when selected (brighter purple) */
    val wishlistSelected = Color(0xFFBF8CF2)

    /** Atmosphere glow (15% alpha, as on iOS) */
    val atmosphere = Color(0x2699CCFF)

    /** Ocean for the flat map view (slightly darker than the globe's) */
    val oceanMap = Color(0xFF31739B)

    /** Dark mode ocean */
    val oceanDark = Color(0xFF1A2640)

    /** Capital star marker — reuses the theme orange, as on iOS */
    val capitalMarker = buttonColor

    /** Thin rim stroked around the capital star to lift it off the country fill */
    val capitalMarkerOutline = Color(0xFF4C4C4C)

    // endregion

    // region Background colors

    /** Light mode warm gradient top */
    val backgroundLightTop = Color(0xFFFAF5ED)

    /** Light mode warm gradient bottom */
    val backgroundLightBottom = Color(0xFFF2E8DE)

    /** Dark mode card/panel background */
    val cardDark = Color(0xFF333340)

    /** Dark mode secondary background */
    val cardDarkSecondary = Color(0xFF262633)

    /** Light mode track/divider */
    val trackLight = Color(0xFFE6E0D9)

    /** Dark mode track/divider */
    val trackDark = Color(0xFF40404C)

    /** Close button background (dark mode) */
    val closeButtonDark = Color(0xFF4C4C59)

    /** Close button background (light mode) */
    val closeButtonLight = Color(0xFFE6E6E6)

    /** Page background (dark mode) */
    val pageBgDark = Color(0xFF1A1A1F)

    /** Page background (light mode) */
    val pageBgLight = Color(0xFFF5F2ED)

    // endregion

    // region Text colors

    /** Primary text (light mode) — warm brown */
    val textPrimaryLight = Color(0xFF33261A)

    /** Secondary text (light mode) */
    val textSecondaryLight = Color(0xFF66594C)

    /** Tertiary text (light mode) */
    val textTertiaryLight = Color(0xFF807366)

    /** Muted text (light mode) */
    val textMutedLight = Color(0xFF998C80)

    /** Secondary text (dark mode) */
    val textSecondaryDark = Color(0xFFB2B2BF)

    /** Tertiary text (dark mode) */
    val textTertiaryDark = Color(0xFF9999A6)

    /** Muted text (dark mode) */
    val textMutedDark = Color(0xFF80808C)

    /** Close button text (light mode) */
    val closeButtonText = Color(0xFF4C4C4C)

    /** Badge text (dark mode) */
    val badgeTextDark = Color(0xFFCCCCD9)

    /** Badge text (light mode) */
    val badgeTextLight = Color(0xFF4C4033)

    // endregion

    // region Progress bar colors

    /** Progress gradient (dark mode) */
    val progressDarkStart = Color(0xFF8066CC)
    val progressDarkEnd = Color(0xFF9980E6)

    /** Progress gradient (light mode) */
    val progressLightStart = Color(0xFFD9804C)
    val progressLightEnd = Color(0xFFF29966)

    // endregion

    // region Challenge game colors

    /** Correct guess fill (deep green, distinct from unvisited land) */
    val challengeCorrect = Color(0xFF1A8C40)

    /** Missed country fill and wrong-guess/reveal banners */
    val challengeWrong = Color(0xFFD94033)

    // endregion

    // region Medal colors (achievement medals)

    /** Gold face gradient (unlocked medal) */
    val medalGoldCenter = Color(0xFFFFDE6B)
    val medalGoldEdge = Color(0xFFC78F26)
    val medalGoldRim = Color(0xFFA6731A)

    /** Silver face gradient (locked medal) */
    val medalSilverCenter = Color(0xFFD1D1D6)
    val medalSilverEdge = Color(0xFF8C8C94)
    val medalSilverRim = Color(0xFF73737A)

    // endregion

    // region Trophy colors (challenge trophies)

    /** Bronze trophy gradient */
    val trophyBronzeLight = Color(0xFFD98F4C)
    val trophyBronzeDark = Color(0xFF8C5426)

    /** Silver/gold trophy gradients reuse the medal palette */
    val trophySilverLight = medalSilverCenter
    val trophySilverDark = medalSilverEdge
    val trophyGoldLight = medalGoldCenter
    val trophyGoldDark = medalGoldEdge

    // endregion

    // region Dark-mode helpers (mirror the iOS helper functions)

    fun textPrimary(isDark: Boolean): Color = if (isDark) Color.White else textPrimaryLight

    fun textSecondary(isDark: Boolean): Color = if (isDark) textSecondaryDark else textSecondaryLight

    fun textTertiary(isDark: Boolean): Color = if (isDark) textTertiaryDark else textTertiaryLight

    fun textMuted(isDark: Boolean): Color = if (isDark) textMutedDark else textMutedLight

    fun track(isDark: Boolean): Color = if (isDark) trackDark else trackLight

    fun cardBackground(isDark: Boolean): Color = if (isDark) cardDark else Color.White

    fun pageBackground(isDark: Boolean): Color = if (isDark) pageBgDark else pageBgLight

    // endregion
}
