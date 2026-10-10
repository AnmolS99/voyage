package com.anmol.voyage.data

import kotlin.math.roundToInt

/**
 * How much of the world is visited, as Home's dock shows it: UN states visited
 * out of all of them, and that as a whole percent — iOS's `visitedUNCountries`
 * over `totalUNCountries` in `CountriesDock`.
 */
data class VisitProgress(val visited: Int, val total: Int = UnMembership.MEMBER_COUNT) {

    val fraction: Float get() = visited.toFloat() / total

    /** Rounded to the nearest percent, as iOS's `.rounded()`. 195 never splits a tie. */
    val percent: Int get() = (visited * 100.0 / total).roundToInt()

    companion object {

        fun of(visitedCountries: Set<String>): VisitProgress =
            VisitProgress(UnMembership.membersOf(visitedCountries).size)

        /**
         * Whether the selection card previews Visit as a +1 segment on its bar:
         * the selected country is unvisited and counts toward the total.
         *
         * [justUnvisited] is a country un-visited from the card. Its preview
         * stays hidden until another country is picked: right after un-visiting,
         * it would fill exactly the gap the shrinking bar just left and look
         * like leftover fill.
         */
        fun previewsVisit(selected: String?, isVisited: Boolean, justUnvisited: String?): Boolean =
            selected != null && !isVisited && UnMembership.isMember(selected) && selected != justUnvisited
    }
}
