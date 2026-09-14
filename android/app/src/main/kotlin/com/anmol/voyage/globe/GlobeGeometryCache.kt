package com.anmol.voyage.globe

import android.content.Context
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
 * The globe's geometry, assembled once per process — the globe's counterpart to
 * [CountryDataCache], and the reason returning to the Home tab is instant.
 *
 * It used to be held by the composable that drew it, so leaving Home — to
 * another tab, or just to the flat map — threw it away and paid for it again on
 * the way back. Nothing about the result depends on the UI: it is a pure
 * function of `world.geojson`, which never changes at runtime. So it lives
 * here, like the parsed countries do, and outlives any composition.
 *
 * The expensive part, [WorldMeshes], is not built on the device either: once
 * [install] has run it is read from the `world_meshes.bin` the build generated,
 * the way iOS ships a prebuilt `globe.scn`. Without [install] — in JVM unit
 * tests — it is triangulated, which is the same code the build ran.
 */
object GlobeGeometryCache {

    @Volatile
    private var cached: GlobeGeometry? = null

    private val lock = Any()

    @Volatile
    private var loadMeshes: (List<GeoJsonCountry>) -> WorldMeshes = WorldMeshes::triangulate

    /** Loads the meshes from the build's prebuilt asset rather than triangulating them. */
    fun install(context: Context) {
        val assets = context.applicationContext.assets
        loadMeshes = { _ -> assets.open(WorldMeshesFile.NAME).use(WorldMeshesFile::read) }
    }

    /**
     * The globe's geometry, loading it on first call.
     *
     * Callers must be off the main thread the first time: reading the meshes is
     * still a sizeable read. Later calls return the cached value immediately,
     * which is what makes coming back to the globe free.
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
     * Starts loading off the main thread at app start, so the globe is usually
     * ready before the first frame that wants it — the same trick
     * [CountryDataCache.prewarm] plays for the countries, and it queues behind
     * that one on the countries lazy.
     */
    fun prewarm() {
        thread(name = "globe-geometry-prewarm", isDaemon = true) {
            runCatching { get(CountryDataCache.shared.countries) }
                .onFailure { Log.w(TAG, "globe geometry prewarm failed", it) }
        }
    }

    private fun build(countries: List<GeoJsonCountry>): GlobeGeometry {
        val started = SystemClock.elapsedRealtime()
        val meshes = loadMeshes(countries)
        // Point-feature microstates have no fill and no border: they are dots,
        // built here because 25 of them take no time worth caching.
        val dots = countries.mapNotNull { country ->
            val at = country.pointCoordinate ?: return@mapNotNull null
            MicrostateDot(
                name = country.name,
                ring = MarkerMeshes.ring(at.lat, at.lon, DOT_RING_RADIUS),
                fill = MarkerMeshes.disc(at.lat, at.lon, DOT_FILL_RADIUS),
            )
        }
        val geometry = GlobeGeometry(
            ocean = UvSphere.build(),
            countries = meshes.countries,
            outlineSectors = meshes.outlineSectors,
            microstateDots = dots,
        )
        Log.i(
            TAG,
            "built ${meshes.countries.size} countries and ${meshes.outlineSectors.size} outline sectors " +
                "(${meshes.outlineSectors.sumOf { it.vertexCount }} outline vertices) " +
                "in ${SystemClock.elapsedRealtime() - started} ms",
        )
        return geometry
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
