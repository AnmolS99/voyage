package com.anmol.voyage.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The border graph answers as `shared/fixtures/daily/graph.json` says — the
 * vectors `scripts/generate_neighbors.py` writes, which iOS
 * (`CountryGraphTests`) and pgTAP (`country_graph_vectors_test.sql`) check too.
 */
class CountryGraphTest {

    companion object {
        private lateinit var graph: CountryGraph
        private lateinit var vectors: JsonObject

        @BeforeClass
        @JvmStatic
        fun load() {
            graph = SharedFiles.countryDataCache().countryGraph
            vectors = Json.parseToJsonElement(
                SharedFiles.file("shared/fixtures/daily/graph.json").readText(),
            ).jsonObject
        }

        private fun strings(element: JsonElement) =
            element.jsonArray.map { it.jsonPrimitive.content }
    }

    @Test
    fun `the graph version matches the vectors`() {
        assertEquals(vectors.getValue("version").jsonPrimitive.content, graph.version)
    }

    @Test
    fun `neighbours match the vectors`() {
        val expected = vectors.getValue("neighbors").jsonObject
        assertTrue(expected.isNotEmpty())
        for ((code, neighbors) in expected) {
            assertEquals(code, strings(neighbors), graph.neighbors(code))
        }
    }

    @Test
    fun `shortest routes match the vectors`() {
        val expected = vectors.getValue("routes").jsonArray
        assertTrue(expected.isNotEmpty())
        for (vector in expected.map { it.jsonObject }) {
            val from = vector.getValue("from").jsonPrimitive.content
            val to = vector.getValue("to").jsonPrimitive.content
            val routes = graph.shortestRoutes(from, to)
            assertEquals("$from-$to", vector.getValue("routes").jsonArray.map(::strings), routes)
            val between = vector.getValue("between").jsonPrimitive.int
            assertEquals("$from-$to", between, routes.firstOrNull()?.let { maxOf(it.size - 2, 0) } ?: -1)
        }
    }

    @Test
    fun `distances match the vectors, in either order`() {
        val expected = vectors.getValue("distances").jsonArray
        assertTrue(expected.isNotEmpty())
        for (vector in expected.map { it.jsonObject }) {
            val a = vector.getValue("a").jsonPrimitive.content
            val b = vector.getValue("b").jsonPrimitive.content
            val km = vector.getValue("km").jsonPrimitive.int
            assertEquals("$a-$b", km, graph.borderDistanceKm(a, b))
            assertEquals("$b-$a", km, graph.borderDistanceKm(b, a))
        }
    }

    @Test
    fun `the graph is symmetric and 0 km means neighbours`() {
        for (a in graph.codes) {
            assertTrue(a, a !in graph.neighbors(a))
            for (b in graph.neighbors(a)) assertTrue("$a-$b", a in graph.neighbors(b))
            for (b in graph.codes) {
                if (a != b) assertEquals("$a-$b", b in graph.neighbors(a), graph.borderDistanceKm(a, b) == 0)
            }
        }
    }

    @Test
    fun `every country in the world has a place in the graph`() {
        val countries = SharedFiles.parseCountries().mapNotNull { it.isoCode }.sorted()
        assertEquals(countries, graph.codes)
    }

    @Test
    fun `unknown codes have no neighbours, distance or route`() {
        assertEquals(emptyList<String>(), graph.neighbors("XX"))
        assertNull(graph.borderDistanceKm("FR", "XX"))
        assertEquals(emptyList<List<String>>(), graph.shortestRoutes("FR", "XX"))
        assertEquals(emptyList<List<String>>(), graph.shortestRoutes("XX", "XX"))
        assertEquals(0, graph.borderDistanceKm("FR", "FR"))
    }

    @Test
    fun `the data cache answers from the graph`() {
        val cache = SharedFiles.countryDataCache()
        assertEquals(graph.neighbors("DE"), cache.neighbors("DE"))
        assertEquals(graph.borderDistanceKm("GB", "IS"), cache.borderDistanceKm("GB", "IS"))
        assertEquals(graph.shortestRoutes("PT", "PL"), cache.shortestRoutes("PT", "PL"))
    }
}
