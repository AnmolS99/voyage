package com.anmol.voyage.data

import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/** Top cities and attractions for one country, keyed by ISO code in the JSON. */
@Serializable
data class CountryHighlights(
    val cities: List<String> = emptyList(),
    val attractions: List<String> = emptyList(),
)

/** Parses `country_highlights.json` (shared with iOS) into a map keyed by ISO code. */
object CountryHighlightsParser {

    private val serializer = MapSerializer(String.serializer(), CountryHighlights.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(input: InputStream): Map<String, CountryHighlights> =
        input.use { json.decodeFromStream(serializer, it) }
}
