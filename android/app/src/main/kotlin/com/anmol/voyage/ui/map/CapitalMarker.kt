package com.anmol.voyage.ui.map

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The five-pointed star that marks a capital city, as on printed maps — the port
 * of iOS `CapitalMarker`.
 *
 * The map and the globe must draw capitals identically, so the shape lives here
 * instead of being rebuilt per view. Both get it through
 * `rememberCapitalStarPath`, at one size — see `CountryMarkers`.
 */
object CapitalMarker {

    /**
     * Inner-to-outer radius ratio. 0.382 is the classic five-pointed star — the
     * inner vertices land where the arms would cross if it were drawn as a
     * pentagram.
     */
    private const val INNER_RADIUS_RATIO = 0.382f
    private const val POINT_COUNT = 5

    /**
     * A star centered on the origin with one point facing up, in a +Y-is-down
     * space (Compose `Canvas`, like Core Graphics on iOS).
     *
     * There is deliberately no `yUp` flag here, where iOS has one. iOS needs it
     * because its globe extrudes this path into a mesh placed in a +Y-up scene;
     * Android's globe draws the star as a screen-space overlay, so both callers
     * want the one +Y-down orientation.
     */
    fun starPath(outerRadius: Float): Path {
        val path = Path()
        val innerRadius = outerRadius * INNER_RADIUS_RATIO

        for (vertex in 0 until POINT_COUNT * 2) {
            // Start straight up, then alternate outer/inner every half segment.
            val angle = PI / 2 + vertex * PI / POINT_COUNT
            val radius = if (vertex % 2 == 0) outerRadius else innerRadius
            val x = cos(angle).toFloat() * radius
            val y = -sin(angle).toFloat() * radius
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        path.close()
        return path
    }
}
