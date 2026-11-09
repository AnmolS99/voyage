package com.anmol.voyage.ui.settings

import com.anmol.voyage.globe.GlobeCamera
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** An equirectangular image as packed ARGB pixels: top-left at (180°W, 90°N), like the Earth textures. */
internal class EquirectPixels(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "expected ${width * height} pixels, got ${argb.size}" }
    }
}

/**
 * How the settings previews see a textured sphere: Home's perspective camera,
 * framed so the globe's disc exactly fills a square.
 *
 * Home's globe is drawn by Filament (Android) and SceneKit (iOS) with a 45°
 * vertical field of view at [GlobeCamera.distance]; the settings thumbnails and
 * preview have no 3D engine of their own — a second Filament engine is what
 * `HomeScreen` exists to avoid — so they ray-cast the same sphere from the same
 * camera instead. Only the framing differs: the field of view here is cut to
 * the cone that just grazes the sphere, so the disc touches the square's edges
 * whatever the distance. Scaled to Home's disc (see [homeGlobeDiameter]) the
 * picture is the one Home draws, perspective included.
 *
 * Points are in the square's own units, `-1…1` with `y` down, so the same maths
 * serves a 64 dp thumbnail, the full-screen preview and the preview's AGSL
 * shader, which is a line-for-line copy of [latLonAt]. iOS `SphereProjection`
 * is too.
 */
internal class SphereProjection(camera: GlobeCamera) {

    private val distance = camera.distance.toDouble()

    /** tan of the half-angle that grazes the sphere: `tan(asin(1 / distance))`. */
    val tanHalfAngle: Double = 1.0 / sqrt(distance * distance - 1.0)

    /** Unit vector from the globe's centre to the camera. */
    val eye: DoubleArray

    /** Screen right and screen up in world space, as [GlobeCamera.latLonAt] builds them. */
    val right: DoubleArray
    val up: DoubleArray

    init {
        val lat = camera.latitude * PI / 180.0
        val lon = -camera.longitude * PI / 180.0
        eye = doubleArrayOf(cos(lat) * cos(lon), sin(lat), cos(lat) * sin(lon))
        // forward = -eye; right = forward × worldUp; up = right × forward.
        val r = doubleArrayOf(eye[2], 0.0, -eye[0])
        val rLength = sqrt(r[0] * r[0] + r[2] * r[2]).takeIf { it > 1e-9 } ?: 1.0
        right = doubleArrayOf(r[0] / rLength, 0.0, r[2] / rLength)
        up = doubleArrayOf(
            right[1] * -eye[2] - right[2] * -eye[1],
            right[2] * -eye[0] - right[0] * -eye[2],
            right[0] * -eye[1] - right[1] * -eye[0],
        )
    }

    /**
     * The latitude and longitude, in degrees, under the point ([x], [y]) of the
     * unit square, written into [out] — or false if the point is off the disc.
     *
     * A point a hair outside the disc snaps to the limb rather than missing, so
     * the anti-aliased edge has a colour to fade.
     */
    fun latLonAt(x: Double, y: Double, out: DoubleArray): Boolean {
        if (x * x + y * y > EDGE_SLACK) return false
        val sx = x * tanHalfAngle
        val sy = -y * tanHalfAngle
        val dx = -eye[0] + right[0] * sx + up[0] * sy
        val dy = -eye[1] + right[1] * sx + up[1] * sy
        val dz = -eye[2] + right[2] * sx + up[2] * sy
        val ex = eye[0] * distance
        val ey = eye[1] * distance
        val ez = eye[2] * distance
        val a = dx * dx + dy * dy + dz * dz
        val b = 2.0 * (ex * dx + ey * dy + ez * dz)
        val c = distance * distance - 1.0
        val t = (-b - sqrt(max(b * b - 4.0 * a * c, 0.0))) / (2.0 * a)
        val px = ex + t * dx
        val py = ey + t * dy
        val pz = ez + t * dz
        val length = sqrt(px * px + py * py + pz * pz)
        out[0] = asin((py / length).coerceIn(-1.0, 1.0)) * 180.0 / PI
        out[1] = -atan2(pz, px) * 180.0 / PI
        return true
    }

