package com.anmol.voyage.ui.globe

import android.content.res.AssetManager
import com.google.android.filament.Engine
import com.google.android.filament.Material
import java.nio.ByteBuffer

/**
 * The globe's materials, compiled at build time from `src/main/materials`.
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

        private const val DIRECTORY = "materials"

        @Volatile
        private var payloads: Payloads? = null

        private class Payloads(
            val country: ByteBuffer,
            val ocean: ByteBuffer,
            val outline: ByteBuffer,
        )

        /** Must run on the thread that owns [engine]. */
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
            return ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        }

        private fun Engine.material(payload: ByteBuffer): Material =
            Material.Builder().payload(payload, payload.remaining()).build(this)
    }
}
