package com.anmol.voyage.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WCAG 2's contrast ratio between two opaque colors, from 1 (identical) to 21
 * (black on white). Body text needs 4.5 to pass AA.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance())
    val darker = minOf(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Black or white, whichever reads better on [background] — for text and icons
 * on the palette's status colors, which iOS labels white whatever the color.
 * White on the visited green is 2.7:1, well under AA; black is 7.9:1.
 */
fun readableContentColor(background: Color): Color =
    if (contrastRatio(background, Color.Black) >= contrastRatio(background, Color.White)) {
        Color.Black
    } else {
        Color.White
    }
