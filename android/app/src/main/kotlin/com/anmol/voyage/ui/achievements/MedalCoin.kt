package com.anmol.voyage.ui.achievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * An achievement medal: a gold coin struck with the achievement's emoji, or a
 * silver one with the emoji drained of color while the achievement is locked.
 *
 * It is drawn flat rather than by a 3D engine, but the shape it draws is a
 * *cylinder in projection*, not a disc. [rotationDegrees] turns it about its
 * vertical axis: the face narrows with the cosine of the angle while the rim
 * band it uncovers widens with the sine, which is what gives a spinning coin its
 * thickness. iOS gets the same silhouette out of an
 * `SCNCylinder(radius: 1.1, height: 0.12)` — [THICKNESS_FRACTION] is that ratio,
 * so the two coins are equally thick for their size.
 *
 * Past a quarter turn it is the coin's reverse that faces the viewer, struck
 * with a star as iOS's back cap is.
 *
 * @param size the coin's diameter. Passed rather than measured so the emoji,
 *   which is text and not a drawing, can be sized from it.
 * @param rotationDegrees how far the coin has turned about its vertical axis.
 *   A function, not a value, so that a coin being spun re-reads it in the draw
 *   phase instead of recomposing every frame — the reason the globe keeps its
 *   camera out of Compose state. Face-on, showing no rim, for every coin but
 *   the spinnable one in the overlay.
 */
@Composable
fun MedalCoin(
    medal: String,
    isEarned: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    rotationDegrees: () -> Float = FACE_ON,
) {
    val diameterPx = with(LocalDensity.current) { size.toPx() }
    val symbolAlpha = if (isEarned) 1f else LOCKED_SYMBOL_ALPHA

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCoin(isEarned, rotationDegrees(), symbolAlpha)
        }

        val symbolSize = with(LocalDensity.current) { (size * SYMBOL_FRACTION).toSp() }

        // The emoji is the one part of the coin that has to be text — no path
        // can stand in for a color glyph. It is always composed and drawn at
        // zero alpha when the coin's back is forward, rather than being chosen
        // between here: which face shows is a function of the angle, and
        // picking it in composition would drag the spin out of the draw phase.
        // The star on the reverse is struck by the Canvas above, where it can
        // be centered exactly.
        Text(
            text = medal,
            fontSize = symbolSize,
            modifier = Modifier
                .graphicsLayer {
                    val turn = Turn(rotationDegrees(), diameterPx)
                    alpha = if (turn.isFrontForward) symbolAlpha else 0f
                    scaleX = turn.faceSqueeze
                    translationX = turn.faceOffset
                }
                // Emoji carry color of their own, which no tint can override — a
                // locked medal drains it through a saturation filter instead, as
                // iOS's `.saturation(0)` does.
                .then(if (isEarned) Modifier else Modifier.grayscale()),
        )

    }
}

/**
 * Where a coin's visible face has been carried by a turn of [rotationDegrees].
 *
 * The two faces sit half a thickness either side of the axis, so turning the
 * coin slides the near one sideways and swings the far one out of sight behind
 * the rim. Everything the drawing needs follows from that.
 */
private class Turn(rotationDegrees: Float, diameterPx: Float) {

    private val radians = Math.toRadians(rotationDegrees.toDouble())
    private val cosine = cos(radians).toFloat()
    private val sine = sin(radians).toFloat()

    /** Whether the struck face, rather than the reverse, is the one on show. */
    val isFrontForward: Boolean = cosine >= 0f

    /** How much of its full width the visible face still spans. */
    val faceSqueeze: Float = abs(cosine)

    /** Half the width of the rim band the turn has uncovered. */
    val edgeHalfWidth: Float = diameterPx * THICKNESS_FRACTION / 2f * abs(sine)

    /**
     * How far the visible face has slid from the coin's center. The same
     * distance as [edgeHalfWidth] — the face is one end of the band — but
     * signed, since which way it slides is what tells the two faces apart.
     */
    val faceOffset: Float =
        diameterPx * THICKNESS_FRACTION / 2f * sine * (if (isFrontForward) 1f else -1f)
}

