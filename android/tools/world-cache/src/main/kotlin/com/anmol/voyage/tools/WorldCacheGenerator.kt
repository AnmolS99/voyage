package com.anmol.voyage.tools

import com.anmol.voyage.data.CountriesFile
import com.anmol.voyage.data.GeoJsonParser
import com.anmol.voyage.globe.WorldMeshesFile
import java.io.File

/**
 * Writes the app's world caches: `WorldCacheGenerator <world.geojson> <output directory>`.
 *
 * Run by :app's `generate<Variant>WorldCache` tasks rather than by hand. The
 * output is a build product, rewritten whenever world.geojson or the code that
 * reads it changes, so it is never checked in.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: WorldCacheGenerator <world.geojson> <output directory>" }
    val countries = File(args[0]).inputStream().use(GeoJsonParser::parse)
    val output = File(args[1]).apply { mkdirs() }
    File(output, CountriesFile.NAME).outputStream().use { CountriesFile.write(countries, it) }
    File(output, WorldMeshesFile.NAME).outputStream().use { WorldMeshesFile.write(countries, it) }
}