    /**
     * Ray-casts the whole disc into a [size]-pixel square of ARGB pixels,
     * bilinearly sampling [texture], with an anti-aliased limb and transparent
     * corners. Not premultiplied, as `Bitmap.setPixels` takes them.
     */
    fun render(texture: EquirectPixels, size: Int): IntArray {
        val pixels = IntArray(size * size)
        val latLon = DoubleArray(2)
        val radius = size / 2.0
        for (row in 0 until size) {
            val y = (row + 0.5 - radius) / radius
            for (column in 0 until size) {
                val x = (column + 0.5 - radius) / radius
                val coverage = coverage(x, y, radius)
                if (coverage <= 0.0 || !latLonAt(x, y, latLon)) continue
                val color = sampleBilinear(texture, latLon[0], latLon[1])
                val alpha = (coverage * 255.0 + 0.5).toInt()
                pixels[row * size + column] = (alpha shl 24) or (color and 0xFFFFFF)
            }
        }
        return pixels
    }

    companion object {
        /** Just past the unit circle: the anti-aliased limb reaches half a pixel beyond it. */
        private const val EDGE_SLACK = 1.0 + 1e-2

        /** How much of the pixel at ([x], [y]) the disc covers, for a disc [radiusPx] across. */
        fun coverage(x: Double, y: Double, radiusPx: Double): Double =
            ((1.0 - sqrt(x * x + y * y)) * radiusPx + 0.5).coerceIn(0.0, 1.0)

        /** The texture's colour at a latitude and longitude, bilinear, wrapping east–west. */
        fun sampleBilinear(texture: EquirectPixels, latitude: Double, longitude: Double): Int {
            val u = ((longitude + 180.0) / 360.0) * texture.width - 0.5
            val v = ((90.0 - latitude) / 180.0) * texture.height - 0.5
            val u0 = floor(u)
            val v0 = floor(v)
            val fu = u - u0
            val fv = v - v0
            val x0 = Math.floorMod(u0.toInt(), texture.width)
            val x1 = (x0 + 1) % texture.width
            val y0 = v0.toInt().coerceIn(0, texture.height - 1)
            val y1 = (v0.toInt() + 1).coerceIn(0, texture.height - 1)
            val p = texture.argb
            val w = texture.width
            return mix(
                mix(p[y0 * w + x0], p[y0 * w + x1], fu),
                mix(p[y1 * w + x0], p[y1 * w + x1], fu),
                fv,
            )
        }

        private fun mix(a: Int, b: Int, t: Double): Int {
            fun channel(shift: Int): Int {
                val ca = (a shr shift) and 0xFF
                val cb = (b shr shift) and 0xFF
                return ((ca + (cb - ca) * t) + 0.5).toInt().coerceIn(0, 255) shl shift
            }
            return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
        }

        /**
         * The on-screen diameter, in pixels, of Home's globe at [distance] in a
         * viewport [viewportHeight] pixels tall: the grazing cone's tangent over
         * the vertical half field of view, across the full height.
         */
        fun homeGlobeDiameter(viewportHeight: Float, distance: Float = GlobeCamera.DEFAULT_DISTANCE): Float {
            val tanGrazing = 1.0 / sqrt(distance.toDouble() * distance - 1.0)
            val tanHalfFov = kotlin.math.tan(Math.toRadians(GlobeCamera.FOV_DEGREES / 2.0))
            return (viewportHeight * tanGrazing / tanHalfFov).toFloat()
        }
    }
}

/**
 * The preview's fallback below API 33, where there is no AGSL: the disc's
 * latitude and longitude worked out once per tilt, so each frame of a spin is
 * only an offset into the texture.
 *
 * Turning the camera's longitude turns every point's longitude by the same
 * amount, so a frame at any longitude is the lookup built at longitude 0 with
 * its columns shifted — no trigonometry per frame. A new latitude does need a
 * new lookup, which the preview builds off the main thread.
 */
