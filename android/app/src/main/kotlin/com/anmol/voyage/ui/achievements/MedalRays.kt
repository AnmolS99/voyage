package com.anmol.voyage.ui.achievements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Beams of light fanning out from behind an earned medal in its own color,
 * slowly turning — iOS's `MedalRays`, with the same count, width, fade and
 * pace. Drawn behind the coin and sized larger than it; the middle, where every
 * beam meets, is what the coin covers.
 */
@Composable
internal fun MedalRays(color: Color, modifier: Modifier = Modifier) {
    val turn = rememberInfiniteTransition(label = "medal-rays")
    val degrees by turn.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(RAY_TURN_MILLIS, easing = LinearEasing)),
        label = "medal-rays-angle",
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        // A soft halo first, so the beams rise out of light rather than a hard rim.
        drawCircle(
            brush = Brush.radialGradient(
                RAY_GLOW_START to color.copy(alpha = GLOW_ALPHA),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
        )

        val beams = Brush.radialGradient(
            RAY_FADE_START to color.copy(alpha = RAY_ALPHA),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        )
        val beam = Path().apply {
            moveTo(center.x, center.y)
            arcTo(
                Rect(center, radius),
                startAngleDegrees = -90f - RAY_WIDTH_DEGREES / 2f,
                sweepAngleDegrees = RAY_WIDTH_DEGREES,
                forceMoveTo = false,
            )
            close()
        }
        // Read here, in the draw phase, so the turning beams never recompose.
        rotate(degrees, pivot = center) {
            repeat(RAY_COUNT) { index ->
                rotate(index * FULL_TURN / RAY_COUNT, pivot = center) {
                    drawPath(beam, brush = beams)
                }
            }
        }
    }
}

/** How much larger than the coin the beams reach. iOS's `MedalRays.scale`. */
internal const val RAYS_SCALE = 1.8f

private const val RAY_COUNT = 12

private const val RAY_WIDTH_DEGREES = 10f

/** One turn of the beams, in milliseconds. */
private const val RAY_TURN_MILLIS = 40_000

/** Where, as a share of their reach, the beams begin to fade: the coin's edge. */
private const val RAY_FADE_START = 1f / RAYS_SCALE

private const val RAY_GLOW_START = 0.45f

private const val RAY_ALPHA = 0.55f

private const val GLOW_ALPHA = 0.35f

private const val FULL_TURN = 360f

