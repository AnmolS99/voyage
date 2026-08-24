package com.anmol.voyage.ui.globe

import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.anmol.voyage.data.CountryHitTester
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.SphereMesh
import com.google.android.filament.android.UiHelper

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
                    host.updateCamera { camera ->
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
            .pointerInput(host, hitTester) {
                detectTapGestures { offset ->
                    onCountryTapped(host.countryAt(offset.x, offset.y, hitTester))
                }
            },
        factory = { context ->
            SurfaceView(context).also { surfaceView -> host.attach(surfaceView) }
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

    fun updateCamera(transform: (GlobeCamera) -> GlobeCamera) {
        camera = transform(camera)
    }

    /** The country under a tap, or null for a tap that missed the globe. */
    fun countryAt(x: Float, y: Float, hitTester: CountryHitTester): String? {
        val latLon = camera.latLonAt(x, y, viewportWidth, viewportHeight) ?: return null
        return hitTester.findCountry(latLon.lat, latLon.lon)
    }

    fun attach(surfaceView: SurfaceView) {
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
        uiHelper.attachTo(surfaceView)
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
