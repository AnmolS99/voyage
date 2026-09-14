package com.anmol.voyage.globe

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ocean sphere the countries sit on — the base layer of the globe, and the
 * iOS scene's `oceanSphere` equivalent.
 *
 * It is generated rather than loaded because it is a plain UV sphere: iOS gets
 * one from `SCNSphere`, and Filament has no primitive shapes.
 *
 * The sphere does real work beyond being blue. It is opaque and sits *inside*
 * the country fills (radius 1.0 against their 1.003), so the depth buffer uses
 * it to hide the far hemisphere's countries — the job iOS does with single-sided
 * winding and backface culling. That is why the country material can stay
 * double-sided without the far side of the world showing through.
 */
object UvSphere {

    /**
     * Builds a sphere of [radius] with [segments] meridians and [rings]
     * parallels, wound counter-clockwise when seen from outside.
     *
     * Defaults are sized for a globe that fills a phone screen: at 96×48 the
     * silhouette has no visible facets at maximum zoom, for ~4.7k vertices.
     */
    fun build(radius: Float = 1.0f, segments: Int = 96, rings: Int = 48): SphereMesh {
        require(segments >= 3) { "a sphere needs at least 3 segments" }
        require(rings >= 2) { "a sphere needs at least 2 rings" }

        val vertexCount = (segments + 1) * (rings + 1)
        val positions = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)

        var p = 0
        var t = 0
        for (ring in 0..rings) {
            // phi runs from the north pole (0) to the south pole (PI)
            val phi = PI * ring / rings
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (segment in 0..segments) {
                val theta = 2.0 * PI * segment / segments
                positions[p++] = (radius * sinPhi * cos(theta)).toFloat()
                positions[p++] = (radius * cosPhi).toFloat()
                positions[p++] = (radius * sinPhi * sin(theta)).toFloat()
                // Equirectangular texture coordinates, so the Earth image lands
                // under the countries: u = (lon + 180) / 360 and v = (90 - lat) / 180,
                // (0, 0) being the image's top-left. `latLonToSphere` puts
                // longitude at -theta, so u runs from 0.5 down to -0.5 around the
                // sphere; the sampler repeats, and the seam is the sphere's own
                // duplicated column, so nothing interpolates across it. iOS lines
                // SCNSphere's UVs up the same way, with a quarter-turn shift.
                uvs[t++] = 0.5f - segment.toFloat() / segments
                uvs[t++] = ring.toFloat() / rings
            }
        }

        // Two triangles per quad, minus the degenerate ones at each pole.
        val indices = IntArrayBuilder(rings * segments * 6)
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val current = ring * (segments + 1) + segment
                val next = current + segments + 1

                // `next` is a ring further south and `current + 1` a segment
                // further east, so this order is counter-clockwise from outside.
                // The opposite order culled the near hemisphere and showed the
                // inside of the far one — invisible while the ocean was flat blue,
                // and a mirrored, antipodal world once it had a texture.
                if (ring != 0) {
                    indices.add(current); indices.add(current + 1); indices.add(next)
                }
                if (ring != rings - 1) {
                    indices.add(current + 1); indices.add(next + 1); indices.add(next)
                }
            }
        }

        return SphereMesh(positions = positions, uvs = uvs, indices = indices.toArray())
    }
}

/** A generated sphere, in the same plain-buffer form as [CountryMesh]. */
class SphereMesh(
    val positions: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
}
