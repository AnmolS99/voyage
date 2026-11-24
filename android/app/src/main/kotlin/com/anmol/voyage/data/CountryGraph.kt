package com.anmol.voyage.data

import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Which countries border each other, and how far apart every pair of outlines
 * is: the graph `scripts/generate_neighbors.py` writes to `neighbors.json` and
 * `border_distances.json`, which iOS reads too (Postgres has the graph
 * alone). The iOS port is
 * `CountryGraph` as well; `CountryGraphTest` and its iOS twin hold both to
 * `shared/fixtures/daily/graph.json`.
 *
 * Reach it through [CountryDataCache.countryGraph], which parses those two
 * files on first use.
 *
 * @param version a hash of the borders (`neighbors.json`'s `version`), which
 *   Postgres's `country_graph_version()` returns too: send it with a Travel
 *   answer so the server can tell an older app's graph from its own.
 * @param codes every country's ISO code, sorted.
 * @param neighbors each code's neighbours, sorted.
 * @param distances the upper triangle of the km matrix, row by row: for
 *   `i < j`, the km between `codes[i]` and `codes[j]`.
 */
class CountryGraph(
    val version: String,
    val codes: List<String>,
    private val neighbors: Map<String, List<String>>,
    private val distances: IntArray,
) {

    private val indexOf: Map<String, Int> = codes.withIndex().associate { (index, code) -> code to index }

    init {
        require(codes.zipWithNext().all { (a, b) -> a < b }) { "codes must be sorted and unique" }
        require(distances.size == codes.size * (codes.size - 1) / 2) {
            "${distances.size} distances for ${codes.size} countries"
        }
        require(neighbors.keys == codes.toSet()) { "neighbours must list every country" }
    }

    /** The countries bordering [isoCode], sorted; empty for an island or an unknown code. */
    fun neighbors(isoCode: String): List<String> = neighbors[isoCode].orEmpty()

    /**
     * Great-circle km between the closest points of two countries' outlines, in
     * either order: 0 exactly for neighbours and for the same country, null for
     * an unknown code. iOS `borderDistanceKm(_:_:)`.
     */
    fun borderDistanceKm(a: String, b: String): Int? {
        val i = indexOf[a] ?: return null
        val j = indexOf[b] ?: return null
        if (i == j) return 0
        val (low, high) = if (i < j) i to j else j to i
        return distances[triangleIndex(low, high, codes.size)]
    }

    /**
     * Every shortest route from [from] to [to] over the border graph, both ends
     * included, sorted. `[from]` alone when they are the same country; empty
     * when no route exists or a code is unknown.
     *
     * Breadth-first from [from], then back from [to] through each country's
     * neighbours one step closer. The generator's `shortest_routes` and the
     * Postgres `shortest_routes` do the same.
     */
    fun shortestRoutes(from: String, to: String): List<List<String>> {
        if (from !in indexOf || to !in indexOf) return emptyList()
        if (from == to) return listOf(listOf(from))

        val depth = HashMap<String, Int>().apply { put(from, 0) }
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == to) break
            val next = depth.getValue(current) + 1
            for (neighbor in neighbors(current)) {
                if (neighbor !in depth) {
                    depth[neighbor] = next
                    queue.addLast(neighbor)
                }
            }
        }
        if (to !in depth) return emptyList()

        fun routesTo(code: String): List<List<String>> =
            if (code == from) {
                listOf(listOf(from))
            } else {
                val closer = depth.getValue(code) - 1
                neighbors(code)
                    .filter { depth[it] == closer }
                    .flatMap { previous -> routesTo(previous).map { it + code } }
            }

        return routesTo(to).sortedWith(routeOrder)
    }

    companion object {
        /** Where the km for `i < j` sits in [distances]. */
        private fun triangleIndex(i: Int, j: Int, count: Int): Int = i * (2 * count - i - 1) / 2 + (j - i - 1)

        /** Element by element, as the generator sorts its route lists. */
        private val routeOrder = Comparator<List<String>> { a, b ->
            a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) }
                ?: a.size.compareTo(b.size)
        }
    }
}

/** Reads `neighbors.json` and `border_distances.json` (shared with iOS) into a [CountryGraph]. */
object CountryGraphParser {

    const val NEIGHBORS = "neighbors.json"
    const val BORDER_DISTANCES = "border_distances.json"

    @Serializable
    private class Neighbors(val version: String, val neighbors: Map<String, List<String>>)

    @Serializable
    private class Distances(val codes: List<String>, val rows: List<List<Int>>)

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(neighbors: InputStream, borderDistances: InputStream): CountryGraph {
        val graph = neighbors.use { json.decodeFromStream(Neighbors.serializer(), it) }
        val distances = borderDistances.use { json.decodeFromStream(Distances.serializer(), it) }
        distances.rows.forEachIndexed { i, row ->
            require(row.size == distances.codes.size - 1 - i) { "row $i of $BORDER_DISTANCES has ${row.size} entries" }
        }
        return CountryGraph(
            version = graph.version,
            codes = distances.codes,
            neighbors = graph.neighbors,
            distances = distances.rows.flatten().toIntArray(),
        )
    }
}
