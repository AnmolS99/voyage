package com.anmol.voyage.tools

import com.anmol.voyage.data.CountryGraphFile
import com.anmol.voyage.data.CountryGraphParser
import java.io.File

/**
 * Writes the app's border graph:
 * `CountryGraphGenerator <neighbors.json> <border_distances.json> <output directory>`.
 *
 * Run by :app's `generate<Variant>CountryGraph` tasks rather than by hand, like
 * [main] in WorldCacheGenerator.kt. The JSON comes from
 * `scripts/generate_neighbors.py`; the output is a build product and never
 * checked in.
 */
fun main(args: Array<String>) {
    require(args.size == 3) {
        "usage: CountryGraphGenerator <neighbors.json> <border_distances.json> <output directory>"
    }
    val graph = CountryGraphParser.parse(File(args[0]).inputStream(), File(args[1]).inputStream())
    val output = File(args[2]).apply { mkdirs() }
    File(output, CountryGraphFile.NAME).outputStream().use { CountryGraphFile.write(graph, it) }
}
