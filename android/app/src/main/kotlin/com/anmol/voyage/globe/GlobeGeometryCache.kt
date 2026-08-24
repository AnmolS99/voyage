package com.anmol.voyage.globe

import android.os.SystemClock
import android.util.Log
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.GeoJsonCountry
import kotlin.concurrent.thread

/** Everything the globe draws, as buffers ready to hand to a renderer. */
class GlobeGeometry(
    val ocean: SphereMesh,
    val countries: List<NamedCountryMesh>,
)

/**
 * The triangulated world, built once per process — the globe's counterpart to
 * [CountryDataCache], and the reason returning to the Home tab is instant.
 *
 * Triangulating 181 countries costs ~300 ms on a Pixel 9 emulator. It used to
 * be held by the composable that drew it, so leaving Home — to another tab, or
 * just to the flat map — threw it away and paid that again on the way back.
 * Nothing about the result depends on the UI: it is a pure function of
 * `world.geojson`, which never changes at runtime. So it lives here, like the
 * parsed countries do, and outlives any composition.
 *
 * iOS gets the same effect a different way, by shipping a prebuilt `globe.scn`;
 * the Android equivalent (a binary geometry cache produced at build time) is
 * Phase 7.8 and would cut the *first* build too, not just repeats.
 */
object GlobeGeometryCache {

    @Volatile
    private var cached: GlobeGeometry? = null

    private val lock = Any()

    /**
     * The globe's geometry, triangulating it on first call.
     *
     * Callers must be off the main thread the first time: the work is hundreds
     * of milliseconds. Later calls return the cached value immediately, which
     * is what makes coming back to the globe free.
     */
    fun get(countries: List<GeoJsonCountry>): GlobeGeometry {
        cached?.let { return it }
        return synchronized(lock) {
            // Re-check: another thread may have built it while this one waited.
            cached ?: build(countries).also { cached = it }
        }
    }

    /** Whether [get] would return immediately. */
    val isReady: Boolean get() = cached != null

    /**
     * Starts triangulation off the main thread at app start, so the globe is
     * usually ready before the first frame that wants it — the same trick
     * [CountryDataCache.prewarm] plays for parsing, and it queues behind that
     * one on the countries lazy.
     */
    fun prewarm() {
        thread(name = "globe-geometry-prewarm", isDaemon = true) {
            runCatching { get(CountryDataCache.shared.countries) }
                .onFailure { Log.w(TAG, "globe geometry prewarm failed", it) }
        }
    }

    private fun build(countries: List<GeoJsonCountry>): GlobeGeometry {
        val started = SystemClock.elapsedRealtime()
        // Point-feature microstates produce no fill mesh — they are dots, and
        // get their markers with the capital stars in Phase 7.5.
        val meshes = countries.mapNotNull { country ->
            PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
                ?.let { NamedCountryMesh(country.name, it) }
        }
        Log.i(TAG, "triangulated ${meshes.size} countries in ${SystemClock.elapsedRealtime() - started} ms")
        return GlobeGeometry(ocean = UvSphere.build(), countries = meshes)
    }

    private const val TAG = "GlobeGeometryCache"
}
