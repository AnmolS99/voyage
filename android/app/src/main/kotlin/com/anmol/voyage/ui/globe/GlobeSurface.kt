package com.anmol.voyage.ui.globe

import android.graphics.Bitmap
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How one microstate's dot is currently painted.
 *
 * Resolved in composition, where the density needed to turn the style's border
 * `dp` into pixels is available — the renderer works in pixels and world units
 * and has no business knowing about `dp`.
 */
internal class GlobeDotStyle(
    val name: String,
    /** Null over an Earth texture for an unmarked dot, which is then only its ring. */
    val fill: GlobeFill?,
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
 * @param colorFor a country's fill, or null to leave it undrawn over the texture.
 * @param earthTexture the image the ocean sphere is painted with. Null paints it
 *   flat [oceanColor] instead, and then every fill should be painted.
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
 * @param visible whether this globe is the thing the user is looking at. A
 *   hidden globe keeps its engine, its geometry and its surface — that is the
 *   whole point of hiding it rather than removing it — but stops rendering
 *   frames and stops taking touches.
 * @param host the engine and its scene. Created here by default; `HomeScreen`
 *   passes its own, so the engine outlives the globe/map toggle as well.
 */
@Composable
internal fun GlobeSurface(
    ocean: SphereMesh,
    countries: List<NamedCountryMesh>,
    outlineSectors: List<OutlineMesh>,
    microstateDots: List<MicrostateDot>,
    colorFor: (String) -> GlobeFill?,
    earthTexture: Bitmap?,
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
    visible: Boolean = true,
    host: GlobeSurfaceHost = rememberGlobeSurfaceHost(backgroundColor),
) {
    val sizes = rememberMarkerSizes()

    // The background is a uniform, not a rebuild: this engine outlives a theme
    // change now, so the skybox has to be repainted in place.
    SideEffect { host.setBackgroundColor(backgroundColor.toFilamentColor()) }

    // Nobody looking, nobody paying: a globe behind another tab, or in an app
    // that has gone to the background, stops posting frames. Everything the
    // frames draw stays uploaded, so showing it again costs the next vsync.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    SideEffect {
        host.rendering = visible && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }

    // Geometry is uploaded once per mesh set; recoloring below never touches it.
    DisposableEffect(host, ocean, countries, outlineSectors, microstateDots) {
        host.setGeometry(ocean, countries, outlineSectors, microstateDots)
        onDispose { }
    }

    // A texture upload, so only when the image changes rather than on every
    // recolor. After the geometry, whose ocean it is bound to.
    DisposableEffect(host, earthTexture, oceanColor) {
        host.setEarthTexture(earthTexture, oceanColor)
        onDispose { }
    }

    // Resolved during composition on purpose: reading visited/wishlist/selection
    // here is what subscribes this composable to them, so a tap or a toggle
    // recomposes and repaints. Applying them is deferred to a SideEffect,
    // because on first composition the material instances do not exist until the
    // DisposableEffect above has uploaded the geometry.
    val fills = countries.map { colorFor(it.name) }
    SideEffect { host.applyColors(countries, fills, dotStyles, sizes) }

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
        modifier = if (visible) {
            modifier.globeGestures(host, hitTester, sizes, onCountryTapped)
        } else {
            // Hidden, so no gesture handlers at all — not merely ignored ones.
            // A pointer node here would be hit before the screen drawn beneath
            // this one and would swallow taps meant for it.
            modifier
        },
        factory = { context ->
            TextureView(context).also { view -> host.attach(view) }
        },
        onRelease = { host.detachView() },
    )
}

/**
 * The globe's engine, created once and destroyed once, on the thread that owns
 * it — the main thread, where composition and the render loop both run.
 *
 * Held by a composable rather than a `ViewModel` on purpose: the host owns a
 * `TextureView` and its surface, and a `ViewModel` outlives the Activity those
 * belong to. What it needs instead is a composition that outlives the *screen* —
 * on Home that is `HomeScreen` itself, which since 7.11 is composed outside the
 * `NavHost` and hidden rather than removed when another tab is chosen.
 *
 * Built with the screen rather than with the globe, so it is ready before the
 * geometry is and a session that starts on the flat map pays for it too. That is
 * the cheap half of the engine — `Engine.create()` and three materials whose
 * shaders were compiled once for the process — while the expensive half, the
 * meshes and the Earth texture, is uploaded only when something asks to draw.
 */
@Composable
internal fun rememberGlobeSurfaceHost(backgroundColor: androidx.compose.ui.graphics.Color): GlobeSurfaceHost {
    val host = remember { GlobeSurfaceHost(backgroundColor.toFilamentColor()) }
    DisposableEffect(host) {
        onDispose { host.destroy() }
    }
    return host
}

