package com.anmol.voyage.ui.globe

import com.anmol.voyage.globe.MarkerMeshes.RING_OUTER_TAG
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
 *
 * Terrain is the one thing that shades them, and it is baked rather than lit: the
 * ocean and the fills hard-light a shaded-relief image over their color (see
 * [HARD_LIGHT]), which the map draws with the same blend. Flat ground is neutral
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
            $HARD_LIGHT
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 uv = getUV0();
                float t = clamp((uv.x + uv.y) * 0.5, 0.0, 1.0);
                vec4 gradientColor = mix(materialParams.colorA, materialParams.colorB, t);
                vec4 color = mix(materialParams.colorA, gradientColor, materialParams.gradient);
                material.baseColor = vec4(hardLight(color.rgb, reliefAt(variable_spherePosition.xyz)), color.a);
            }
        """.trimIndent()

        /**
         * Hands the fill's model-space position to the fragment stage, where it
         * becomes a latitude and longitude for the relief lookup. A fill's own
         * UVs span its country's bounding box (they draw the visited+wishlist
         * diagonal), so they cannot address a world-wide image.
         */
        private val COUNTRY_VERTEX_SHADER = """
            void materialVertex(inout MaterialVertexInputs material) {
                material.spherePosition = getPosition();
            }
        """.trimIndent()

        /**
         * The relief blend, shared by the fills and the ocean.
         *
         * Hard light, per channel, exactly as Skia composites
         * `BlendMode.Hardlight` for opaque layers — which is how the flat map
         * draws the same image — so the globe and the map shade alike. 0.5 is
         * neutral: the ocean and flat land keep their palette colors, shadowed
         * slopes darken proportionally, lit slopes lighten toward white.
         *
         * `reliefAt` turns a point on the sphere into the image's
         * equirectangular coordinates — the inverse of
         * `PolygonTriangulator.latLonToSphere`, top-left at (180°W, 90°N) like
         * the Earth textures. `u` jumps from 1 to 0 at the antimeridian, which
         * would make the GPU pick the smallest mip level for the pixels on
         * that line and draw a seam; measuring the gradient of `u` shifted
         * half a turn as well, and keeping the smaller one, removes it. The
         * maths is `highp` because fragment shaders default to half precision
         * on mobile, whose 1/2048 steps near u = 1 are half a texel here.
         */
        private const val HARD_LIGHT = """
            float reliefAt(highp vec3 position) {
                highp vec3 d = normalize(position);
                highp float lat = asin(clamp(d.y, -1.0, 1.0));
                highp float lon = -atan(d.z, d.x);
                highp vec2 uv = vec2(lon * (0.5 / PI) + 0.5, 0.5 - lat / PI);
                highp vec2 dx = dFdx(uv);
                highp vec2 dy = dFdy(uv);
                highp float dxShifted = dFdx(fract(uv.x + 0.5));
                highp float dyShifted = dFdy(fract(uv.x + 0.5));
                if (abs(dxShifted) + abs(dyShifted) < abs(dx.x) + abs(dy.x)) {
                    dx.x = dxShifted;
                    dy.x = dyShifted;
                }
                return textureGrad(materialParams_relief, uv, dx, dy).r;
            }

            vec3 hardLight(vec3 base, float shade) {
                return shade <= 0.5
                    ? 2.0 * shade * base
                    : 1.0 - 2.0 * (1.0 - shade) * (1.0 - base);
            }
        """

        /**
         * The ocean sphere, painted with the Earth texture.
         *
         * The sphere's UVs are already geographic (see `UvSphere`), so this is a
         * plain lookup. The texture is uploaded as `RGBA8`, not `SRGB8_A8`: like
         * the palette colors (see `toFilamentColor`), its sRGB values must reach
         * the framebuffer untouched, which holds only while post-processing is
         * off. A flat ocean is a 1 × 1 texture of the palette color, so there is
         * one ocean material rather than two.
         *
         * The relief image shares the sphere's geographic UVs, so it needs none
         * of the fills' position-to-UV work; `reliefAt` is compiled in only
         * because it sits beside `hardLight`.
         */
        private val OCEAN_SHADER = """
            $HARD_LIGHT
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec4 earth = texture(materialParams_earth, getUV0());
                float shade = texture(materialParams_relief, getUV0()).r;
                material.baseColor = vec4(hardLight(earth.rgb, shade), earth.a);
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
         *
         * A microstate's ring is the one mesh with two edges that move apart:
         * its outer vertices carry [RING_OUTER_TAG] on top of their gradient
         * parameter and go a further `band` out (see `MarkerMeshes.ring`). No
         * other mesh carries the tag, so for them `band` is never read.
         */
        private val OUTLINE_VERTEX_SHADER = """
            void materialVertex(inout MaterialVertexInputs material) {
                vec4 custom = getCustom0();
                float outer = step(${RING_OUTER_TAG / 2f + 0.5f}, custom.w);
                material.worldPosition.xyz += custom.xyz * (materialParams.thickness + outer * materialParams.band);
                material.gradientT = vec4(custom.w - outer * $RING_OUTER_TAG, 0.0, 0.0, 0.0);
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
                    .variable(MaterialBuilder.Variable.CUSTOM0, "spherePosition")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorA")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "colorB")
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "gradient")
                    .reliefSampler()
                    .materialVertex(COUNTRY_VERTEX_SHADER)
                    .material(COUNTRY_SHADER)
                    .build()

                val ocean = MaterialBuilder()
                    .name("ocean")
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .targetApi(MaterialBuilder.TargetApi.OPENGL)
                    .shading(MaterialBuilder.Shading.UNLIT)
                    .require(MaterialBuilder.VertexAttribute.UV0)
                    // Filament flips V by default, for glTF-style UVs. The sphere's
                    // are already top-left like the image's rows, so flipping them
                    // painted the world upside down.
                    .flipUV(false)
                    .samplerParameter(
                        MaterialBuilder.SamplerType.SAMPLER_2D,
                        MaterialBuilder.SamplerFormat.FLOAT,
                        MaterialBuilder.ParameterPrecision.DEFAULT,
                        "earth",
                    )
                    .reliefSampler()
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
                    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "band")
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

        /** The shaded-relief image both surface materials blend with — see [HARD_LIGHT]. */
        private fun MaterialBuilder.reliefSampler(): MaterialBuilder = samplerParameter(
            MaterialBuilder.SamplerType.SAMPLER_2D,
            MaterialBuilder.SamplerFormat.FLOAT,
            MaterialBuilder.ParameterPrecision.DEFAULT,
            "relief",
        )

        private fun Engine.material(payload: ByteBuffer): Material =
            Material.Builder().payload(payload, payload.remaining()).build(this)
    }
}
