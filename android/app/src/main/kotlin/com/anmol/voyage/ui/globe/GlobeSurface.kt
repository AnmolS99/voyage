package com.anmol.voyage.ui.globe

import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.GlobeFlight
import com.anmol.voyage.globe.GlobeInertia
import com.anmol.voyage.globe.MarkerMeshes
import com.anmol.voyage.globe.MicrostateDot
import com.anmol.voyage.globe.NamedCountryMesh
import com.anmol.voyage.globe.OutlineMesh
import com.anmol.voyage.globe.SphereMesh
import com.anmol.voyage.ui.map.CapitalMarker
import com.anmol.voyage.ui.map.MarkerSizes
import com.anmol.voyage.ui.map.rememberMarkerSizes
import com.google.android.filament.android.UiHelper
import kotlin.math.exp

/**
 * How one microstate's dot is currently painted.
 *
 * Resolved in composition, where the density needed to turn the style's border
 * `dp` into pixels is available — the renderer works in pixels and world units
 * and has no business knowing about `dp`.
 */
internal class GlobeDotStyle(
    val name: String,
    val fill: GlobeFill,
    val border: GlobeFill,
    val borderWidthPx: Float,
)

/**
 * The globe's rendering surface: a Filament-backed [TextureView] hosted in
 * Compose, with the rotate / pinch / tap gestures on top of it. It is a
 * TextureView and not a SurfaceView on purpose — see [GlobeSurfaceHost.attach].
 *
 * The render loop is driven by [Choreographer] rather than a background thread,
 * which is what Filament's own Android samples do: it keeps every Engine call
 * on one thread and paces frames to the display's vsync for free.
 *
 * @param dotStyles how each microstate's dot is currently painted. The dots
 *   themselves are meshes in the scene, uploaded with the rest of the geometry.
 * @param capital the selected country's capital, marked with a star.
 * @param focus a place to fly the camera to, once, when it changes. The selected
 *   country's center, so picking a country brings it into view.
 * @param autoRotating whether the globe turns on its own — true until something
 *   is selected or the globe is dragged.
 * @param onInteraction a drag or a zoom started, which ends the idle spin. A tap
 *   does not report here: on iOS a tap that misses every country leaves the
 *   globe turning, and one that hits stops it by selecting a country.
 */
