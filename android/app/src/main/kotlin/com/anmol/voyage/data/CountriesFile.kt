package com.anmol.voyage.data

import java.io.InputStream
import java.io.OutputStream

/**
 * `countries.bin`: `world.geojson` already parsed, as the build wrote it.
 *
 * The app ships this instead of the GeoJSON. Parsing 3.2 MB of JSON cost ~600 ms
 * on a Galaxy A55 in a release build, where this is a straight read of the
 * doubles the parse produced. The build runs [GeoJsonParser] — this same code,
 * compiled for the build machine by `tools/world-cache` — and writes its result
 * here, so the countries the app loads are exactly the ones parsing would have
 * produced; `CountriesFileTest` holds the round trip to that, bit for bit.
 */
object CountriesFile {

    const val NAME = "countries.bin"

    private const val MAGIC = 0x56594354 // "VYCT"

    /** Bump when the layout below changes. The app and its assets are always built together. */
    private const val VERSION = 1

    fun write(countries: List<GeoJsonCountry>, output: OutputStream) {
        BinaryWriter(output).run {
            header(MAGIC, VERSION)
            int(countries.size)
            for (country in countries) {
                string(country.name)
                nullable(country.isoCode) { string(it) }
                nullable(country.continent) { string(it) }
                nullable(country.capital) { capital ->
                    string(capital.name)
                    double(capital.lat)
                    double(capital.lon)
                }
                rings(country.polygons)
                rings(country.holes)
                boolean(country.isPointCountry)
                nullable(country.pointCoordinate) { point ->
                    double(point.lat)
                    double(point.lon)
                }
            }
            flush()
        }
    }

    fun read(input: InputStream): List<GeoJsonCountry> = BinaryReader(input).run {
        header(MAGIC, VERSION)
        val countries = List(size()) {
            GeoJsonCountry(
                name = string(),
                isoCode = nullable { string() },
                continent = nullable { string() },
                capital = nullable { Capital(name = string(), lat = double(), lon = double()) },
                polygons = rings(),
                holes = rings(),
                isPointCountry = boolean(),
                pointCoordinate = nullable { LatLon(lat = double(), lon = double()) },
            )
        }
        end()
        countries
    }

    private fun BinaryWriter.rings(rings: List<Ring>) {
        int(rings.size)
        for (ring in rings) doubles(ring.lonLat)
    }

    private fun BinaryReader.rings(): List<Ring> = List(size()) { Ring(doubles()) }
}
