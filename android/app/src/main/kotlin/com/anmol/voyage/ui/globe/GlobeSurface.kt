package com.anmol.voyage.ui.globe

import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.NamedCountryMesh
import com.anmol.voyage.globe.SphereMesh
import com.google.android.filament.android.UiHelper
import kotlin.math.exp

/**
 * The globe's rendering surface: a Filament-backed [SurfaceView] hosted in
 * Compose, with the rotate / pinch / tap gestures on top of it.
 *
 * The render loop is driven by [Choreographer] rather than a background thread,
 * which is what Filament's own Android samples do: it keeps every Engine call
 * on one thread and paces frames to the display's vsync for free.
 */
@Composable
internal fun GlobeSurface(
    ocean: SphereMesh,
    countries: List<NamedCountryMesh>,
    colorFor: (String) -> GlobeFill,
    oceanColor: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
    hitTester: CountryHitTester,
    onCountryTapped: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onCameraChange: (GlobeCamera) -> Unit = {},
) {
    val host = remember(backgroundColor) { GlobeSurfaceHost(backgroundColor.toFilamentColor()) }

    DisposableEffect(host) {
        onDispose { host.destroy() }
    }

    // Geometry is uploaded once per mesh set; recoloring below never touches it.
    DisposableEffect(host, ocean, countries) {
        host.setGeometry(ocean, countries)
        onDispose { }
    }

    // Resolved during composition on purpose: reading visited/wishlist/selection
    // here is what subscribes this composable to them, so a tap or a toggle
    // recomposes and repaints. Applying them is deferred to a SideEffect,
    // because on first composition the material instances do not exist until the
    // DisposableEffect above has uploaded the geometry.
    val fills = countries.map { colorFor(it.name) }
    SideEffect { host.applyColors(countries, fills, oceanColor) }

    AndroidView(
        modifier = modifier
            .pointerInput(host) {
                detectTransformGestures { _, pan, zoom, _ ->
                    host.updateCamera(onCameraChange) { camera ->
                        val degreesPerPixel = camera.degreesPerPixel(size.height.toFloat())
                        camera
                            // Dragging right spins the globe east-to-west under
                            // the finger, so the surface tracks the touch rather
                            // than running away from it.
                            .rotatedBy(
                                deltaLatitude = pan.y * degreesPerPixel,
                                deltaLongitude = -pan.x * degreesPerPixel,
                            )
                            .zoomedBy(zoom)
                    }
                }
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
                        host.updateCamera(onCameraChange) { it.zoomedBy(zoomForScroll(scroll)) }
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
 * Binds a [GlobeRenderer] to a SurfaceView and a Choreographer loop.
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
     */
    private var camera = GlobeCamera()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return
            choreographer.postFrameCallback(this)
            renderer.setCamera(camera)
            renderer.render(frameTimeNanos)
        }
    }

    fun updateCamera(onCameraChange: (GlobeCamera) -> Unit, transform: (GlobeCamera) -> GlobeCamera) {
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

    fun setGeometry(ocean: SphereMesh, countries: List<NamedCountryMesh>) {
        if (!destroyed) renderer.setGeometry(ocean, countries)
    }

    fun applyColors(
        countries: List<NamedCountryMesh>,
        fills: List<GlobeFill>,
        oceanColor: androidx.compose.ui.graphics.Color,
    ) {
        if (destroyed) return
        renderer.setOceanColor(oceanColor.toFilamentColor())
        for ((index, country) in countries.withIndex()) {
            val fill = fills[index]
            renderer.setCountryColor(
                name = country.name,
                colorA = fill.colorA.toFilamentColor(),
                colorB = fill.colorB.toFilamentColor(),
                gradient = fill.gradient,
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