/**
 * Every gesture the globe answers, in the order they have to be layered.
 *
 * Written as one chain so the globe can be handed the whole set or none of it:
 * a hidden globe takes no touches, and that has to mean no pointer nodes rather
 * than nodes that decline, because Compose stops at the first sibling it hits.
 */
private fun Modifier.globeGestures(
    host: GlobeSurfaceHost,
    hitTester: CountryHitTester,
    sizes: MarkerSizes,
    onCountryTapped: (String?) -> Unit,
): Modifier = this
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
    .pointerInput(host, hitTester, sizes) {
        detectTapGestures { offset ->
            onCountryTapped(host.countryAt(offset.x, offset.y, hitTester, sizes))
        }
    }
    .pointerInput(host) {
        // Last in the chain on purpose, which makes it the innermost
        // handler: Compose delivers the main pass inwards-out, so this
        // sees every event before the detectors above it and its
        // consumption is what keeps a zoom drag from also turning the
        // globe. See `detectZoomDrags`.
        detectZoomDrags(host)
    }

/**
 * Binds a [GlobeRenderer] to a [TextureView] and a Choreographer loop.
 *
 * Kept out of the composable so the engine's lifetime is tied to one object
 * that `DisposableEffect` can destroy, rather than to several remembered values
 * that would have to be torn down in the right order. It is deliberately
 * longer-lived than the surface it draws to: see [rememberGlobeSurfaceHost] for
 * where it is owned, [attach] for what a new `TextureView` costs, and
 * [setGeometry] for what it does not.
 */
internal class GlobeSurfaceHost(backgroundColor: FloatArray) {

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

    /** Where a one-finger zoom drag started from; only read while one is live. */
    private var zoomDragStartDistance = camera.distance

    /** Reported to the composable after every camera move, coasting included. */
    var onCameraChange: (GlobeCamera) -> Unit = {}

    /** Reported once at the start of each drag or zoom. */
    var onInteraction: () -> Unit = {}

    /** Whether to keep turning the globe when nothing else is moving it. */
    var autoRotating: Boolean = false

    /**
     * Whether frames are being drawn.
     *
     * Turning it off leaves the engine, its geometry and its surface exactly
     * where they are and only stops asking the [Choreographer] for frames —
     * which is what lets the globe be left behind another tab for free and come
     * back on the next vsync. The frame clock resets with it, so the first frame
     * after a pause does not hand [advance] a step measured from before it.
     */
    var rendering: Boolean = false
        set(value) {
            if (field == value || destroyed) return
            field = value
            if (value) {
                lastFrameNanos = 0L
                choreographer.postFrameCallback(frameCallback)
            } else {
                choreographer.removeFrameCallback(frameCallback)
            }
        }

    /** The view currently bound to the engine's swap chain, or null. */
    private var attachedView: TextureView? = null

    /**
     * The geometry the GPU is already holding, compared by identity.
     *
     * The meshes come from the process-wide `GlobeGeometryCache`, so the same
     * four objects arriving again means the upload can be skipped — which is
     * what keeps a trip to the flat map and back from re-uploading 181 meshes
     * onto an engine that never went anywhere.
     */
    private var uploadedOcean: SphereMesh? = null
    private var uploadedCountries: List<NamedCountryMesh>? = null
    private var uploadedOutlines: List<OutlineMesh>? = null
    private var uploadedDots: List<MicrostateDot>? = null

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

    /**
     * A one-finger zoom drag began. Takes the camera away from everything else
     * that might be moving it, and records the distance the drag measures from
     * — iOS's `doubleTapDragStartDistance`.
     */
    fun beginZoomDrag() {
        onInteraction()
        stopMotion()
        zoomDragStartDistance = camera.distance
    }

