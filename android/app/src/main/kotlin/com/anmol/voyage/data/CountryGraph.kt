package com.anmol.voyage.data

import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Which countries border each other, and how many borders and km apart every
 * pair is: what `scripts/generate_neighbors.py` writes to `neighbors.json`,
 * `border_crossings.json` and `border_distances.json`, which iOS reads too
 * (Postgres has the graph alone). The iOS port is `CountryGraph` as well;
 * `CountryGraphTest` and its iOS twin hold both to
 * `shared/fixtures/daily/graph.json`.
 *
 * Reach it through [CountryDataCache.countryGraph], which parses those files
 * on first use.
 *
 * @param version a hash of the borders (`neighbors.json`'s `version`), which
 *   Postgres's `country_graph_version()` returns too: send it with a Shortest Route
 *   answer so the server can tell an older app's graph from its own.
 * @param codes every country's ISO code, sorted.
 * @param neighbors each code's neighbours, sorted.
 * @param crossings the upper triangle of the crossings matrix, row by row: for
 *   `i < j`, the fewest borders crossed between `codes[i]` and `codes[j]`, -1
 *   when no route exists.
 * @param distances the upper triangle of the km matrix, laid out the same way.
 */
class CountryGraph(
    val version: String,
    val codes: List<String>,
    private val neighbors: Map<String, List<String>>,
    private val crossings: IntArray,
    private val distances: IntArray,
) {

    private val indexOf: Map<String, Int> = codes.withIndex().associate { (index, code) -> code to index }

    init {
        val pairs = codes.size * (codes.size - 1) / 2
        require(codes.zipWithNext().all { (a, b) -> a < b }) { "codes must be sorted and unique" }
        require(crossings.size == pairs && distances.size == pairs) {
            "${crossings.size} crossing counts and ${distances.size} distances for ${codes.size} countries"
        }
        require(neighbors.keys == codes.toSet()) { "neighbours must list every country" }
    }

    /** The countries bordering [isoCode], sorted; empty for an island or an unknown code. */
    fun neighbors(isoCode: String): List<String> = neighbors[isoCode].orEmpty()

    /**
     * The fewest borders a route between two countries crosses, in either
     * order: 0 for the same country, 1 for neighbours, null when no route
     * exists or a code is unknown. iOS `borderCrossings(_:_:)`.
     *
     * This is all Shortest Route needs from routes: the shortest one has
     * `crossings - 1` countries in between, and `x` is on some shortest route
     * from `a` to `b` exactly when `crossings(a, x) + crossings(x, b) ==
     * crossings(a, b)`.
     */
    fun borderCrossings(a: String, b: String): Int? = value(crossings, a, b)?.takeIf { it >= 0 }

    /**
     * Great-circle km between the closest points of two countries' outlines, in
     * either order: 0 exactly for neighbours and for the same country, null for
     * an unknown code. iOS `borderDistanceKm(_:_:)`.
     */
    fun borderDistanceKm(a: String, b: String): Int? = value(distances, a, b)

    /** [triangle]'s value for [a] and [b]: 0 for the same country, null for an unknown code. */
    private fun value(triangle: IntArray, a: String, b: String): Int? {
        val i = indexOf[a] ?: return null
        val j = indexOf[b] ?: return null
        if (i == j) return 0
        val (low, high) = if (i < j) i to j else j to i
        return triangle[low * (2 * codes.size - low - 1) / 2 + (high - low - 1)]
    }
}

/**
 * Reads `neighbors.json`, `border_crossings.json` and `border_distances.json`
 * (shared with iOS) into a [CountryGraph].
 */
object CountryGraphParser {

    const val NEIGHBORS = "neighbors.json"
    const val BORDER_CROSSINGS = "border_crossings.json"
    const val BORDER_DISTANCES = "border_distances.json"

    @Serializable
    private class Neighbors(val version: String, val neighbors: Map<String, List<String>>)

    /** [BORDER_CROSSINGS] or [BORDER_DISTANCES]. */
    @Serializable
    private class Triangle(val codes: List<String>, val rows: List<List<Int>>) {
        fun flattened(name: String): IntArray {
            rows.forEachIndexed { i, row ->
                require(row.size == codes.size - 1 - i) { "row $i of $name has ${row.size} entries" }
            }
            return rows.flatten().toIntArray()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(neighbors: InputStream, borderCrossings: InputStream, borderDistances: InputStream): CountryGraph {
        val graph = neighbors.use { json.decodeFromStream(Neighbors.serializer(), it) }
        val crossings = borderCrossings.use { json.decodeFromStream(Triangle.serializer(), it) }
        val distances = borderDistances.use { json.decodeFromStream(Triangle.serializer(), it) }
        require(crossings.codes == distances.codes) { "$BORDER_CROSSINGS and $BORDER_DISTANCES list different countries" }
        return CountryGraph(
            version = graph.version,
            codes = distances.codes,
            neighbors = graph.neighbors,
            crossings = crossings.flattened(BORDER_CROSSINGS),
            distances = distances.flattened(BORDER_DISTANCES),
        )
    }
}
