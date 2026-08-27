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
 * instead of being rebuilt per view — the map turns it into a `Path` it strokes,
 * the globe into a mesh it lays on the sphere. Only [starVertices] knows what a
 * star is.
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
     * The star's ten corners, as `[x0, y0, x1, y1, …]` counter-clockwise from
     * straight up.
     *
     * @param yUp `true` for spaces where +Y points up — the globe, which lays the
     *   star on the sphere's tangent plane with +Y pointing north — and `false`
     *   for +Y-down spaces (Compose `Canvas`, like Core Graphics on iOS). Getting
     *   this wrong renders the star upside down rather than failing loudly, which
     *   is why it is a named argument rather than a convention. iOS carries the
     *   same flag on the same shape, for the same two callers.
     */
    fun starVertices(outerRadius: Float, yUp: Boolean): FloatArray {
        val innerRadius = outerRadius * INNER_RADIUS_RATIO
        val ySign = if (yUp) 1f else -1f
        val vertices = FloatArray(POINT_COUNT * 2 * 2)

        for (vertex in 0 until POINT_COUNT * 2) {
            // Start straight up, then alternate outer/inner every half segment.
            val angle = PI / 2 + vertex * PI / POINT_COUNT
            val radius = if (vertex % 2 == 0) outerRadius else innerRadius
            vertices[vertex * 2] = cos(angle).toFloat() * radius
            vertices[vertex * 2 + 1] = sin(angle).toFloat() * radius * ySign
        }
        return vertices
    }

    /** The star as a Compose path, for the renderer that draws rather than builds a mesh. */
    fun starPath(outerRadius: Float): Path {
        val vertices = starVertices(outerRadius, yUp = false)
        val path = Path()
        for (vertex in 0 until vertices.size / 2) {
            val x = vertices[vertex * 2]
            val y = vertices[vertex * 2 + 1]
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