    /** [travelDp] is the finger's vertical travel since [beginZoomDrag]. */
    fun zoomDrag(travelDp: Float) {
        updateCamera { it.zoomDraggedBy(zoomDragStartDistance, travelDp) }
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

    /**
     * The country under a tap, or null for a tap that missed the globe.
     *
     * A microstate is hit anywhere its dot is drawn: the dot's radius, as arc,
     * is the hit radius — which only differs from the default when zoomed out,
     * where the dot holds a minimum size on screen.
     */
    fun countryAt(x: Float, y: Float, hitTester: CountryHitTester, sizes: MarkerSizes): String? {
        val latLon = camera.latLonAt(x, y, viewportWidth, viewportHeight) ?: return null
        val dotRadius = camera.dotRadiusInWorld(sizes.dotRadiusPx, viewportHeight) / GlobeCamera.GLOBE_RADIUS
        return hitTester.findCountry(latLon.lat, latLon.lon, pointHitRadius = Math.toDegrees(dotRadius.toDouble()))
    }

    /**
     * Binds the engine to [view]'s surface, releasing any previous one.
     *
     * Since 7.11 this runs once per Activity for the globe on Home: its view is
     * composed outside the `NavHost` and hidden rather than removed, so a tab
     * switch never detaches it. Switching to the flat map and back still builds
     * a new view — but only a swap chain is rebuilt with it, not the engine.
     */
    fun attach(view: TextureView) {
        if (destroyed) return
        detachView()
        attachedView = view
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
    }

    /** Gives up the surface. The engine and everything uploaded to it stay. */
    fun detachView() {
        if (attachedView == null) return
        attachedView = null
        uiHelper.detach()
    }

    /** Repaints the background behind the globe, for a theme change. */
    fun setBackgroundColor(color: FloatArray) {
        if (!destroyed) renderer.setBackgroundColor(color)
    }

    fun setGeometry(
        ocean: SphereMesh,
        countries: List<NamedCountryMesh>,
        outlines: List<OutlineMesh>,
        microstateDots: List<MicrostateDot>,
    ) {
        if (destroyed) return
        val alreadyUploaded = ocean === uploadedOcean &&
            countries === uploadedCountries &&
            outlines === uploadedOutlines &&
            microstateDots === uploadedDots
        if (alreadyUploaded) return
        uploadedOcean = ocean
        uploadedCountries = countries
        uploadedOutlines = outlines
        uploadedDots = microstateDots
        renderer.setGeometry(ocean, countries, outlines, microstateDots)
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

    fun setEarthTexture(image: Bitmap?, fallback: androidx.compose.ui.graphics.Color) {
        if (!destroyed) renderer.setEarthTexture(image, fallback)
    }

    fun applyColors(
        countries: List<NamedCountryMesh>,
        fills: List<GlobeFill?>,
        dotStyles: List<GlobeDotStyle>,
        sizes: MarkerSizes,
    ) {
        if (destroyed) return
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

    /** Runs exactly once, on the main thread — the one that created the engine. */
    fun destroy() {
        if (destroyed) return
        rendering = false
        destroyed = true
        detachView()
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
            // Consumed ones belong to the zoom drag, which moves the camera in
            // and out rather than around — measuring it here would leave the
            // globe spinning north or south when the finger lifts.
            val moving = event.changes.filter { it.pressed && it.previousPressed && !it.isConsumed }
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

/**
 * Tap, then press and drag vertically: a one-finger zoom, ported from iOS's
 * `handleDoubleTapDrag`.
 *
 * Dragging **down** zooms in, the direction Google Maps uses on Android and the
 * opposite of iOS — the gesture is a port, its direction deliberately is not.
 * [GlobeCamera.zoomDraggedBy] holds that decision.
 *
 * Runs as the innermost pointer handler on the globe, so it sees each event on
 * the main pass before the rotate detector, the flick tracker and the tap
 * detector do. Consuming is what makes the three of them stand down:
 * `detectTransformGestures` abandons a gesture the moment a change is consumed,
 * `detectTapGestures` reports no tap, and `trackFlicks` skips consumed changes,
 * so a gesture meant only to zoom neither turns the globe nor leaves it
 * spinning.
 *
 * Nothing happens until the second touch actually moves, so a plain double tap
 * stays a plain double tap and leaves the idle spin alone.
 */
private suspend fun PointerInputScope.detectZoomDrags(host: GlobeSurfaceHost) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        // A first touch that is a tap: back up quickly, and from where it
        // landed. A touch that is held or travels is a rotation, and belongs to
        // the detector above — iOS spends `minimumPressDuration` and
        // `allowableMovement` on the same two questions.
        val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        } ?: return@awaitEachGesture
        if ((firstUp.position - firstDown.position).getDistance() > viewConfiguration.touchSlop) {
            return@awaitEachGesture
        }

        // ...and a second touch soon enough after it to be the same gesture.
        val press = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false)
        } ?: return@awaitEachGesture

        var travelDp = 0f
        var zooming = false
        while (true) {
            val event = awaitPointerEvent()
            val finger = event.changes.firstOrNull { it.id == press.id } ?: break
            travelDp += (finger.position.y - finger.previousPosition.y) / density
            if (!zooming && travelDp != 0f) {
                host.beginZoomDrag()
                zooming = true
            }
            if (zooming) {
                host.zoomDrag(travelDp)
                // Every change, not only the zooming finger: a second finger
                // landing mid-drag would otherwise reach the rotate detector as
                // the beginning of a pinch.
                event.changes.forEach { it.consume() }
            }
            if (!finger.pressed) break
        }
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
