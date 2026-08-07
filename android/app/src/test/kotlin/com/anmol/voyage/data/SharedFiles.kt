package com.anmol.voyage.data

import java.io.File
import java.io.InputStream

/**
 * Access to the files under `shared/` from JVM unit tests.
 *
 * The app reads `world.geojson` and `country_highlights.json` out of its assets,
 * which are the very same files (wired up with `assets.srcDirs`). Tests read them
 * from disk instead, so they run on the JVM without an emulator or Robolectric.
 */
object SharedFiles {

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "shared/data/world.geojson").isFile }
            ?: error("shared/data not found above ${File("").absolutePath}")

    fun open(path: String): InputStream = File(repoRoot, path).inputStream()

    /** A cache backed by the shared data files, matching the app's asset wiring. */
    fun countryDataCache(): CountryDataCache = CountryDataCache { name -> open("shared/data/$name") }

    fun openFixture(): InputStream = open("shared/fixtures/expected_countries.json")
}
