package com.anmol.voyage.globe

import com.anmol.voyage.data.BinaryReader
import com.anmol.voyage.data.BinaryWriter
import com.anmol.voyage.data.GeoJsonCountry
import java.io.InputStream
import java.io.OutputStream

/**
 * The expensive half of the globe: every country's fill, and the world's
 * borders in sectors.
 *
 * Triangulating them took ~1.2 s on a Galaxy A55 in a release build, so the app
 * does not: the build runs [triangulate] and ships the result as
 * `world_meshes.bin` ([WorldMeshesFile]) — Android's counterpart of the prebuilt
 * `globe.scn` iOS ships. The rest of [GlobeGeometry], the ocean sphere and 25
 * microstate dots, costs milliseconds and is still built on the device.
 */
class WorldMeshes(
    val countries: List<NamedCountryMesh>,
    val outlineSectors: List<OutlineMesh>,
) {
    companion object {

        fun triangulate(countries: List<GeoJsonCountry>): WorldMeshes = WorldMeshes(
            countries = fillsOf(countries),
            outlineSectors = bordersOf(countries).map(PolygonTriangulator::createOutlineMesh),
        )

        internal fun fillsOf(countries: List<GeoJsonCountry>): List<NamedCountryMesh> =
            shapesOf(countries).mapNotNull { country ->
                PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
                    ?.let { NamedCountryMesh(country.name, it) }
            }

        internal fun bordersOf(countries: List<GeoJsonCountry>): List<BorderCenterline> =
            PolygonTriangulator.createSectoredBorderCenterlines(shapesOf(countries).flatMap { it.polygons })

        /** Point-feature microstates get no fill and no border: they are dots instead. */
        private fun shapesOf(countries: List<GeoJsonCountry>) = countries.filter { !it.isPointCountry }
    }
}

/**
 * `world_meshes.bin`: [WorldMeshes] as the build wrote them.
 *
 * Written by `tools/world-cache`, which compiles this same code for the build
 * machine, so what the app loads is what triangulating on the device would have
 * built — `WorldMeshesFileTest` holds the round trip to that, bit for bit.
 * Borders are stored as [BorderCenterline]s and widened on load.
 */
object WorldMeshesFile {

    const val NAME = "world_meshes.bin"

    private const val MAGIC = 0x5659474D // "VYGM"

    /** Bump when the layout below changes. The app and its assets are always built together. */
    private const val VERSION = 1

    fun write(countries: List<GeoJsonCountry>, output: OutputStream) {
        BinaryWriter(output).run {
            header(MAGIC, VERSION)
            val fills = WorldMeshes.fillsOf(countries)
            int(fills.size)
            for (fill in fills) {
                string(fill.name)
                floats(fill.mesh.positions)
                floats(fill.mesh.uvs)
                ints(fill.mesh.indices)
                int(fill.mesh.gridFallbackRingCount)
            }
            val borders = WorldMeshes.bordersOf(countries)
            int(borders.size)
            for (border in borders) {
                floats(border.points)
                ints(border.ringSizes)
            }
            flush()
        }
    }

    fun read(input: InputStream): WorldMeshes = BinaryReader(input).run {
        header(MAGIC, VERSION)
        val countries = List(size()) {
            NamedCountryMesh(
                name = string(),
                mesh = CountryMesh(
                    positions = floats(),
                    uvs = floats(),
                    indices = ints(),
                    gridFallbackRingCount = int(),
                ),
            )
        }
        val outlineSectors = List(size()) {
            PolygonTriangulator.createOutlineMesh(BorderCenterline(points = floats(), ringSizes = ints()))
        }
        end()
        WorldMeshes(countries, outlineSectors)
    }
}
