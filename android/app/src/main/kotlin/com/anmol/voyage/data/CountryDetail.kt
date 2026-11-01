package com.anmol.voyage.data

/**
 * Everything the country details UI shows about one country, gathered in one
 * place: the country's own fields, its flag, and its highlights.
 *
 * The join is here rather than in the composable because it crosses two parsed
 * files — countries are keyed by name, highlights by ISO code — and because
 * building it touches `country_highlights.json`, which is parsed lazily and so
 * belongs off the main thread the first time. It is also the shape the iOS
 * `CountryExploreView` assembles inline from `CountryDataCache`; having it as a
 * value makes it testable without a renderer.
 */
data class CountryDetail(
    val name: String,
    val isoCode: String?,
    val flag: String,
    /**
     * Every capital, the main one first (La Paz, then Sucre). Empty for the few
     * features that have none (Antarctica).
     */
    val capitals: List<String>,
    val continent: String?,
    val cities: List<String>,
    val attractions: List<String>,
) {

    val hasHighlights: Boolean get() = cities.isNotEmpty() || attractions.isNotEmpty()

    /** The capital the country is best known by. */
    val capital: String? get() = capitals.firstOrNull()

    companion object {

        /** The detail for [name], or null when no country goes by it. */
        fun of(cache: CountryDataCache, name: String): CountryDetail? =
            cache.countryNamed(name)?.let { of(cache, it) }

        fun of(cache: CountryDataCache, country: GeoJsonCountry): CountryDetail {
            val highlights = country.isoCode?.let(cache::highlights)
            return CountryDetail(
                name = country.name,
                isoCode = country.isoCode,
                flag = FlagEmoji.of(country),
                capitals = country.capitalNames,
                continent = country.continent,
                cities = highlights?.cities.orEmpty(),
                attractions = highlights?.attractions.orEmpty(),
            )
        }
    }
}