@Composable
internal fun GlobeSurface(
    ocean: SphereMesh,
    countries: List<NamedCountryMesh>,
    outlineSectors: List<OutlineMesh>,
    microstateDots: List<MicrostateDot>,
    colorFor: (String) -> GlobeFill,
    oceanColor: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
    hitTester: CountryHitTester,
    onCountryTapped: (String?) -> Unit,
    modifier: Modifier = Modifier,
    dotStyles: List<GlobeDotStyle> = emptyList(),
    capital: LatLon? = null,
    selectedOutline: OutlineMesh? = null,
    selectedOutlineColor: GlobeFill? = null,
    focus: LatLon? = null,
    autoRotating: Boolean = false,
    onInteraction: () -> Unit = {},
    onCameraChange: (GlobeCamera) -> Unit = {},
) {
    val host = remember(backgroundColor) { GlobeSurfaceHost(backgroundColor.toFilamentColor()) }

    DisposableEffect(host) {
        onDispose { host.destroy() }
    }

    val sizes = rememberMarkerSizes()

    // Geometry is uploaded once per mesh set; recoloring below never touches it.
    DisposableEffect(host, ocean, countries, outlineSectors, microstateDots) {
        host.setGeometry(ocean, countries, outlineSectors, microstateDots)
        onDispose { }
    }

    // Resolved during composition on purpose: reading visited/wishlist/selection
    // here is what subscribes this composable to them, so a tap or a toggle
    // recomposes and repaints. Applying them is deferred to a SideEffect,
    // because on first composition the material instances do not exist until the
    // DisposableEffect above has uploaded the geometry.
    val fills = countries.map { colorFor(it.name) }
    SideEffect { host.applyColors(countries, fills, oceanColor, dotStyles, sizes) }

    // The star is a mesh, so it is rebuilt when the capital moves — not on every
    // recomposition. Its size is a uniform and rides the per-frame size pass.
    DisposableEffect(host, capital) {
        host.setCapitalStar(capital, sizes)
        onDispose { }
    }

    // The overlay border is a mesh upload, not a uniform write, so it is keyed
    // on both the shape and its color and re-runs only when one of them changes.
    DisposableEffect(host, selectedOutline, selectedOutlineColor) {
        host.setSelectedOutline(selectedOutline, selectedOutlineColor)
        onDispose { }
    }

    // Keyed on the place, so selecting a country flies to it exactly once and
    // re-selecting it after a deselect flies again — the guard iOS spends
    // `hasAnimatedToCountry` and `lastAnimatedCountry` on, for free.
    DisposableEffect(host, focus) {
        if (focus != null) host.flyTo(focus)
        onDispose { }
    }

    // The camera is the host's, not Compose state, so the callback that reports
    // it has to be pushed down rather than passed at each call: the render loop
    // moves the camera on its own now, while a flick is coasting.
    SideEffect {
        host.onCameraChange = onCameraChange
        host.onInteraction = onInteraction
        host.autoRotating = autoRotating
    }

    AndroidView(
        modifier = modifier
            .pointerInput(host) {
                detectTransformGestures { _, pan, zoom, _ ->
                    host.drag(pan / density, zoom)
                }
            }
            .pointerInput(host) {
                // Sits alongside the transform detector rather than inside it,
                // because what inertia needs is the two things that detector does
                // not report: when the gesture starts (to kill a spin still in
                // flight, as a tap does on iOS) and how fast the finger was
                // travelling when it left the screen.
                trackFlicks(host)
            }
            .pointerInput(host) {
                // Wheel and trackpad zoom. A touchscreen has pinch, but a mouse
                // is a first-class pointer on Chromebooks, DeX, tablets with a
                // mouse attached — and on the emulator, where pinch otherwise
                // needs a modifier key most people never find.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val scroll = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (scroll == 0f) continue
                        host.zoom(zoomForScroll(scroll))
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .pointerInput(host, hitTester) {
                detectTapGestures { offset ->
                    onCountryTapped(host.countryAt(offset.x, offset.y, hitTester))
                }
            },
        factory = { context ->
            TextureView(context).also { view -> host.attach(view) }
        },
    )
}

/**
 * Binds a [GlobeRenderer] to a [TextureView] and a Choreographer loop.
 *
 * Kept out of the composable so the engine's lifetime is tied to one object
 * that `DisposableEffect` can destroy, rather than to several remembered values
 * that would have to be torn down in the right order.
 */
private class GlobeSurfaceHost(backgroundColor: FloatArray) {

    private val renderer = GlobeRenderer(backgroundColor)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var destroyed = false
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    /**
     * The camera, owned here rather than held as Compose state.
     *
     * A drag changes it up to once per frame, and recomposing the whole globe
     * for that would re-resolve all 181 country colors each time for a value
     * only the render loop reads. Everything that touches it — the gesture
     * handlers, the tap hit-test and [doFrame] — runs on the main thread, so it
     * needs no synchronization.
     *
     * Nothing outside the render loop reads it any more: the markers were
     * briefly drawn by a Compose overlay that had to, and that is exactly why
     * they trailed the globe while dragging. They are meshes in the scene now.
     */
    private var camera = GlobeCamera()

    /** The spin left over from the last flick, stepped once per frame. */
    private val inertia = GlobeInertia()

    /** A camera flight in progress, or null. Outranks both kinds of spin. */
    private var flight: GlobeFlight? = null

    private var lastFrameNanos = 0L

    /** Reported to the composable after every camera move, coasting included. */
    var onCameraChange: (GlobeCamera) -> Unit = {}

    /** Reported once at the start of each drag or zoom. */
    var onInteraction: () -> Unit = {}

    /** Whether to keep turning the globe when nothing else is moving it. */
    var autoRotating: Boolean = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return
            choreographer.postFrameCallback(this)
            advance(frameTimeNanos)
            renderer.setCamera(camera)
            renderer.render(frameTimeNanos)
        }
    }

    /**
     * Moves the globe by everything that is not a finger: one frame of coasting
     * from the last flick, or of the idle spin when nothing was flicked.
     *
     * Integrating here rather than off a timer is what makes the motion even:
     * one step per rendered frame, paced by the same vsync that will show it.
     * iOS does this in `renderer(_:updateAtTime:)` for the same reason, and
     * clamps the step the same way — a frame the app was not scheduled for
     * (a tab switch, a stall) must not teleport the globe.
     *
     * Momentum wins over the idle spin while it lasts, as it does on iOS, where
     * the interactive spin overwrites the auto-rotation action's transform every
     * frame it is active. In practice the two rarely overlap: a drag stops the
     * idle spin, and only a deselect during the second or two a flick is still
     * coasting can turn it back on mid-flight.
     */
    private fun advance(frameTimeNanos: Long) {
        val elapsed = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / NANOS_PER_SECOND
        lastFrameNanos = frameTimeNanos
        val dt = elapsed.coerceIn(0f, MAX_FRAME_STEP)
        if (dt <= 0f) return
        val flying = flight
        when {
            flying != null -> {
                updateCamera { flying.step(dt) }
                if (flying.isFinished) flight = null
            }
            inertia.isActive -> updateCamera { inertia.step(dt, it) }
            autoRotating -> updateCamera { it.autoRotated(dt) }
        }
    }

    /**
     * Starts a flight to [target], from wherever the globe is now.
     *
     * Any momentum is dropped rather than added to the flight: the camera has
     * one writer per frame, and a flick still coasting would otherwise be
     * fighting the flight for the same 0.8 seconds.
     */
    fun flyTo(target: LatLon) {
        inertia.reset()
        flight = GlobeFlight(camera, latitude = target.lat, longitude = target.lon)
    }

    /**
     * A drag: rotate under the finger and pinch, in one gesture as iOS does.
     *
     * [pan] is in dp, not pixels — see [GlobeCamera.degreesPerDp].
     */
    fun drag(pan: Offset, zoom: Float) {
        onInteraction()
        updateCamera { camera ->
            val degreesPerDp = camera.degreesPerDp
            camera
                // Dragging right spins the globe east-to-west under the finger,
                // so the surface tracks the touch rather than running away from
                // it.
                .rotatedBy(
                    deltaLatitude = pan.y * degreesPerDp,
                    deltaLongitude = -pan.x * degreesPerDp,
                )
                .zoomedBy(zoom)
        }
    }

    /**
     * Any new touch stops the globe moving by itself — a spin still coasting, as
     * a tap or a drag does on iOS, and a camera flight along with it. iOS leaves
     * its flight animating under the finger, where the drag and the animation
     * write the same transform and the globe stutters between them; a touch
     * taking the wheel is the behavior that gesture was asking for.
     */
    fun stopMotion() {
        inertia.reset()
        flight = null
    }

    /**
     * Hands the globe the finger's parting speed, in dp per second.
     *
     * Converted through the same pan curve a drag uses, so the first coasting
     * frame carries on at the speed the finger left at instead of stepping.
     */
    fun flick(velocity: Velocity) {
        val degreesPerDp = camera.degreesPerDp
        inertia.latitude = (velocity.y * degreesPerDp).toFloat()
        inertia.longitude = (-velocity.x * degreesPerDp).toFloat()
    }

    /** A wheel or trackpad zoom, which ends the idle spin as a pinch does. */
    fun zoom(scale: Float) {
        onInteraction()
        updateCamera { it.zoomedBy(scale) }
    }

    private fun updateCamera(transform: (GlobeCamera) -> GlobeCamera) {
        camera = transform(camera)
        onCameraChange(camera)
    }

    /** The country under a tap, or null for a tap that missed the globe. */
    fun countryAt(x: Float, y: Float, hitTester: CountryHitTester): String? {
        val latLon = camera.latLonAt(x, y, viewportWidth, viewportHeight) ?: return null
        return hitTester.findCountry(latLon.lat, latLon.lon)
    }

    fun attach(view: TextureView) {
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                if (!destroyed) renderer.onNativeWindowChanged(surface)
            }

            override fun onDetachedFromSurface() {
                if (!destroyed) renderer.onDetachedFromSurface()
            }

            override fun onResized(width: Int, height: Int) {
                if (destroyed) return
                renderer.onResized(width, height)
                viewportWidth = width.toFloat()
                viewportHeight = height.toFloat()
            }
        }
        // A TextureView, not a SurfaceView. A SurfaceView is its own window
        // layer: it punches a hole through the app window and shows black until
        // its first buffer is composited. Compose navigation builds a new one on
        // every return to Home, so that black gap was visible on every tab
        // switch. A TextureView draws inside the normal view hierarchy, so
        // before the first frame it is simply transparent and the theme
        // background shows through instead.
        uiHelper.isOpaque = false
        uiHelper.attachTo(view)
        choreographer.postFrameCallback(frameCallback)
    }

    fun setGeometry(
        ocean: SphereMesh,
        countries: List<NamedCountryMesh>,
        outlines: List<OutlineMesh>,
        microstateDots: List<MicrostateDot>,
    ) {
        if (!destroyed) renderer.setGeometry(ocean, countries, outlines, microstateDots)
    }

    fun setSelectedOutline(outline: OutlineMesh?, color: GlobeFill?) {
        if (!destroyed) renderer.setSelectedOutline(outline, color)
    }

    /**
     * Builds the capital star's two meshes and hands them to the renderer.
     *
     * The corners come from [CapitalMarker], the same definition the flat map
     * turns into a `Path`, in the +Y-up orientation the sphere's tangent plane
     * wants. Sizes are unit-relative here — the material scales them to
     * [sizes] every frame, so a zoom never rebuilds the mesh.
     */
    fun setCapitalStar(capital: LatLon?, sizes: MarkerSizes) {
        if (destroyed) return
        if (capital == null) {
            renderer.setCapitalStar(null, null, 0f, 0f)
            return
        }
        val corners = CapitalMarker.starVertices(outerRadius = 1f, yUp = true)
        renderer.setCapitalStar(
            outline = MarkerMeshes.star(capital.lat, capital.lon, STAR_OUTLINE_RADIUS, corners),
            fill = MarkerMeshes.star(capital.lat, capital.lon, STAR_FILL_RADIUS, corners),
            radiusPx = sizes.starRadiusPx,
            outlinePx = sizes.starOutlinePx,
        )
    }

    fun applyColors(
        countries: List<NamedCountryMesh>,
        fills: List<GlobeFill>,
        oceanColor: androidx.compose.ui.graphics.Color,
        dotStyles: List<GlobeDotStyle>,
        sizes: MarkerSizes,
    ) {
        if (destroyed) return
        renderer.setOceanColor(oceanColor)
        for ((index, country) in countries.withIndex()) {
            renderer.setCountryColor(country.name, fills[index])
        }
        for (dot in dotStyles) {
            renderer.setDotAppearance(
                name = dot.name,
                fill = dot.fill,
                border = dot.border,
                radiusPx = sizes.dotRadiusPx,
                borderWidthPx = dot.borderWidthPx,
            )
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        renderer.destroy()
    }
}

