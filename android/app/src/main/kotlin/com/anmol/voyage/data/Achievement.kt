package com.anmol.voyage.data

/**
 * What an achievement counts, so the UI can say "12/195 countries".
 *
 * iOS carries this as a free-text `itemLabel` on the achievement itself; here it
 * is an enum the UI resolves to a string resource, because the label is
 * user-visible text and every other user-visible string in this app is
 * translatable.
 */
enum class AchievementUnit { COUNTRIES, CAPITALS, WONDERS, CONTINENTS }

/**
 * Which achievement this is. The medal and the unit are derived from it rather
 * than stored alongside, so there is one place that says Europe's medal is a
 * castle and one that says the Wonders achievement counts wonders.
 */
sealed interface AchievementKind {

    /** Stable identity, for list keys and saved screen state. */
    val id: String

    /** Emoji struck on the medal's face. */
    val medal: String

    /** What its items are. */
    val unit: AchievementUnit

    /** Every UN member and observer state. */
    data object Globetrotter : AchievementKind {
        override val id = "globetrotter"
        override val medal = "🌍"
        override val unit = AchievementUnit.COUNTRIES
    }

    /** The capital city of every UN state that has one. */
    data object CapitalCollector : AchievementKind {
        override val id = "capital-collector"
        override val medal = "🏛️"
        override val unit = AchievementUnit.CAPITALS
    }

    /** The New 7 Wonders plus the honorary Pyramids of Giza. */
    data object Wonders : AchievementKind {
        override val id = "wonders"
        override val medal = "⭐️"
        override val unit = AchievementUnit.WONDERS
    }

    /** One country on each of the seven continents. */
    data object ContinentalDrifter : AchievementKind {
        override val id = "continental-drifter"
        override val medal = "🌐"
        override val unit = AchievementUnit.CONTINENTS
    }

    /** Every country on one continent. */
    data class Explorer(val continent: Continent) : AchievementKind {
        override val id = "explorer-${continent.name}"
        override val medal = continent.medal
        override val unit = AchievementUnit.COUNTRIES
    }
}

/**
 * One achievement and how far along it is — the Kotlin analogue of the iOS
 * `Achievement` struct.
 *
 * [earned] and [remaining] are the items themselves, not just counts, because
 * the card lists them when it is expanded. Both are sorted by the builder.
 */
data class Achievement(
    val kind: AchievementKind,
    val earned: List<String>,
    val remaining: List<String>,
) {

    val id: String get() = kind.id

    val medal: String get() = kind.medal

    val unit: AchievementUnit get() = kind.unit

    val current: Int get() = earned.size

    val total: Int get() = earned.size + remaining.size

    val isCompleted: Boolean get() = current >= total

    val progress: Float get() = if (total > 0) current.toFloat() / total else 0f

    /**
     * Progress as a whole percentage, rounded down.
     *
     * Integer division rather than `(progress * 100).toInt()`: it is the same
     * truncation iOS's `Int(progress * 100)` performs, without the cases where a
     * ratio lands a hair under the whole number it should be and truncates to
     * one percent less.
     */
    val percentage: Int get() = if (total > 0) current * 100 / total else 0
}

/**
 * The New 7 Wonders of the World plus the Pyramids of Giza — the only surviving
 * ancient wonder, which the New7Wonders campaign named an honorary eighth rather
 * than putting it to the vote. Each is paired with the country whose attraction
 * checklist (`country_highlights.json`) contains it.
 *
 * A port of the iOS `WondersOfTheWorld`, list and all; `AchievementTest` asserts
 * every pair still exists in the shared data, so a rename there cannot leave a
 * wonder impossible to tick off.
 */
object WondersOfTheWorld {

    data class Wonder(val country: String, val attraction: String)

    val wonders: List<Wonder> = listOf(
        Wonder("Brazil", "Christ the Redeemer"),
        Wonder("China", "Great Wall of China"),
        Wonder("Egypt", "Pyramids of Giza"),
        Wonder("India", "Taj Mahal"),
        Wonder("Italy", "Colosseum"),
        Wonder("Jordan", "Petra"),
        Wonder("Mexico", "Chichen Itza"),
        Wonder("Peru", "Machu Picchu"),
    )

    fun visited(checkedAttractions: Map<String, Set<String>>): List<String> =
        wonders.filter { it.attraction in checkedAttractions[it.country].orEmpty() }
            .map { it.attraction }
            .sorted()

    fun remaining(checkedAttractions: Map<String, Set<String>>): List<String> =
        wonders.filterNot { it.attraction in checkedAttractions[it.country].orEmpty() }
            .map { it.attraction }
            .sorted()
}

/**
 * Which features of `world.geojson` are UN member or observer states.
 *
 * The dataset carries 206 countries and territories; progress is counted against
 * the 195 UN states, so a user is not asked to visit French Guiana to finish the
 * world. The list is iOS's `GlobeState.nonUNTerritories`, verbatim.
 */
object UnMembership {

    val nonMemberTerritories: Set<String> = setOf(
        "Antarctica",
        "Bermuda",
        "Falkland Islands",
        "French Guiana",
        "French Southern and Antarctic Lands",
        "Greenland",
        "Kosovo",
        "New Caledonia",
        "Puerto Rico",
        "Taiwan",
        "Western Sahara",
    )

    fun isMember(country: String): Boolean = country !in nonMemberTerritories

    /** The UN states among [countries]. */
    fun membersOf(countries: Set<String>): Set<String> = countries - nonMemberTerritories
}
