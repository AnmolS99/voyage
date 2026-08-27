package com.anmol.voyage.globe

import android.os.SystemClock
import android.util.Log
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.GeoJsonCountry
import kotlin.concurrent.thread

/**
 * Everything the globe draws, as buffers ready to hand to a renderer.
 *
 * @property outlineSectors The world's borders, split into longitude sectors so
 *   the renderer can drop the ones past the globe's horizon. Every country's
 *   rings are in exactly one sector; together they are the same mesh a single
 *   merged outline would be.
 */
class GlobeGeometry(
    val ocean: SphereMesh,
    val countries: List<NamedCountryMesh>,
    val outlineSectors: List<OutlineMesh>,
    /** One dot per Point-feature microstate, keyed by name so it can be recolored. */
    val microstateDots: List<MicrostateDot>,
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
        // Point-feature microstates produce no fill mesh and no border — they
        // are dots instead, built just below.
        val polygonCountries = countries.filter { !it.isPointCountry }
        val dots = countries.mapNotNull { country ->
            val at = country.pointCoordinate ?: return@mapNotNull null
            MicrostateDot(
                name = country.name,
                ring = MarkerMeshes.disc(at.lat, at.lon, DOT_RING_RADIUS),
                fill = MarkerMeshes.disc(at.lat, at.lon, DOT_FILL_RADIUS),
            )
        }
        val meshes = polygonCountries.mapNotNull { country ->
            PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
                ?.let { NamedCountryMesh(country.name, it) }
        }
        val outlines = PolygonTriangulator.createSectoredOutlineGeometries(
            polygonCountries.flatMap { it.polygons },
        )
        Log.i(
            TAG,
            "triangulated ${meshes.size} countries and ${outlines.size} outline sectors " +
                "(${outlines.sumOf { it.vertexCount }} outline vertices) " +
                "in ${SystemClock.elapsedRealtime() - started} ms",
        )
        return GlobeGeometry(
            ocean = UvSphere.build(),
            countries = meshes,
            outlineSectors = outlines,
            microstateDots = dots,
        )
    }

    /**
     * Sphere radii for the two layers of a dot, above the fills (1.003) and the
     * borders (1.005) so a dot is never buried by the country under it. The ring
     * sits just below its fill so the two never z-fight.
     */
    private const val DOT_RING_RADIUS = 1.0058f
    private const val DOT_FILL_RADIUS = 1.0062f

    private const val TAG = "GlobeGeometryCache"
}
