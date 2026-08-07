package com.anmol.voyage.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * Parsed country data, loaded once — the Kotlin analogue of the iOS
 * `CountryDataCache`.
 *
 * Everything is `by lazy`, which is synchronized, so the first caller from any
 * thread parses and the rest wait. Call sites should go through [shared] rather
 * than re-parsing.
 *
 * Assets are supplied as a lambda rather than a [Context] so unit tests can read
 * the very same files straight from `shared/data/`.
 */
class CountryDataCache(private val openAsset: (String) -> InputStream) {

    /** All countries, in `world.geojson` feature order. */
    val countries: List<GeoJsonCountry> by lazy {
        GeoJsonParser.parse(openAsset(WORLD_GEOJSON))
    }

    /** Country names, for quick membership checks. */
    val countryNames: Set<String> by lazy {
        countries.mapTo(HashSet(countries.size)) { it.name }
    }

    /** Country highlights keyed by ISO code. Parsed on first use, like iOS. */
    val countryHighlights: Map<String, CountryHighlights> by lazy {
        CountryHighlightsParser.parse(openAsset(COUNTRY_HIGHLIGHTS))
    }

    /** Countries grouped by continent. */
    val continents: ContinentIndex by lazy { ContinentIndex(countries) }

    private val countriesByName: Map<String, GeoJsonCountry> by lazy {
        countries.associateBy { it.name }
    }

    private val countriesByIsoCode: Map<String, GeoJsonCountry> by lazy {
        countries.mapNotNull { country -> country.isoCode?.let { it to country } }.toMap()
    }

    fun countryNamed(name: String): GeoJsonCountry? = countriesByName[name]

    fun countryWithIsoCode(isoCode: String): GeoJsonCountry? = countriesByIsoCode[isoCode]

    fun highlights(isoCode: String): CountryHighlights? = countryHighlights[isoCode]

    companion object {
        const val WORLD_GEOJSON = "world.geojson"
        const val COUNTRY_HIGHLIGHTS = "country_highlights.json"

        @Volatile
        private var instance: CountryDataCache? = null

        /**
         * The process-wide cache. [install] must have run first — it does, from
         * `VoyageApplication.onCreate`.
         */
        val shared: CountryDataCache
            get() = checkNotNull(instance) { "CountryDataCache.install(context) has not run" }

        fun install(context: Context) {
            val assets = context.applicationContext.assets
            instance = CountryDataCache { name -> assets.open(name) }
        }

        /**
         * Starts GeoJSON parsing off the main thread so it overlaps the rest of
         * startup instead of blocking the first map/globe render — the same trick
         * `voyageApp.init` plays on iOS.
         */
        fun prewarm() {
            val cache = instance ?: return
            thread(name = "country-data-prewarm", isDaemon = true) {
                val started = SystemClock.elapsedRealtime()
                val count = cache.countries.size
                Log.i(TAG, "parsed $count countries in ${SystemClock.elapsedRealtime() - started} ms")
            }
        }

        private const val TAG = "CountryDataCache"
    }
}
