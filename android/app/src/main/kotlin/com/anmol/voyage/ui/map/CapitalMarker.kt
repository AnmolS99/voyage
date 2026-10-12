package com.anmol.voyage.ui.map

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "star seal" that marks a capital city — a terracotta disc with an ink rim
 * and a small cream star cut out of its centre. The port of iOS `CapitalMarker`.
 *
 * The map and the globe must draw capitals identically, so the proportions live
 * here instead of being restated per view — the map paints the seal onto its
 * canvas, the globe lays it on the sphere as three flat meshes (rim, disc, star).
 * Both measure everything as a fraction of the seal's outer radius.
 */
object CapitalMarker {

    /**
     * Inner-to-outer radius ratio of the star. Shallower than the classic 0.382
     * so the star stays legible as a cut-out at a few dp across.
     */
    private const val INNER_RADIUS_RATIO = 0.45f
    private const val POINT_COUNT = 5

    /** The star's outer radius as a fraction of the seal's. */
    const val STAR_TO_SEAL_RATIO = 0.6f

    /** The ink rim's width as a fraction of the seal's radius — 1 dp on the map's 6 dp seal. */
    const val RIM_TO_SEAL_RATIO = 1f / 6f

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
