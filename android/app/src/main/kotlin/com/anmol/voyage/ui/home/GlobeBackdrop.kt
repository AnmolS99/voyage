package com.anmol.voyage.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** One star, positioned as a fraction of the backdrop so it survives a resize. */
private class Star(val x: Float, val y: Float, val diameterDp: Float, val alpha: Float)

/** iOS's `StarryBackground`: 150 white dots, 1–3 pt across, 30–100% opaque. */
private const val STAR_COUNT = 150

/**
 * Stars are placed from a fixed seed, so the sky is the same on every launch
 * and never reshuffles on recomposition — where iOS redraws random positions.
 */
private const val STAR_SEED = 1

/**
 * What sits behind the globe — a port of iOS's `GlobeBackdrop`: a starry sky
 * in dark mode, the page background in light mode.
 *
 * The globe's Filament view clears to transparent for exactly this, so the sky
 * stays fixed on screen while the globe turns in front of it, as it does on iOS.
 */
@Composable
internal fun GlobeBackdrop(isDark: Boolean, modifier: Modifier = Modifier) {
    if (!isDark) {
        Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        )
        return
    }
    val stars = remember {
        val random = Random(STAR_SEED)
        List(STAR_COUNT) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                diameterDp = 1f + 2f * random.nextFloat(),
                alpha = 0.3f + 0.7f * random.nextFloat(),
            )
        }
    }
    Canvas(modifier.fillMaxSize().background(Color.Black)) {
        for (star in stars) {
            drawCircle(
                color = Color.White,
                radius = star.diameterDp.dp.toPx() / 2f,
                center = Offset(star.x * size.width, star.y * size.height),
                alpha = star.alpha,
            )
        }
    }
}
