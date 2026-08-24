package com.anmol.voyage.globe

import com.anmol.voyage.data.LatLon

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the camera is and what a screen tap points at.
 *
 * Kept separate from the renderer for the same reason `MapProjection` is kept
 * out of `WorldMap`: the projection and its inverse have to agree, and the only
 * way to be sure is to test them against each other without a GPU.
 *
 * The camera orbits a globe of radius 1 at the origin. [latitude] and
 * [longitude] are the point on the sphere the camera looks straight down at, so
 * they read the same way as the coordinates everywhere else in the app, and
 * [distance] is the camera's distance from the center — matching iOS's
 * `GlobeState.zoomLevel` and its 1.1…10.0 clamps.
 */
data class GlobeCamera(
    val latitude: Double = 20.0,
    val longitude: Double = 0.0,
    val distance: Float = DEFAULT_DISTANCE,
) {

    /** The camera's position in world space. */
    val position: Vec3d
        get() {
            // The inverse of PolygonTriangulator.latLonToSphere, at `distance`
            // instead of on the surface, so the looked-at point stays centered.
            val latRad = latitude * PI / 180.0
            val lonRad = -longitude * PI / 180.0
            return Vec3d(
                x = distance * cos(latRad) * cos(lonRad),
                y = distance * sin(latRad),
                z = distance * cos(latRad) * sin(lonRad),
            )
        }

    /** Applies a drag, in degrees, clamping latitude so the globe never flips over. */
    fun rotatedBy(deltaLatitude: Double, deltaLongitude: Double): GlobeCamera = copy(
        latitude = (latitude + deltaLatitude).coerceIn(-MAX_LATITUDE, MAX_LATITUDE),
        longitude = wrapLongitude(longitude + deltaLongitude),
    )

    /** Applies a pinch. [scale] > 1 zooms in, matching Compose's gesture convention. */
    fun zoomedBy(scale: Float): GlobeCamera =
        copy(distance = (distance / scale).coerceIn(MIN_DISTANCE, MAX_DISTANCE))

    /**
     * Degrees of rotation per pixel of drag.
     *
     * Scaled by distance so the globe tracks the finger at every zoom: close in,
     * a pixel covers less of the surface. iOS does the same in
     * `GlobeView.Coordinator.panRotationSpeed`.
     */
    fun degreesPerPixel(viewportHeight: Float): Double {
        if (viewportHeight <= 0f) return 0.0
        val visibleDegrees = 2.0 * Math.toDegrees(tan(FOV_RADIANS / 2.0)) * distance / 2.0
        return visibleDegrees / viewportHeight
    }

    /**
     * Turns a tap into a point on the globe, or null if it missed.
     *
     * The ray is built for the same perspective projection the renderer uses,
     * then handed to [PolygonTriangulator.raySphereSurfaceDirection] — the
     * analytic intersection ported from iOS PR #50, rather than a mesh hit-test
     * that oblique rays would strike on the raised fills first.
     */
    fun latLonAt(x: Float, y: Float, viewportWidth: Float, viewportHeight: Float): LatLon? {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null

        // Normalized device coordinates: -1…1, y up.
        val ndcX = (2.0 * x / viewportWidth) - 1.0
        val ndcY = 1.0 - (2.0 * y / viewportHeight)

        val aspect = viewportWidth / viewportHeight
        val tanHalfFov = tan(FOV_RADIANS / 2.0)

        val eye = position
        val forward = (Vec3d(0.0, 0.0, 0.0) - eye).normalized()
        val worldUp = Vec3d(0.0, 1.0, 0.0)
        val right = forward.cross(worldUp).normalized()
        val up = right.cross(forward).normalized()

        // Filament's FOV is vertical, matching iOS's 45° fieldOfView.
        val direction = forward +
            right * (ndcX * tanHalfFov * aspect) +
            up * (ndcY * tanHalfFov)

        val surface = PolygonTriangulator.raySphereSurfaceDirection(eye, direction) ?: return null
        return PolygonTriangulator.sphereToLatLon(surface)
    }

    companion object {
        /** Matches iOS `SCNCamera.fieldOfView = 45`. */
        const val FOV_DEGREES = 45.0
        private const val FOV_RADIANS = FOV_DEGREES * PI / 180.0

        /** iOS `GlobeState.zoomLevel` and its clamps. */
        const val DEFAULT_DISTANCE = 4.0f
        const val MIN_DISTANCE = 1.1f
        const val MAX_DISTANCE = 10.0f

        /**
         * Latitude stops just short of the poles: at exactly ±90° the camera's
         * forward vector is parallel to the world up vector and the view basis
         * collapses.
         */
        private const val MAX_LATITUDE = 89.0

        private fun wrapLongitude(value: Double): Double {
            var wrapped = (value + 180.0) % 360.0
            if (wrapped < 0) wrapped += 360.0
            return wrapped - 180.0
        }
    }
}
