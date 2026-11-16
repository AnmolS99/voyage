package com.anmol.voyage.ui.globe

import android.content.res.AssetManager
import com.google.android.filament.Engine
import com.google.android.filament.Material
import java.nio.ByteBuffer

/**
 * The globe's materials, compiled by the build.
 *
 * Their sources are the `.mat` files in `app/src/main/materials/`, and `CompileMaterials` in
 * app/build.gradle.kts runs Filament's `matc` over them and packages the
 * results as assets. `matc` is a Gradle dependency like any other — Filament
 * publishes it to Maven Central per platform, at the engine's own version — so
 * nothing has to be installed, here or on CI. Compiling on the device instead
 * (`filamat`) put a 9 MB shader compiler in every install and ~190 ms in every
 * first launch, for three materials that never change after the build.
 *
 * All three materials are **unlit** on purpose. CLAUDE.md requires the globe and the
 * map to show identical country colors, and a lit shading model would run every
 * palette value through the lighting equation, so #34BE82 would reach the screen
 * as something else — and differently at each latitude. Unlit puts the palette
 * on screen unchanged, exactly as the flat map's `Canvas` does.
 *
 * Terrain is the one thing that shades them, and it is baked rather than lit: the
 * ocean and the fills hard-light a shaded-relief image over their color (see
 * `relief.glsl`), which the map draws with the same blend. Flat ground is neutral
 * in that image, so it still shows the palette color; only slopes change.
 */
internal class GlobeMaterials private constructor(
    val country: Material,
    val ocean: Material,
    val outline: Material,
) {

    fun destroy(engine: Engine) {
        engine.destroyMaterial(country)
        engine.destroyMaterial(ocean)
        engine.destroyMaterial(outline)
    }

    companion object {

        /** Where the build puts the compiled materials, under assets. */
        private const val DIRECTORY = "materials"

        /**
         * The compiled materials, kept for the life of the process: they are
         * the same bytes for every engine, and the globe gets a new engine
         * with each Activity.
         */
        @Volatile
        private var payloads: Payloads? = null

        private class Payloads(
            val country: ByteBuffer,
            val ocean: ByteBuffer,
            val outline: ByteBuffer,
        )

        /** Builds the materials for [engine]. Must run on the thread that owns it. */
        fun build(engine: Engine, assets: AssetManager): GlobeMaterials {
            val compiled = payloads ?: Payloads(
                country = assets.material("country"),
                ocean = assets.material("ocean"),
                outline = assets.material("outline"),
            ).also { payloads = it }
            return GlobeMaterials(
                // duplicate() so each build reads from position 0 — Filament
                // consumes the buffer it is given.
                country = engine.material(compiled.country.duplicate()),
                ocean = engine.material(compiled.ocean.duplicate()),
                outline = engine.material(compiled.outline.duplicate()),
            )
        }

        private fun AssetManager.material(name: String): ByteBuffer {
            val bytes = open("$DIRECTORY/$name.filamat").use { it.readBytes() }
            // Direct, as Filament reads a payload from native memory.
            return ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        }

        private fun Engine.material(payload: ByteBuffer): Material =
            Material.Builder().payload(payload, payload.remaining()).build(this)
    }
}
