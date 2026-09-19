package com.anmol.voyage.ui.challenges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlin.random.Random

/**
 * A burst of confetti from the bottom edge — a port of iOS `ConfettiView`: the
 * same hundred particles, launch speeds, gravity and fade, in dp where iOS works
 * in points. It draws only and takes no touches.
 */
@Composable
internal fun Confetti(modifier: Modifier = Modifier) {
    val particles = remember { List(PARTICLE_COUNT) { ConfettiParticle.random() } }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < LIFETIME_SECONDS) {
            elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
        }
    }
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = elapsed
        val alpha = if (t < FADE_START_SECONDS) 1f else (1f - (t - FADE_START_SECONDS) / FADE_SECONDS).coerceAtLeast(0f)
        if (alpha <= 0f) return@Canvas
        for (particle in particles) {
            val x = particle.xFraction * size.width + particle.vx * density * t
            val y = size.height + particle.vy0 * density * t + 0.5f * GRAVITY * density * t * t
            if (y >= size.height + 20 * density) continue
            val w = particle.size * density
            translate(x, y) {
                rotate(particle.rotation0 + particle.rotationSpeed * t, pivot = Offset.Zero) {
                    val topLeft = Offset(-w / 2, -w * 0.3f)
                    val box = Size(w, w * 0.55f)
                    if (particle.isRect) {
                        drawRect(particle.color, topLeft, box, alpha = alpha)
                    } else {
                        drawOval(particle.color, topLeft, box, alpha = alpha)
                    }
                }
            }
        }
    }
}

private class ConfettiParticle(
    val xFraction: Float,
    /** Launch speed, dp per second; negative is up. */
    val vy0: Float,
    val vx: Float,
    val color: Color,
    val size: Float,
    val rotation0: Float,
    val rotationSpeed: Float,
    val isRect: Boolean,
) {
    companion object {
        fun random() = ConfettiParticle(
            xFraction = Random.nextFloat() * 0.8f + 0.1f,
            vy0 = -(500f + Random.nextFloat() * 400f),
            vx = Random.nextFloat() * 200f - 100f,
            color = VoyagePalette.confetti.random(),
            size = 7f + Random.nextFloat() * 6f,
            rotation0 = Random.nextFloat() * 360f,
            rotationSpeed = Random.nextFloat() * 800f - 400f,
            isRect = Random.nextBoolean(),
        )
    }
}

private const val PARTICLE_COUNT = 100

/** dp per second squared, iOS's 600 points. */
private const val GRAVITY = 600f
private const val FADE_START_SECONDS = 2.5f
private const val FADE_SECONDS = 0.5f
private const val LIFETIME_SECONDS = FADE_START_SECONDS + FADE_SECONDS
