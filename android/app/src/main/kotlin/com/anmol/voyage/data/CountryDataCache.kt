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
 * thread loads and the rest wait. Call sites should go through [shared] rather
 * than loading again.
 *
 * Assets are supplied as a lambda rather than a [Context] so unit tests can read
 * the very same files straight from `shared/data/`. The countries are the
 * exception: the app reads them from `countries.bin` ([CountriesFile]), which the
 * build generates from `world.geojson` rather than parsing it on the device, and
 * which is not a file tests can open — so they pass [loadCountries] instead.
 */
class CountryDataCache(
    private val openAsset: (String) -> InputStream,
    loadCountries: () -> List<GeoJsonCountry> = {
        openAsset(CountriesFile.NAME).use(CountriesFile::read)
    },
) {

    /** All countries, in `world.geojson` feature order. */
    val countries: List<GeoJsonCountry> by lazy(loadCountries)

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

    /** Tap-to-country lookup, shared by the map and (from Phase 7) the globe. */
    val hitTester: CountryHitTester by lazy { CountryHitTester(countries) }

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
            instance = CountryDataCache({ name -> assets.open(name) })
        }

        /**
         * Starts loading the countries off the main thread so it overlaps the
         * rest of startup instead of blocking the first map/globe render — the
         * same trick `voyageApp.init` plays on iOS.
         */
        fun prewarm() {
            val cache = instance ?: return
            thread(name = "country-data-prewarm", isDaemon = true) {
                val started = SystemClock.elapsedRealtime()
                val count = cache.countries.size
                Log.i(TAG, "loaded $count countries in ${SystemClock.elapsedRealtime() - started} ms")
            }
        }

        private const val TAG = "CountryDataCache"
    }
}
