package com.anmol.voyage.ui.globe

import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.filamat.MaterialBuilder

/**
 * The globe's materials, compiled on device at startup.
 *
 * Filament normally wants materials compiled ahead of time by `matc`, a native
 * binary from the Filament release tarball. Using `filamat` instead keeps that
 * toolchain out of the Gradle build and out of CI (a Linux runner that would
 * otherwise need a matching matc), at the cost of a few milliseconds per
 * material at startup. There are two of them, so that trade is cheap.
 *
 * Both materials are **unlit** on purpose. CLAUDE.md requires the globe and the
 * map to show identical country colors, and a lit shading model would run every
 * palette value through the lighting equation, so #34BE82 would reach the screen
 * as something else — and differently at each latitude. Unlit puts the palette
 * on screen unchanged, exactly as the flat map's `Canvas` does.
 */
internal class GlobeMaterials private constructor(
    val country: Material,
    val ocean: Material,
) {

    fun destroy(engine: Engine) {
        engine.destroyMaterial(country)
        engine.destroyMaterial(ocean)
    }

    companion object {

        /**
         * The country fill shader.
         *
         * `colorA`/`colorB` and `gradient` exist to reproduce the map's
         * visited+wishlist treatment: when a country is on both lists it is
         * painted with a yellow→purple diagonal, and everywhere else `colorA`
         * is used flat. The UVs span the country's own lon/lat bounding box
         * (see `PolygonTriangulator.computeTexCoords`), so `u + v` runs
         * bottom-left → top-right across the country and matches the direction
         * `CountryStyles` documents for the flat map.
         */
        private val COUNTRY_SHADER = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 uv = getUV0();
                float t = clamp((uv.x + uv.y) * 0.5, 0.0, 1.0);
                vec4 gradientColor = mix(materialParams.colorA, materialParams.colorB, t);
                material.baseColor = mix(materialParams.colorA, gradientColor, materialParams.gradient);
            }
        """.trimIndent()

        private val OCEAN_SHADER = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = materialParams.baseColor;
            }
        """.trimIndent()

        /**
         * Compiles both materials. Must run on the thread that owns [engine].
         *
         * `MaterialBuilder.init()`/`shutdown()` bracket all building, as
         * filamat requires; shutdown releases the compiler's own globals and is
         * safe once every package has been built.
         */
        fun build(engine: Engine): GlobeMaterials {
            MaterialBuilder.init()
            try {
                val country = MaterialBuilder()
                    .name("country")
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .targetApi(MaterialBuilder.TargetApi.OPENGL)
                    .shading(MaterialBuilder.Shading.UNLIT)
                    .require(MaterialBuilder.VertexAttribute.UV0)
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorA")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorB")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "gradient")
                    .material(COUNTRY_SHADER)
                    .build()

                val ocean = MaterialBuilder()
                    .name("ocean")
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .targetApi(MaterialBuilder.TargetApi.OPENGL)
                    .shading(MaterialBuilder.Shading.UNLIT)
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
                    .material(OCEAN_SHADER)
                    .build()

                check(country.isValid) { "country material failed to compile" }
                check(ocean.isValid) { "ocean material failed to compile" }

                return GlobeMaterials(
                    country = Material.Builder().payload(country.buffer, country.buffer.remaining()).build(engine),
                    ocean = Material.Builder().payload(ocean.buffer, ocean.buffer.remaining()).build(engine),
                )
            } finally {
                MaterialBuilder.shutdown()
            }
        }
    }
}