/**
 * Watches a whole touch gesture for the two things the transform detector does
 * not report: its start, and the finger's speed at its end.
 *
 * The pan it accumulates is the centroid's, matching what
 * `detectTransformGestures` applies to the camera — so the spin a flick leaves
 * behind continues at the speed the globe was already turning, not at the speed
 * of some other measure of the gesture. Positions are fed in dp and stamped with
 * the event's own time, so the velocity comes back in dp/s directly.
 *
 * Nothing here consumes: this observes a gesture the detectors above own.
 */
private suspend fun PointerInputScope.trackFlicks(host: GlobeSurfaceHost) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        host.stopMotion()

        val velocity = VelocityTracker()
        var travel = Offset.Zero
        var event: PointerEvent
        do {
            event = awaitPointerEvent()
            // Pointers that were down before this event and still are: a finger
            // arriving or leaving moves the centroid without moving the globe.
            val moving = event.changes.filter { it.pressed && it.previousPressed }
            if (moving.isNotEmpty()) {
                var pan = Offset.Zero
                for (change in moving) pan += change.position - change.previousPosition
                travel += pan / (moving.size * density)
                velocity.addPosition(moving.first().uptimeMillis, travel)
            }
        } while (event.changes.any { it.pressed })

        host.flick(velocity.calculateVelocity())
    }
}

/** Nanoseconds in a second, as a float — [Choreographer] counts in nanos. */
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * The longest step a single frame may take, in seconds. iOS clamps its frame
 * delta to the same 0.1 s.
 */
private const val MAX_FRAME_STEP = 0.1f

/**
 * Turns one scroll step into a zoom factor.
 *
 * Scroll deltas are ~±1 per wheel notch but arbitrary and much smaller on a
 * precision trackpad, so this is exponential rather than linear: every unit of
 * scroll is a constant *ratio* of zoom, which keeps the feel the same at both
 * ends of the 1.1…10.0 distance range and can never flip the sign of the
 * distance the way a subtractive step could.
 *
 * Scrolling up (negative delta) zooms in, matching every map on the platform.
 */
internal fun zoomForScroll(scrollDelta: Float): Float = exp(-scrollDelta * SCROLL_ZOOM_RATE)

private const val SCROLL_ZOOM_RATE = 0.2f

/**
 * Sphere radii for the star's two layers, just above the dots so a capital on a
 * microstate is drawn over its dot rather than inside it.
 */
private const val STAR_OUTLINE_RADIUS = 1.0064f
private const val STAR_FILL_RADIUS = 1.0066f
