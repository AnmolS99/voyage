package com.anmol.voyage.data

import kotlinx.serialization.Serializable

/**
 * A closed ring of geographic coordinates, flattened to
 * `[lon0, lat0, lon1, lat1, …]`.
 *
 * The flat layout is deliberate: it is what both the Compose map (Phase 4) and
 * Filament (Phase 7) want to consume, and it avoids one object per coordinate
 * for the ~171k points in `world.geojson`.
 */
class Ring(val lonLat: DoubleArray) {

    /** Number of coordinate pairs in the ring. */
    val size: Int get() = lonLat.size / 2

    fun lon(index: Int): Double = lonLat[index * 2]

    fun lat(index: Int): Double = lonLat[index * 2 + 1]
}

/** A geographic point. Matches the iOS `(lat:lon:)` tuple ordering. */
data class LatLon(val lat: Double, val lon: Double)

/** "La Paz, Sucre" — a country's capitals on one line, or null when it has none. */
fun capitalsLine(names: List<String>): String? = names.takeIf { it.isNotEmpty() }?.joinToString(", ")

/** A country's capital city and its location. */
@Serializable
data class Capital(val name: String, val lat: Double, val lon: Double)

/**
 * One country as parsed from `world.geojson` — the Kotlin analogue of the iOS
 * `GeoJSONCountry`.
 *
 * Unlike iOS this carries no `color`: every country parses to the same land
 * color, which lives once in `VoyagePalette` and is applied at render time.
 */
class GeoJsonCountry(
    val name: String,
    /**
     * ISO 3166-1 alpha-2 code from the feature `id`, which doubles as the flag
     * emoji code. Nullable to mirror the iOS parser, which keeps features that
     * lack an `id`; every feature in the shipped dataset has one.
     */
    val isoCode: String?,
    val continent: String?,
    /** The capital the country is best known by (La Paz for Bolivia). */
    val capital: Capital?,
    /**
     * The country's other official capitals (Sucre for Bolivia), in the order
     * `world.geojson` lists them. Empty for most countries.
     */
    val otherCapitals: List<Capital>,
    /** Outer boundary rings. Empty for point countries. */
    val polygons: List<Ring>,
    /** Inner rings (holes) — e.g. the Lesotho enclave inside South Africa. */
    val holes: List<Ring>,
    /** Rendered as a dot rather than a filled shape (microstates, small islands). */
    val isPointCountry: Boolean,
    /** Set only for `Point` features; polygon countries keep this null. */
    val pointCoordinate: LatLon?,
) {

    /**
     * Every capital, the main one first. Each is a right answer in Name the
     * Capital and marked on the globe and map.
     */
    val capitals: List<Capital> get() = listOfNotNull(capital) + otherCapitals

    val capitalNames: List<String> get() = capitals.map { it.name }

    /** The capital of this country [name] names, ignoring case, or null. */
    fun capitalNamed(name: String): Capital? = capitals.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
