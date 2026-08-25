package com.anmol.voyage.ui.globe

import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer

/**
 * The globe's materials, compiled on device at startup.
 *
 * Filament normally wants materials compiled ahead of time by `matc`, a native
 * binary from the Filament release tarball. Using `filamat` instead keeps that
 * toolchain out of the Gradle build and out of CI (a Linux runner that would
 * otherwise need a matching matc), at the cost of a few milliseconds per
 * material at startup. There are three of them, so that trade is cheap.
 *
 * All three materials are **unlit** on purpose. CLAUDE.md requires the globe and the
 * map to show identical country colors, and a lit shading model would run every
 * palette value through the lighting equation, so #34BE82 would reach the screen
 * as something else — and differently at each latitude. Unlit puts the palette
 * on screen unchanged, exactly as the flat map's `Canvas` does.
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
         * Widens the border strip, and is the whole reason zooming does not
         * rebuild ~335k vertices.
         *
         * `createBorderOutlineGeometry` puts both vertices of every strip rung
         * on the border centerline, so the mesh has zero width until this runs;
         * pushing each one along its own miter (`custom0.xyz`) is what gives it
         * one. Because `thickness` is a uniform in world units, the renderer
         * keeps borders a constant width on screen by scaling that single float
         * with camera distance — the Filament analogue of the iOS
         * `outlineShaderModifier`.
         *
         * `worldPosition` is camera-shifted for precision, which is fine here:
         * this adds a displacement, and a displacement is unaffected by where
         * the origin sits.
         */
        private val OUTLINE_VERTEX_SHADER = """
            void materialVertex(inout MaterialVertexInputs material) {
                vec4 custom = getCustom0();
                material.worldPosition.xyz += custom.xyz * materialParams.thickness;
                material.gradientT = vec4(custom.w, 0.0, 0.0, 0.0);
            }
        """.trimIndent()

        /**
         * Paints the strip. Flat [colorA] for a plain black border or a
         * single-status one; the visited+wishlist diagonal when `gradient` is 1,
         * using the parameter the vertex stage forwarded from the miter's `w`.
         */
        private val OUTLINE_SHADER = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                float t = clamp(variable_gradientT.x, 0.0, 1.0);
                vec4 gradientColor = mix(materialParams.colorA, materialParams.colorB, t);
                material.baseColor = mix(materialParams.colorA, gradientColor, materialParams.gradient);
            }
        """.trimIndent()

        /**
         * The compiled shader bytes, kept for the life of the process.
         *
         * Compiling these costs ~190 ms, and it was being paid again every time
         * the globe was rebuilt — on every return to the Home tab. The bytes
         * depend on nothing but the shader source, so they are compiled once
         * and re-wrapped for whichever engine is current.
         */
        @Volatile
        private var payloads: Payloads? = null

        private class Payloads(
            val country: ByteBuffer,
            val ocean: ByteBuffer,
            val outline: ByteBuffer,
        )

        /**
         * Builds both materials for [engine]. Must run on the thread that owns it.
         *
         * The first call compiles; later calls only hand the cached bytes to
         * Filament, which is effectively free.
         */
        fun build(engine: Engine): GlobeMaterials {
            val compiled = payloads ?: compile().also { payloads = it }
            return GlobeMaterials(
                // duplicate() so each build reads from position 0 — Filament
                // consumes the buffer it is given.
                country = engine.material(compiled.country.duplicate()),
                ocean = engine.material(compiled.ocean.duplicate()),
                outline = engine.material(compiled.outline.duplicate()),
            )
        }

        /**
         * `MaterialBuilder.init()`/`shutdown()` bracket all building, as filamat
         * requires; shutdown releases the compiler's own globals and is safe
         * once every package has been built.
         */
        private fun compile(): Payloads {
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

                val outline = MaterialBuilder()
                    .name("outline")
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .targetApi(MaterialBuilder.TargetApi.OPENGL)
                    .shading(MaterialBuilder.Shading.UNLIT)
                    // xyz is the miter direction, w the gradient parameter.
                    .require(MaterialBuilder.VertexAttribute.CUSTOM0)
                    .variable(MaterialBuilder.Variable.CUSTOM0, "gradientT")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorA")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorB")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "gradient")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "thickness")
                    // The strip's winding flips halfway around each ring, so
                    // either face can end up toward the camera. The ocean sphere
                    // hides the far hemisphere anyway (see GlobeRenderer).
                    .culling(MaterialBuilder.CullingMode.NONE)
                    .materialVertex(OUTLINE_VERTEX_SHADER)
                    .material(OUTLINE_SHADER)
                    .build()

                check(country.isValid) { "country material failed to compile" }
                check(ocean.isValid) { "ocean material failed to compile" }
                check(outline.isValid) { "outline material failed to compile" }

                return Payloads(
                    country = country.buffer,
                    ocean = ocean.buffer,
                    outline = outline.buffer,
                )
            } finally {
                MaterialBuilder.shutdown()
            }
        }

        private fun Engine.material(payload: ByteBuffer): Material =
            Material.Builder().payload(payload, payload.remaining()).build(this)
    }
}
