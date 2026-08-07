package com.anmol.voyage.data

import java.io.InputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream

/**
 * Parses `shared/data/world.geojson` into [GeoJsonCountry] values — the Kotlin
 * port of the iOS `GeoJSONParser`, and deliberately identical in behaviour:
 * countries come back in feature order, the first ring of every polygon is the
 * outer boundary and the rest are holes, and features without a usable name or
 * geometry are skipped.
 *
 * `shared/fixtures/expected_countries.json` pins that behaviour on both
 * platforms; see `GeoJsonParserTest`.
 */
object GeoJsonParser {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(input: InputStream): List<GeoJsonCountry> {
        val collection = input.use { json.decodeFromStream(RawFeatureCollection.serializer(), it) }
        return collection.features.mapNotNull { it.toCountry() }
    }
}

@Serializable
private class RawFeatureCollection(val features: List<RawFeature> = emptyList())

@Serializable
private class RawFeature(
    val id: String? = null,
    val properties: RawProperties = RawProperties(),
    @Serializable(with = RawGeometrySerializer::class) val geometry: RawGeometry? = null,
)

@Serializable
private class RawProperties(
    val name: String? = null,
    /** Upper-case fallback the iOS parser also accepts (raw Natural Earth naming). */
    @Suppress("PropertyName") val NAME: String? = null,
    val continent: String? = null,
    val capital: String? = null,
    val capitalLat: Double? = null,
    val capitalLon: Double? = null,
    val renderAs: String? = null,
)

private sealed interface RawGeometry {
    /** A `Point` feature: a microstate or small island rendered as a dot. */
    class Point(val lon: Double, val lat: Double) : RawGeometry

    /** `Polygon` and `MultiPolygon` both land here: polygons, each a list of rings. */
    class Areas(val polygons: List<List<Ring>>) : RawGeometry

    /** A geometry type this app does not render. */
    object Unsupported : RawGeometry
}

private fun RawFeature.toCountry(): GeoJsonCountry? {
    val name = properties.name ?: properties.NAME ?: return null
    val geometry = geometry ?: return null

    val capital = properties.capital?.let { capitalName ->
        val lat = properties.capitalLat ?: return@let null
        val lon = properties.capitalLon ?: return@let null
        Capital(capitalName, lat, lon)
    }

    return when (geometry) {
        is RawGeometry.Point -> GeoJsonCountry(
            name = name,
            isoCode = id,
            continent = properties.continent,
            capital = capital,
            polygons = emptyList(),
            holes = emptyList(),
            isPointCountry = true,
            pointCoordinate = LatLon(lat = geometry.lat, lon = geometry.lon),
        )

        is RawGeometry.Areas -> {
            val polygons = geometry.polygons.mapNotNull { it.firstOrNull() }
            if (polygons.isEmpty()) return null
            GeoJsonCountry(
                name = name,
                isoCode = id,
                continent = properties.continent,
                capital = capital,
                polygons = polygons,
                holes = geometry.polygons.flatMap { it.drop(1) },
                isPointCountry = properties.renderAs == "point",
                pointCoordinate = null,
            )
        }

        RawGeometry.Unsupported -> null
    }
}

/**
 * Reads a GeoJSON `geometry` object.
 *
 * `coordinates` is nested to a different depth per geometry type, so it can only
 * be decoded once `type` is known. Every GeoJSON writer emits `type` first, which
 * lets the common case stream straight into typed lists; the reversed order is
 * handled by buffering `coordinates` into a [JsonElement] and decoding it after.
 */
private object RawGeometrySerializer : KSerializer<RawGeometry> {

    // A coordinate pair decodes straight into a primitive array rather than a
    // `List<Double>`, which saves ~340k boxed doubles per parse. (Measured: the
    // lexer, not the boxing, is what the parse time actually goes on.)
    private val pointCoordinates = DoubleArraySerializer()
    private val ringCoordinates = ListSerializer(pointCoordinates)
    private val polygonCoordinates = ListSerializer(ringCoordinates)
    private val multiPolygonCoordinates = ListSerializer(polygonCoordinates)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GeoJsonGeometry") {
        element<String>("type")
        element("coordinates", JsonElement.serializer().descriptor)
    }

    /** Decodes `coordinates` with whichever shape the geometry type calls for. */
    private interface CoordinateReader {
        fun <T> read(strategy: DeserializationStrategy<T>): T
    }

    override fun deserialize(decoder: Decoder): RawGeometry = decoder.decodeStructure(descriptor) {
        val streamed = object : CoordinateReader {
            override fun <T> read(strategy: DeserializationStrategy<T>): T =
                decodeSerializableElement(descriptor, 1, strategy)
        }

        var type: String? = null
        var geometry: RawGeometry? = null
        var buffered: JsonElement? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> type = decodeStringElement(descriptor, 0)
                1 -> {
                    val knownType = type
                    if (knownType == null) {
                        buffered = streamed.read(JsonElement.serializer())
                    } else {
                        geometry = build(knownType, streamed)
                    }
                }
                else -> throw SerializationException("Unexpected geometry field at index $index")
            }
        }

        val element = buffered
        val knownType = type
        when {
            geometry != null -> geometry
            element != null && knownType != null -> build(
                knownType,
                object : CoordinateReader {
                    override fun <T> read(strategy: DeserializationStrategy<T>): T =
                        Json.decodeFromJsonElement(strategy, element)
                },
            )
            else -> RawGeometry.Unsupported
        }
    }

    private fun build(type: String, coordinates: CoordinateReader): RawGeometry = when (type) {
        "Point" -> coordinates.read(pointCoordinates).let { point ->
            if (point.size >= 2) RawGeometry.Point(lon = point[0], lat = point[1])
            else RawGeometry.Unsupported
        }

        "Polygon" -> RawGeometry.Areas(listOf(coordinates.read(polygonCoordinates).map(::toRing)))

        "MultiPolygon" -> RawGeometry.Areas(
            coordinates.read(multiPolygonCoordinates).map { polygon -> polygon.map(::toRing) },
        )

        else -> {
            coordinates.read(JsonElement.serializer())
            RawGeometry.Unsupported
        }
    }

    private fun toRing(points: List<DoubleArray>): Ring {
        val flat = DoubleArray(points.size * 2)
        points.forEachIndexed { index, point ->
            if (point.size < 2) throw SerializationException("Coordinate $index is not a lon/lat pair")
            flat[index * 2] = point[0]
            flat[index * 2 + 1] = point[1]
        }
        return Ring(flat)
    }

    override fun serialize(encoder: Encoder, value: RawGeometry) =
        throw UnsupportedOperationException("world.geojson is read-only")
}
