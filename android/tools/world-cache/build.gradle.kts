/*
 * The build-time generator for the app's world caches, `countries.bin` and
 * `world_meshes.bin`. :app runs it once per variant (`GenerateWorldCache` in
 * app/build.gradle.kts) and packages what it writes as assets, so the device
 * neither parses world.geojson nor triangulates the globe.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    sourceSets.named("main") {
        // The app's own parser, triangulator and file formats, compiled a second
        // time for the build machine — not copied, and not moved into a module of
        // their own. The caches are only worth trusting if they are written by the
        // code that would otherwise run on the device. The patterns apply to this
        // project's own src/main/kotlin too, hence the tools entry.
        kotlin.srcDir("../../app/src/main/kotlin")
        kotlin.include(
            "com/anmol/voyage/tools/**",
            "com/anmol/voyage/data/BinaryStreams.kt",
            "com/anmol/voyage/data/CountriesFile.kt",
            "com/anmol/voyage/data/GeoJsonCountry.kt",
            "com/anmol/voyage/data/GeoJsonParser.kt",
            "com/anmol/voyage/data/Polygons.kt",
            "com/anmol/voyage/globe/Earcut.kt",
            "com/anmol/voyage/globe/GlobeMesh.kt",
            "com/anmol/voyage/globe/PolygonTriangulator.kt",
            "com/anmol/voyage/globe/WorldMeshes.kt",
        )
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
