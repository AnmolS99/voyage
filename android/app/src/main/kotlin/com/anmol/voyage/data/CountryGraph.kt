package com.anmol.voyage.data

import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Which countries border each other, and how far apart every pair of outlines
 * is: the graph `scripts/generate_neighbors.py` writes to `neighbors.json` and
 * `border_distances.json`, which iOS and Postgres read too. The iOS port is
 * `CountryGraph` as well; `CountryGraphTest` and its iOS twin hold both to
 * `shared/fixtures/daily/graph.json`.
 *
 * The app reads it from `country_graph.bin` ([CountryGraphFile]), which the
 * build writes from those two files, through [CountryDataCache.countryGraph].
 *
 * @param codes every country's ISO code, sorted.
 * @param neighbors each code's neighbours, sorted.
 * @param distances the upper triangle of the km matrix, row by row: for
 *   `i < j`, the km between `codes[i]` and `codes[j]`.
 */
class CountryGraph(
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
        fun triangleIndex(i: Int, j: Int, count: Int): Int = i * (2 * count - i - 1) / 2 + (j - i - 1)

        /** Element by element, as the generator sorts its route lists. */
        private val routeOrder = Comparator<List<String>> { a, b ->
            a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) }
                ?: a.size.compareTo(b.size)
        }
    }
}

/**
 * Reads `neighbors.json` and `border_distances.json` (shared with iOS) into a
 * [CountryGraph]. The build does this once and writes `country_graph.bin`;
 * tests do it to read the shared files directly.
 */
object CountryGraphParser {

    const val NEIGHBORS = "neighbors.json"
    const val BORDER_DISTANCES = "border_distances.json"

    @Serializable
    private class Distances(val codes: List<String>, val rows: List<List<Int>>)

    private val json = Json { ignoreUnknownKeys = true }
    private val neighborsSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(neighbors: InputStream, borderDistances: InputStream): CountryGraph {
        val neighborLists = neighbors.use { json.decodeFromStream(neighborsSerializer, it) }
        val distances = borderDistances.use { json.decodeFromStream(Distances.serializer(), it) }
        distances.rows.forEachIndexed { i, row ->
            require(row.size == distances.codes.size - 1 - i) { "row $i of $BORDER_DISTANCES has ${row.size} entries" }
        }
        return CountryGraph(
            codes = distances.codes,
            neighbors = neighborLists,
            distances = distances.rows.flatten().toIntArray(),
        )
    }
}

/**
 * `country_graph.bin`: the [CountryGraph] the build parsed from the shared
 * JSON, so the device reads ints instead of parsing ~120 KB of JSON.
 * `CountryGraphFileTest` holds the round trip to the parse.
 */
object CountryGraphFile {

    const val NAME = "country_graph.bin"

    private const val MAGIC = 0x56594752 // "VYGR"

    /** Bump when the layout below changes. The app and its assets are always built together. */
    private const val VERSION = 1

    fun write(graph: CountryGraph, output: OutputStream) {
        BinaryWriter(output).run {
            header(MAGIC, VERSION)
            int(graph.codes.size)
            graph.codes.forEach { string(it) }
            val index = graph.codes.withIndex().associate { (i, code) -> code to i }
            for (code in graph.codes) {
                ints(graph.neighbors(code).map { index.getValue(it) }.toIntArray())
            }
            val count = graph.codes.size
            val distances = IntArray(count * (count - 1) / 2)
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    distances[CountryGraph.triangleIndex(i, j, count)] =
                        checkNotNull(graph.borderDistanceKm(graph.codes[i], graph.codes[j]))
                }
            }
            ints(distances)
            flush()
        }
    }

    fun read(input: InputStream): CountryGraph = BinaryReader(input).run {
        header(MAGIC, VERSION)
        val codes = List(size()) { string() }
        val neighbors = codes.associateWith { ints().map { codes[it] } }
        val distances = ints()
        end()
        CountryGraph(codes, neighbors, distances)
    }
}
