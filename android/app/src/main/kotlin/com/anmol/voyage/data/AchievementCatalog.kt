package com.anmol.voyage.data

/**
 * Builds the achievement list from what the user has marked — the Kotlin
 * analogue of the `achievements` computed property on the iOS
 * `AchievementsView`.
 *
 * It takes the marked sets rather than the app state itself, so it stays in the
 * data layer (which nothing in `state` may depend on backwards) and so the whole
 * catalog is testable on the JVM without a ViewModel or a renderer. The order of
 * the returned list is the order the screen shows, and matches iOS.
 */
object AchievementCatalog {

    fun of(
        cache: CountryDataCache,
        visited: Set<String>,
        checkedCities: Map<String, Set<String>>,
        checkedAttractions: Map<String, Set<String>>,
    ): List<Achievement> {
        val unCountries = UnMembership.membersOf(cache.countryNames)

        return buildList {
            add(globetrotter(unCountries, visited))
            add(capitalCollector(cache, unCountries, checkedCities))
            add(wonders(checkedAttractions))
            add(continentalDrifter(cache, visited))
            // Antarctica has no explorer medal of its own — it counts only
            // toward Continental Drifter, as on iOS.
            Continent.entries.filterNot { it == Continent.ANTARCTICA }
                .mapTo(this) { explorer(cache, it, visited) }
        }
    }

    /**
     * Bronze and silver limits per achievement; gold is all of it. iOS's
     * `AchievementCatalog` holds the same table, and `AchievementTest` pins it.
     */
    internal val GLOBETROTTER = MedalThresholds(bronze = 25, silver = 100)
    internal val CAPITAL_COLLECTOR = MedalThresholds(bronze = 25, silver = 100)
    internal val WONDERS = MedalThresholds(bronze = 2, silver = 5)
    internal val CONTINENTAL_DRIFTER = MedalThresholds(bronze = 2, silver = 4)

    internal fun explorerThresholds(continent: Continent) = when (continent) {
        Continent.AFRICA -> MedalThresholds(bronze = 10, silver = 30)
        Continent.ASIA -> MedalThresholds(bronze = 10, silver = 25)
        Continent.EUROPE -> MedalThresholds(bronze = 10, silver = 25)
        Continent.NORTH_AMERICA -> MedalThresholds(bronze = 5, silver = 15)
        Continent.SOUTH_AMERICA -> MedalThresholds(bronze = 3, silver = 8)
        Continent.OCEANIA -> MedalThresholds(bronze = 3, silver = 8)
        // No explorer medal; listed so the `when` stays exhaustive.
        Continent.ANTARCTICA -> MedalThresholds(bronze = 1, silver = 1)
    }

    /**
     * Every UN state. Both halves are intersected with the dataset rather than
     * subtracted from the visited set: a name saved by an older version and
     * since renamed would otherwise be counted as visited *and* leave its
     * current name outstanding, inflating the total past 195.
     */
    private fun globetrotter(unCountries: Set<String>, visited: Set<String>) = Achievement(
        kind = AchievementKind.Globetrotter,
        earned = (unCountries intersect visited).sorted(),
        remaining = (unCountries - visited).sorted(),
        thresholds = GLOBETROTTER,
    )

    /** The capital of every UN state that has one, ticked off in its city list. */
    private fun capitalCollector(
        cache: CountryDataCache,
        unCountries: Set<String>,
        checkedCities: Map<String, Set<String>>,
    ): Achievement {
        val withCapitals = cache.countries.filter { it.capital != null && it.name in unCountries }
        val (checked, unchecked) = withCapitals.partition { country ->
            country.capital!!.name in checkedCities[country.name].orEmpty()
        }
        return Achievement(
            kind = AchievementKind.CapitalCollector,
            earned = checked.map { it.capital!!.name }.sorted(),
            remaining = unchecked.map { it.capital!!.name }.sorted(),
            thresholds = CAPITAL_COLLECTOR,
        )
    }

    private fun wonders(checkedAttractions: Map<String, Set<String>>) = Achievement(
        kind = AchievementKind.Wonders,
        earned = WondersOfTheWorld.visited(checkedAttractions),
        remaining = WondersOfTheWorld.remaining(checkedAttractions),
        thresholds = WONDERS,
    )

    /** One item per continent, earned by setting foot on all seven. */
    private fun continentalDrifter(cache: CountryDataCache, visited: Set<String>) = Achievement(
        kind = AchievementKind.ContinentalDrifter,
        earned = cache.continents.visitedContinentNames(visited),
        remaining = cache.continents.remainingContinentNames(visited),
        thresholds = CONTINENTAL_DRIFTER,
    )

    /** Every country on one continent, territories included. */
    private fun explorer(
        cache: CountryDataCache,
        continent: Continent,
        visited: Set<String>,
    ): Achievement {
        val countries = cache.continents.countries(of = continent)
        return Achievement(
            kind = AchievementKind.Explorer(continent),
            earned = (countries intersect visited).sorted(),
            remaining = (countries - visited).sorted(),
            thresholds = explorerThresholds(continent),
        )
    }
}
