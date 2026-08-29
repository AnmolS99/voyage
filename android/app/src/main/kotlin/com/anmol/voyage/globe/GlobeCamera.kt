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
    fun rotatedBy(deltaLatitude: Double, deltaLongitude: Double): GlobeCamera =
        at(latitude + deltaLatitude, longitude + deltaLongitude, distance)

    /**
     * Advances the idle spin by [dt] seconds.
     *
     * iOS turns its globe node a full circle every 60 seconds; here the globe is
     * fixed and the camera orbits, so the same motion is the camera's longitude
     * running backwards at [AUTO_ROTATION_DEGREES_PER_SECOND]. Rotating the globe
     * about its polar axis and moving the camera the other way around it are the
     * same picture, and the direction is the one Earth actually turns: new land
     * arrives at the left limb.
     */
    fun autoRotated(dt: Float): GlobeCamera =
        rotatedBy(0.0, -AUTO_ROTATION_DEGREES_PER_SECOND * dt)

    /** Applies a pinch. [scale] > 1 zooms in, matching Compose's gesture convention. */
    fun zoomedBy(scale: Float): GlobeCamera = at(latitude, longitude, distance / scale)

    /**
     * Degrees of rotation per dp of finger travel — iOS's pan curve, ported.
     *
     * Speed grows with the square of camera distance so the globe stays quick to
     * spin when zoomed out. That falloff alone overshoots close in, though,
     * because the on-screen arc a rotation sweeps grows as
     * `1 / (distance - radius)`: at the minimum distance the surface ran ahead of
     * the finger. Zoomed in past [DEFAULT_DISTANCE] the speed is therefore capped
     * at finger tracking, and the cap tightens to [FULL_ZOOM_TRACKING_FACTOR] of
     * finger speed at the closest zoom, ramping back to 1:1 by the reference
     * distance so the extra damping lands where fine control matters. Both
     * branches agree at the reference distance, so speed is continuous.
     *
     * Per *dp*, not per pixel: iOS's constants are radians per point, and a point
     * and a dp are the same physical size, so the globe moves the same distance
     * under the same finger travel on both platforms regardless of screen
     * density. This replaced a projection-derived speed that was correct in its
     * own terms and about 2.4x slower than iOS at the default zoom — matching the
     * feel matters more here than deriving it, since the constants below are what
     * was actually tuned against a finger.
     *
     * iOS `GlobeView.Coordinator.panRotationSpeed`.
     */
    val degreesPerDp: Double
        get() = Math.toDegrees(panRadiansPerDp(distance).toDouble())

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

    /**
     * How many world units one screen pixel covers, at the globe's near face.
     *
     * This is what lets the markers be measured in `dp` like the flat map's:
     * multiply a pixel size by this and the resulting world size covers those
     * pixels at the current zoom. Distinct from [screenScale], which is a
     * *ratio* against the default distance and is what the borders use — a
     * border has a base world width to scale, a marker has a target pixel size
     * to hit.
     */
    fun pixelSizeInWorld(viewportHeight: Float): Float {
        if (viewportHeight <= 0f) return 0f
        // The globe's near face is (distance - radius) in front of the camera,
        // where the viewport spans 2·tan(fov/2)·depth world units.
        val depth = (distance - GLOBE_RADIUS).coerceAtLeast(0f)
        return (2.0 * tan(FOV_RADIANS / 2.0) * depth / viewportHeight).toFloat()
    }

    /**
     * How much to shrink world-sized decorations so they keep a constant size on
     * screen — today the border outlines, and whatever else the globe grows.
     *
     * Perspective makes anything on the globe's near face cover a span
     * proportional to `1 / (distance - radius)`, so a world-space width has to
     * scale by the inverse to look unchanged. 1.0 at the default distance.
     *
     * The lower clamp is the scale at the closest zoom, so it never binds inside
     * the usable range: width stays constant all the way down rather than the
     * outlines fattening once past a floor. iOS
     * `GlobeView.Coordinator.screenScale` and its clamp, exactly.
     */
    val screenScale: Float
        get() {
            val minimum = rawScreenScale(MIN_DISTANCE)
            return rawScreenScale(distance).coerceIn(minimum, 1.0f)
        }

    /**
     * Whether a mesh with this bounding sphere lies entirely beyond the globe's
     * horizon — everything it draws is on the far side and hidden by the globe.
     *
     * A point p on the unit sphere is visible from a camera at distance d iff
     * `dot(p, camDir) >= 1/d`. For a bounding sphere (center c, radius r),
     * `dot(p, camDir) <= dot(c, camDir) + r` for every point it contains, so the
     * whole mesh is safely behind the horizon once that upper bound falls below
     * it. Ported from iOS `cullFarSideOutlineSectors`, [HORIZON_MARGIN] included
     * to keep sectors from popping right at the limb.
     *
     * Conservative by construction: it can answer false for a mesh that happens
     * to be invisible, never true for one with a visible vertex.
     */
    fun isBeyondHorizon(center: Vec3, boundingRadius: Float): Boolean {
        if (distance <= 1.0f) return false
        // The camera orbits a globe fixed at the origin, so its position *is*
        // its direction from the globe's center, once normalized.
        val eye = position.normalized()
        val dot = center.x * eye.x + center.y * eye.y + center.z * eye.z
        return dot + boundingRadius < 1.0 / distance - HORIZON_MARGIN
    }

    companion object {
        /**
         * A camera at a place, with every limit already applied: latitude
         * clamped short of the pole, longitude wrapped into ±180°, distance
         * clamped to the zoom range.
         *
         * The one way to build a camera from raw numbers, so a drag, a pinch and
         * a flight cannot each end up with their own idea of the limits.
         */
        fun at(latitude: Double, longitude: Double, distance: Float) = GlobeCamera(
            latitude = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE),
            longitude = wrapLongitude(longitude),
            distance = distance.coerceIn(MIN_DISTANCE, MAX_DISTANCE),
        )

        /** Keeps the horizon test from popping meshes right at the limb. */
        const val HORIZON_MARGIN = 0.02

        /** The globe's own radius; the ocean sphere is built at exactly this. */
        const val GLOBE_RADIUS = 1.0f

        private fun rawScreenScale(distance: Float): Float =
            (distance - GLOBE_RADIUS) / (DEFAULT_DISTANCE - GLOBE_RADIUS)

        /** Matches iOS `SCNCamera.fieldOfView = 45`. */
        const val FOV_DEGREES = 45.0
        private const val FOV_RADIANS = FOV_DEGREES * PI / 180.0

        /** iOS `GlobeState.zoomLevel` and its starting value. */
        const val DEFAULT_DISTANCE = 4.0f

        /** iOS `GlobeState.minCameraDistance`. */
        const val MIN_DISTANCE = 1.1f

        /**
         * How far out the globe can be pushed.
         *
         * Tighter than iOS on purpose, and the one place this globe's motion is
         * deliberately not a port. iOS lets a pinch reach 8.0 and its zoom-out
         * button 10.0 (`GlobeState.maxCameraDistance`), by which point the globe
         * is a small ball in a lot of empty screen. There is nothing to see out
         * there, so Android stops at 6.0 — still comfortably the whole world in
         * frame, about 40% of the screen's height.
         *
         * A pinch is the only way out here; if Android ever grows a zoom-out
         * button, that is the moment to give the state its own looser clamp the
         * way iOS does, not now.
         */
        const val MAX_DISTANCE = 6.0f

        /**
         * How far from the equator a drag can tilt the camera. iOS's
         * `±.pi / 2.5` clamp on `currentRotationX`, in degrees.
         *
         * Well short of the poles, which the view basis needs anyway: at exactly
         * ±90° the camera's forward vector is parallel to the world up vector and
         * the basis collapses.
         */
        const val MAX_LATITUDE = 72.0

        /**
         * How fast an untouched globe turns. iOS runs `rotateBy(y: 2π)` over 60
         * seconds, which is this.
         */
        const val AUTO_ROTATION_DEGREES_PER_SECOND = 360.0 / 60.0

        /** Radians of rotation per point of finger travel at [DEFAULT_DISTANCE]. */
        private const val BASE_PAN_SPEED = 0.005f

        /**
         * Fraction of finger speed the globe tracks at when fully zoomed in.
         * Below 1.0 the surface deliberately lags the finger, trading 1:1 feel
         * for finer control when a single country fills the screen.
         */
        private const val FULL_ZOOM_TRACKING_FACTOR = 0.6f

        private fun panRadiansPerDp(distance: Float): Float {
            val distanceRatio = distance / DEFAULT_DISTANCE
            val quadratic = BASE_PAN_SPEED * distanceRatio * distanceRatio
            if (distance >= DEFAULT_DISTANCE) return quadratic

            val zoomOutProgress = (distance - MIN_DISTANCE) / (DEFAULT_DISTANCE - MIN_DISTANCE)
            val tracking = FULL_ZOOM_TRACKING_FACTOR +
                (1f - FULL_ZOOM_TRACKING_FACTOR) * zoomOutProgress.coerceIn(0f, 1f)
            // The unclamped ratio, as iOS uses here: `screenScale` clamps for the
            // outline widths, and the cap wants the raw perspective factor.
            return minOf(quadratic, BASE_PAN_SPEED * tracking * rawScreenScale(distance))
        }

        private fun wrapLongitude(value: Double): Double {
            var wrapped = (value + 180.0) % 360.0
            if (wrapped < 0) wrapped += 360.0
            return wrapped - 180.0
        }
    }
}
