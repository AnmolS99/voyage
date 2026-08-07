package com.anmol.voyage.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * `shared/fixtures/expected_countries.json` — the cross-platform contract for
 * what parsing `world.geojson` must produce. iOS asserts against the same file
 * (`GeoJSONFixtureTests`), so a change to the data or to either parser that only
 * lands on one platform fails there.
 *
 * Regenerate with `python3 scripts/generate_country_fixture.py`.
 */
@Serializable
data class ExpectedCountries(
    val countryCount: Int,
    val totalCoordinateCount: Int,
    val countries: List<ExpectedCountry>,
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun load(): ExpectedCountries {
            val json = Json { ignoreUnknownKeys = true }
            return SharedFiles.openFixture().use { json.decodeFromStream(serializer(), it) }
        }
    }
}

@Serializable
data class ExpectedCountry(
    val iso: String?,
    val name: String,
    val continent: String?,
    val capital: ExpectedCapital?,
    val isPointCountry: Boolean,
    val point: ExpectedPoint?,
    val polygonPointCounts: List<Int>,
    val holePointCounts: List<Int>,
    val bbox: List<Double>?,
)

@Serializable
data class ExpectedCapital(val name: String, val lat: Double, val lon: Double)

@Serializable
data class ExpectedPoint(val lat: Double, val lon: Double)
