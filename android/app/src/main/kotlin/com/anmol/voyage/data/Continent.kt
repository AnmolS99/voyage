package com.anmol.voyage.data

/**
 * The seven continents, in the same order as the iOS `Continent.allCases` —
 * that order is user-visible wherever continents are listed.
 */
enum class Continent(val displayName: String, val medal: String) {
    AFRICA("Africa", "🦁"),
    ASIA("Asia", "🐉"),
    EUROPE("Europe", "🏰"),
    NORTH_AMERICA("North America", "🦅"),
    SOUTH_AMERICA("South America", "🦜"),
    OCEANIA("Oceania", "🐨"),
    ANTARCTICA("Antarctica", "🐧");

    companion object {
        /** Resolves the raw `continent` string carried by a GeoJSON feature. */
        fun fromRaw(raw: String): Continent? = entries.firstOrNull { it.displayName == raw }
    }
}

/**
 * Which countries belong to which continent — the Kotlin analogue of the iOS
 * `ContinentData`. GeoJSON is the single source of truth for the grouping, so
 * this is built from parsed countries rather than a hardcoded table.
 */
class ContinentIndex(countries: List<GeoJsonCountry>) {

    /** Country names per continent; every continent is present, possibly empty. */
    val countriesByContinent: Map<Continent, List<String>> =
        Continent.entries.associateWith { mutableListOf<String>() }.also { mapping ->
            countries.forEach { country ->
                val continent = country.continent?.let(Continent::fromRaw) ?: return@forEach
                mapping.getValue(continent).add(country.name)
            }
        }

    private val countryNames: Map<Continent, Set<String>> =
        countriesByContinent.mapValues { (_, names) -> names.toSet() }

    private val continentByCountry: Map<String, Continent> = buildMap {
        countriesByContinent.forEach { (continent, names) ->
            names.forEach { put(it, continent) }
        }
    }

    fun countries(of: Continent): Set<String> = countryNames.getValue(of)

    fun continentOf(country: String): Continent? = continentByCountry[country]

    fun visitedCountries(continent: Continent, visited: Set<String>): Set<String> =
        visited intersect countries(of = continent)

    /** A continent counts as visited once any single country on it has been visited. */
    fun hasVisited(continent: Continent, visited: Set<String>): Boolean =
        countries(of = continent).any { it in visited }

    /**
     * Continents with at least one visited country, in [Continent.entries] order.
     * Antarctica is included even though it has no "Explorer of" achievement of
     * its own — the Continental Drifter achievement requires all seven.
     */
    fun visitedContinentNames(visited: Set<String>): List<String> =
        Continent.entries.filter { hasVisited(it, visited) }.map { it.displayName }

    /** Continents with no visited country, in [Continent.entries] order. */
    fun remainingContinentNames(visited: Set<String>): List<String> =
        Continent.entries.filterNot { hasVisited(it, visited) }.map { it.displayName }
}