/** The metal itself: the rim band, then the visible face struck on top of it. */
private fun DrawScope.drawCoin(isEarned: Boolean, rotationDegrees: Float, symbolAlpha: Float) {
    val diameter = size.minDimension
    val radius = diameter / 2f
    val turn = Turn(rotationDegrees, diameter)
    val faceHalfWidth = radius * turn.faceSqueeze
    val faceCenter = Offset(center.x + turn.faceOffset, center.y)

    val rimColor = if (isEarned) VoyagePalette.medalGoldRim else VoyagePalette.medalSilverRim
    val edgeColor = if (isEarned) VoyagePalette.medalGoldEdge else VoyagePalette.medalSilverEdge
    val centerColor =
        if (isEarned) VoyagePalette.medalGoldCenter else VoyagePalette.medalSilverCenter

    // The whole silhouette — a band capped by a half ellipse at each end — is
    // filled as rim first; the face then covers all of it but the crescent that
    // is genuinely the coin's edge. Face-on the band has no width and the
    // silhouette is exactly the face's circle, so a still coin pays nothing for
    // this and looks no different.
    drawPath(
        path = coinSilhouette(center, turn.edgeHalfWidth, faceHalfWidth, radius),
        // Stands in for the specular highlight iOS gets from a blinn material
        // and a key light: brightest across the middle, falling off to the
        // silhouette on either side.
        brush = Brush.horizontalGradient(
            colors = listOf(rimColor, edgeColor, rimColor),
            startX = center.x - (turn.edgeHalfWidth + faceHalfWidth),
            endX = center.x + (turn.edgeHalfWidth + faceHalfWidth),
        ),
    )

    // Edge-on there is no face left to strike, and a zero scale is degenerate.
    if (faceHalfWidth < HAIRLINE) return

    scale(scaleX = turn.faceSqueeze, scaleY = 1f, pivot = faceCenter) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(centerColor, edgeColor),
                center = faceCenter,
                radius = radius,
            ),
            radius = radius,
            center = faceCenter,
        )
        drawCircle(
            color = rimColor,
            radius = radius * RING_FRACTION,
            center = faceCenter,
            style = Stroke(width = diameter * RING_WIDTH_FRACTION),
        )

        if (!turn.isFrontForward) {
            drawPath(
                path = starPath(faceCenter, diameter * STAR_WIDTH_FRACTION / STAR_INK_WIDTH),
                color = rimColor,
                alpha = symbolAlpha,
            )
        }
    }
}

/**
 * A five-pointed star, the reverse iOS strikes on its back cap, sized by
 * [radius] and standing centered on [center].
 *
 * Centered by its *ink*, which is not the same as centering the shape: a
 * five-pointed star reaches a full radius up to its top point but only
 * [STAR_INK_TOP] of one down to its bottom edge, so a star sitting on the
 * circle's center reads as noticeably high. The whole shape is nudged down by
 * half that difference to put what the eye sees in the middle. iOS lands in the
 * same place from the other direction, by aspect-fitting the glyph's ink box
 * into a square centered on the cap.
 */
private fun starPath(center: Offset, radius: Float): Path {
    val inkOffset = radius * (1f - STAR_INK_TOP) / 2f
    return Path().apply {
        repeat(STAR_POINTS * 2) { index ->
            val pointRadius = if (index % 2 == 0) radius else radius * STAR_INNER_RATIO
            val angle = Math.toRadians(-90.0 + index * HALF_TURN / STAR_POINTS)
            val x = center.x + pointRadius * cos(angle).toFloat()
            val y = center.y + pointRadius * sin(angle).toFloat() + inkOffset
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * The outline of a cylinder seen side-on: a rectangle [edgeHalfWidth] either
 * side of center, closed off by a half ellipse at each end.
 */
private fun coinSilhouette(
    center: Offset,
    edgeHalfWidth: Float,
    faceHalfWidth: Float,
    radius: Float,
): Path {
    val left = center.x - edgeHalfWidth
    val right = center.x + edgeHalfWidth
    val top = center.y - radius
    val bottom = center.y + radius
    return Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        arcTo(Rect(right - faceHalfWidth, top, right + faceHalfWidth, bottom), -90f, 180f, false)
        lineTo(left, bottom)
        arcTo(Rect(left - faceHalfWidth, top, left + faceHalfWidth, bottom), 90f, 180f, false)
        close()
    }
}

/**
 * Draws the content with every color drained to grey.
 *
 * A layer is needed rather than a tint: emoji carry their own color, so the
 * filter has to apply to the rendered glyph rather than to a paint.
 */
private fun Modifier.grayscale(): Modifier = drawWithContent {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        canvas.restore()
    }
}

/** A coin that is not being spun, and so shows no rim at all. */
private val FACE_ON: () -> Float = { 0f }

/**
 * The coin's thickness as a share of its diameter — iOS's
 * `SCNCylinder(radius: 1.1, height: 0.12)`, which is 0.12 across 2.2.
 */
private const val THICKNESS_FRACTION = 0.12f / 2.2f

/** The struck symbol's share of the coin's diameter — iOS's `coin * 0.5`. */
private const val SYMBOL_FRACTION = 0.5f

/**
 * The star's ink width as a share of the coin's diameter — iOS's back-cap plane
 * is 1.3 across a coin of 2.2, and the star's width is what fills it.
 */
private const val STAR_WIDTH_FRACTION = 0.59f

private const val STAR_POINTS = 5

/** Inner radius of a regular pentagram, `(3 - sqrt(5)) / 2` of the outer. */
private const val STAR_INNER_RATIO = 0.381966f

/** How far a five-pointed star reaches below its center, in radii. */
private const val STAR_INK_TOP = 0.809017f

/** The star's ink width in radii — `2 * cos(18°)`. */
private const val STAR_INK_WIDTH = 1.902113f

/** Degrees in half a turn, which a star's points divide between them. */
private const val HALF_TURN = 180.0

/** The ring circle's share of the coin's radius — iOS's 90% of the diameter. */
private const val RING_FRACTION = 0.9f

/** Ring stroke width, as a share of the coin's diameter. iOS's `coin * 0.02`. */
private const val RING_WIDTH_FRACTION = 0.02f

/** How visible a locked medal's symbol is. iOS's `.opacity(0.55)`. */
private const val LOCKED_SYMBOL_ALPHA = 0.55f

/** Below this many pixels wide, the face is not worth drawing. */
private const val HAIRLINE = 0.5f
