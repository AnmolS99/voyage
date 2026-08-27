package com.anmol.voyage.globe

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A flat marker lying on the globe's surface — a microstate's dot, or a capital's
 * star.
 *
 * Built the same way [OutlineMesh] is, and drawn with the same material: every
 * vertex sits at the marker's *center* on the sphere, with the direction it
 * should move in stored per vertex, and the material pushes it out by one
 * uniform at render time. So the shape is degenerate until it is drawn, and its
 * on-screen size is one float rather than geometry — which is what lets a marker
 * keep a constant size on screen without rebuilding it on every zoom.
 *
 * @property offsets Four floats per vertex: `xyz` is the offset direction in
 *   world space, already scaled so a length of 1 means "the marker's nominal
 *   radius", and `w` is the visited+wishlist gradient parameter running
 *   bottom-left to top-right across the marker, as on the flat map.
 */
class MarkerMesh(
    val positions: FloatArray,
    val offsets: FloatArray,
    val indices: IntArray,
    /** Where the marker sits on the sphere — every vertex is here until drawn. */
    val center: Vec3,
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * One microstate's dot: a ring in the border color with a fill on top, the same
 * two layers the flat map draws as a filled circle with a stroke.
 *
 * They are separate meshes at slightly different sphere radii rather than one
 * mesh drawn twice, because at the same radius the two would z-fight.
 */
class MicrostateDot(val name: String, val ring: MarkerMesh, val fill: MarkerMesh)

/**
 * Builds the globe's markers.
 *
 * These are meshes in the Filament scene rather than a Compose overlay above it,
 * and that is the whole point: an overlay is a second producer drawing from its
 * own copy of the camera, so during a drag it lands a frame away from the
 * surface underneath and the markers visibly trail the borders. In the scene
 * they move under the same camera matrix as everything else, and cannot drift by
 * construction. iOS puts its markers in the SceneKit scene for the same reason.
 */
object MarkerMeshes {

    /** Corners around a dot. 24 is smooth at every size a dot is ever drawn. */
    private const val DISC_SEGMENTS = 24

    /**
     * The tangent frame at a point on the globe: which way is east, and which
     * way is north, for a marker lying flat on the surface there.
     *
     * Markers are laid on the tangent plane rather than turned to face the
     * camera. A star is a printed-map convention, and on a globe it should read
     * as printed on the surface; billboarding it (as iOS does) makes it float
     * above the sphere instead.
     */
    private fun tangentFrame(lat: Double, lon: Double): Pair<Vec3, Vec3> {
        val normal = PolygonTriangulator.latLonToSphere(lat, lon, 1f)
        // east = up × normal, which points toward increasing longitude given the
        // handedness `latLonToSphere` uses. The operand order is load-bearing:
        // normal × up is its mirror, and a mirrored frame draws a star that is
        // upside down and back to front while every size and flatness check
        // still passes. The poles would make this degenerate; no country in the
        // dataset is within a degree of one.
        var eastX = normal.z
        var eastY = 0f
        var eastZ = -normal.x
        val length = sqrt(eastX * eastX + eastZ * eastZ)
        if (length < 1e-6f) {
            eastX = 1f; eastY = 0f; eastZ = 0f
        } else {
            eastX /= length; eastZ /= length
        }
        val east = Vec3(eastX, eastY, eastZ)
        // north = normal × east
        val north = Vec3(
            x = normal.y * east.z - normal.z * east.y,
            y = normal.z * east.x - normal.x * east.z,
            z = normal.x * east.y - normal.y * east.x,
        )
        return east to north
    }

    /**
     * A filled disc centered on [lat]/[lon], as a triangle fan.
     *
     * [radiusRatio] scales the rim relative to the material's size uniform, which
     * is how the dot's black ring is drawn slightly wider than its fill from the
     * same nominal radius.
     */
    fun disc(lat: Double, lon: Double, sphereRadius: Float, radiusRatio: Float = 1f): MarkerMesh {
        val corners = FloatArray(DISC_SEGMENTS * 2)
        for (i in 0 until DISC_SEGMENTS) {
            val angle = 2.0 * PI * i / DISC_SEGMENTS
            corners[i * 2] = (cos(angle) * radiusRatio).toFloat()
            corners[i * 2 + 1] = (sin(angle) * radiusRatio).toFloat()
        }
        return fan(lat, lon, sphereRadius, corners, extent = radiusRatio)
    }

    /**
     * A star centered on [lat]/[lon], from the corners `CapitalMarker` defines.
     *
     * The caller passes the corners so the shape stays defined in exactly one
     * place — the same array the flat map turns into a `Path`. They must be in
     * a +Y-is-up space, since +Y here is north.
     */
    fun star(
        lat: Double,
        lon: Double,
        sphereRadius: Float,
        corners: FloatArray,
        radiusRatio: Float = 1f,
    ): MarkerMesh {
        var extent = 0f
        for (i in corners.indices) {
            val magnitude = kotlin.math.abs(corners[i])
            if (magnitude > extent) extent = magnitude
        }
        return fan(lat, lon, sphereRadius, corners, extent = extent)
    }

    /**
     * Turns a closed 2D outline into a triangle fan on the tangent plane at
     * [lat]/[lon]. Convex-safe only, which both markers are: a five-pointed star
     * fans correctly from its own center even though it is not convex, because
     * every corner is visible from the center.
     */
    private fun fan(
        lat: Double,
        lon: Double,
        sphereRadius: Float,
        corners: FloatArray,
        extent: Float,
    ): MarkerMesh {
        val (east, north) = tangentFrame(lat, lon)
        val center = PolygonTriangulator.latLonToSphere(lat, lon, sphereRadius)
        val cornerCount = corners.size / 2

        // Every vertex sits at the marker's center; the material moves it out.
        val positions = FloatArray((cornerCount + 1) * 3)
        val offsets = FloatArray((cornerCount + 1) * 4)
        for (v in 0..cornerCount) {
            positions[v * 3] = center.x
            positions[v * 3 + 1] = center.y
            positions[v * 3 + 2] = center.z
        }
        // Vertex 0 is the fan's hub, with no offset; its gradient is the midpoint.
        offsets[3] = 0.5f

        for (i in 0 until cornerCount) {
            val u = corners[i * 2]
            val v = corners[i * 2 + 1]
            val slot = (i + 1) * 4
            offsets[slot] = east.x * u + north.x * v
            offsets[slot + 1] = east.y * u + north.y * v
            offsets[slot + 2] = east.z * u + north.z * v
            // Bottom-left of the marker's own box to top-right, the direction
            // `CountryStyles` documents for the flat map's gradient.
            offsets[slot + 3] = (((u / extent + 1f) / 2f + (v / extent + 1f) / 2f) / 2f)
                .coerceIn(0f, 1f)
        }

        val indices = IntArray(cornerCount * 3)
        for (i in 0 until cornerCount) {
            indices[i * 3] = 0
            indices[i * 3 + 1] = i + 1
            indices[i * 3 + 2] = (i + 1) % cornerCount + 1
        }

        return MarkerMesh(
            positions = positions,
            offsets = offsets,
            indices = indices,
            center = center,
        )
    }
}