internal class TurntableLookup(
    val latitude: Double,
    val distance: Float,
    val size: Int,
    private val textureWidth: Int,
    private val textureHeight: Int,
) {
    /** Per pixel: the texel row's start, or -1 off the disc. */
    private val rowStart = IntArray(size * size) { -1 }

    /** Per pixel: the texel column at longitude 0, in 1/256ths of a texel. */
    private val column = IntArray(size * size)

    /** Per pixel: the disc's coverage, 0–255. */
    private val alpha = IntArray(size * size)

    init {
        val projection = SphereProjection(GlobeCamera(latitude = latitude, longitude = 0.0, distance = distance))
        val latLon = DoubleArray(2)
        val radius = size / 2.0
        for (row in 0 until size) {
            val y = (row + 0.5 - radius) / radius
            for (col in 0 until size) {
                val x = (col + 0.5 - radius) / radius
                val coverage = SphereProjection.coverage(x, y, radius)
                val index = row * size + col
                if (coverage <= 0.0 || !projection.latLonAt(x, y, latLon)) continue
                val v = ((90.0 - latLon[0]) / 180.0 * textureHeight).toInt().coerceIn(0, textureHeight - 1)
                rowStart[index] = v * textureWidth
                column[index] = ((latLon[1] + 180.0) / 360.0 * textureWidth * 256.0).toInt()
                alpha[index] = (coverage * 255.0 + 0.5).toInt()
            }
        }
    }

    /** Fills [out] with the disc seen from [longitude], nearest-texel. */
    fun frame(texture: EquirectPixels, longitude: Double, out: IntArray) {
        val shift = (longitude / 360.0 * textureWidth * 256.0).toInt()
        val period = textureWidth * 256
        val pixels = texture.argb
        for (i in out.indices) {
            val start = rowStart[i]
            if (start < 0) {
                out[i] = 0
                continue
            }
            val texel = Math.floorMod(column[i] + shift, period) shr 8
            out[i] = (alpha[i] shl 24) or (pixels[start + texel] and 0xFFFFFF)
        }
    }
}

/** From [from] at 1 to [to] at 0, the short way round in longitude. */
internal fun lerpCamera(to: GlobeCamera, from: GlobeCamera, fraction: Float): GlobeCamera {
    val t = fraction.coerceIn(0f, 1f).toDouble()
    var deltaLongitude = (from.longitude - to.longitude) % 360.0
    if (deltaLongitude > 180.0) deltaLongitude -= 360.0
    if (deltaLongitude < -180.0) deltaLongitude += 360.0
    return GlobeCamera.at(
        latitude = to.latitude + (from.latitude - to.latitude) * t,
        longitude = to.longitude + deltaLongitude * t,
        distance = to.distance + (from.distance - to.distance) * t.toFloat(),
    )
}

/**
 * Hard light, per channel, of an 8-bit [shade] over [base] — the blend Home's
 * globe (`GlobeMaterials.HARD_LIGHT`) and map apply the relief with, on encoded
 * values as both do. 128 leaves a colour as it is, give or take one step.
 */
internal fun hardLight(base: Int, shade: Int): Int {
    val row = (shade and 0xFF) shl 8
    val lut = HARD_LIGHT_TABLE
    return (base and ALPHA_MASK) or
        (lut[row or ((base shr 16) and 0xFF)].toInt() and 0xFF shl 16) or
        (lut[row or ((base shr 8) and 0xFF)].toInt() and 0xFF shl 8) or
        (lut[row or (base and 0xFF)].toInt() and 0xFF)
}

private const val ALPHA_MASK = -0x1000000

/** Every (shade, channel) pair, so blending a texture costs lookups rather than float maths. */
private val HARD_LIGHT_TABLE: ByteArray by lazy {
    ByteArray(256 * 256) { index ->
        val s = (index shr 8) / 255.0
        val b = (index and 0xFF) / 255.0
        val lit = if (s <= 0.5) 2.0 * s * b else 1.0 - 2.0 * (1.0 - s) * (1.0 - b)
        (lit * 255.0 + 0.5).toInt().coerceIn(0, 255).toByte()
    }
}
