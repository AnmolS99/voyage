package com.anmol.voyage.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * An ASTC 4 × 4 encoder for opaque color images: every 4 × 4 block of pixels
 * becomes 16 bytes, 8 bits a pixel, which a GPU samples as it is.
 *
 * Android ships no ASTC encoder. The texture encoder it does ship —
 * `android.opengl.ETC1` — is not good enough for the Earth: ETC1 keeps a
 * block's base colors at 5 bits a channel, which on a desert or an ice sheet
 * shows as a patchwork of faintly pink, yellow and green squares at full zoom.
 * So this writes the one kind of ASTC block that suits a photograph and needs
 * no search:
 *
 * - one partition, so one pair of endpoint colors for the block, stored at a
 *   full 8 bits a channel (color endpoint mode 8, LDR RGB direct);
 * - a weight for every pixel (a 4 × 4 weight grid), 3 bits each, placing it at
 *   one of 8 steps between the two.
 *
 * The endpoints are the ends of the block's pixels along their principal axis
 * — the line through color space they lie closest to. Sixteen neighbouring
 * pixels of a satellite image are very nearly on one line, which is why this
 * measures 45.7 dB against the source over the whole Earth (42 dB across a
 * coastline, its hardest case: two colors meeting in one block) and cannot be
 * told from it at full zoom. A full encoder's search over partitions and
 * modes would buy little, and take minutes on a phone rather than seconds.
 *
 * The bit layout is the ASTC specification's (Khronos Data Format
 * Specification, "ASTC Compressed Texture Image Formats"); `Astc4x4Test`
 * decodes what this writes by that layout.
 */
internal object Astc4x4 {

    const val BLOCK_BYTES = 16

    /** The bytes a [width] × [height] image compresses to: partial blocks are whole ones. */
    fun encodedBytes(width: Int, height: Int): Int = ((width + 3) / 4) * ((height + 3) / 4) * BLOCK_BYTES

    /**
     * A 4 × 4 weight grid of 3-bit weights, one plane: in the specification's
     * table, width `B + 4` with B = 0, height `A + 2` with A = 2, and weight
     * range R = 0b111 with the precision bit clear.
     */
    private const val BLOCK_MODE = 0x53L

    /** LDR RGB direct: two RGB endpoints, alpha 1. */
    private const val COLOR_ENDPOINT_MODE = 8L

    private const val WEIGHT_STEPS = 7

    /**
     * How far from the first endpoint to the second each weight is. A 3-bit
     * weight is widened to 6 bits by repeating it, and anything past half way
     * gains one, so the steps run 0 to 64.
     */
    private val FRACTION = FloatArray(WEIGHT_STEPS + 1) { weight ->
        val widened = weight shl 3 or weight
        (if (widened > 32) widened + 1 else widened) / 64f
    }

    /**
     * Encodes the [width] × [height] image at [offset] in [rgb] — three bytes a
     * pixel, rows top-down — into [out] from [outOffset], block rows top-down.
     * A block that runs past the right or bottom edge repeats the edge pixel.
     */
    fun encode(rgb: ByteArray, offset: Int, width: Int, height: Int, out: ByteArray, outOffset: Int) {
        val red = FloatArray(16)
        val green = FloatArray(16)
        val blue = FloatArray(16)
        val weights = IntArray(16)
        var at = outOffset
        for (blockY in 0 until height step 4) {
            for (blockX in 0 until width step 4) {
                for (i in 0 until 16) {
                    val x = minOf(blockX + (i and 3), width - 1)
                    val y = minOf(blockY + (i shr 2), height - 1)
                    val pixel = offset + (y * width + x) * 3
                    red[i] = (rgb[pixel].toInt() and 0xFF).toFloat()
                    green[i] = (rgb[pixel + 1].toInt() and 0xFF).toFloat()
                    blue[i] = (rgb[pixel + 2].toInt() and 0xFF).toFloat()
                }
                encodeBlock(red, green, blue, weights, out, at)
                at += BLOCK_BYTES
            }
        }
    }

    private fun encodeBlock(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        weights: IntArray,
        out: ByteArray,
        at: Int,
    ) {
        var meanR = 0f
        var meanG = 0f
        var meanB = 0f
        for (i in 0 until 16) {
            meanR += red[i]
            meanG += green[i]
            meanB += blue[i]
        }
        meanR /= 16f
        meanG /= 16f
        meanB /= 16f

        // The covariance of the block's colors; its principal axis is the
        // direction they vary most along.
        var rr = 0f
        var rg = 0f
        var rb = 0f
        var gg = 0f
        var gb = 0f
        var bb = 0f
        for (i in 0 until 16) {
            val r = red[i] - meanR
            val g = green[i] - meanG
            val b = blue[i] - meanB
            rr += r * r
            rg += r * g
            rb += r * b
            gg += g * g
            gb += g * b
            bb += b * b
        }

        // Power iteration from the channel variances, which is never
        // perpendicular to the axis it is looking for.
        var axisR = rr
        var axisG = gg
        var axisB = bb
        var length = sqrt(axisR * axisR + axisG * axisG + axisB * axisB)
        var low = 0f
        var high = 0f
        if (length > 1e-3f) {
            for (iteration in 0 until 4) {
                axisR /= length
                axisG /= length
                axisB /= length
                val r = rr * axisR + rg * axisG + rb * axisB
                val g = rg * axisR + gg * axisG + gb * axisB
                val b = rb * axisR + gb * axisG + bb * axisB
                axisR = r
                axisG = g
                axisB = b
                length = sqrt(axisR * axisR + axisG * axisG + axisB * axisB)
                if (length <= 1e-6f) break
            }
        }
        val flat = length <= 1e-3f
        if (!flat) {
            axisR /= length
            axisG /= length
            axisB /= length
            low = Float.MAX_VALUE
            high = -Float.MAX_VALUE
            for (i in 0 until 16) {
                val along = (red[i] - meanR) * axisR + (green[i] - meanG) * axisG + (blue[i] - meanB) * axisB
                if (along < low) low = along
                if (along > high) high = along
            }
        }

        var r0 = meanR + axisR * low
        var g0 = meanG + axisG * low
        var b0 = meanB + axisB * low
        var r1 = meanR + axisR * high
        var g1 = meanG + axisG * high
        var b1 = meanB + axisB * high
        assignWeights(red, green, blue, r0, g0, b0, r1, g1, b1, weights)

        // With the weights chosen, the best endpoints for them are a least
        // squares fit: each pixel is (1 − f)·e0 + f·e1 for its weight's f. The
        // axis ends are only where the outermost pixels fell, and eight steps
        // between them leave every pixel a little off; the fit takes about a
        // decibel out of that.
        var aa = 0f
        var ab = 0f
        var bb2 = 0f
        var redA = 0f
        var redB = 0f
        var greenA = 0f
        var greenB = 0f
        var blueA = 0f
        var blueB = 0f
        for (i in 0 until 16) {
            val f = FRACTION[weights[i]]
            val e = 1f - f
            aa += e * e
            ab += e * f
            bb2 += f * f
            redA += e * red[i]
            redB += f * red[i]
            greenA += e * green[i]
            greenB += f * green[i]
            blueA += e * blue[i]
            blueB += f * blue[i]
        }
        val determinant = aa * bb2 - ab * ab
        // Zero when every pixel has the same weight: one color, nothing to fit.
        if (determinant > 1e-4f) {
            r0 = (redA * bb2 - redB * ab) / determinant
            r1 = (redB * aa - redA * ab) / determinant
            g0 = (greenA * bb2 - greenB * ab) / determinant
            g1 = (greenB * aa - greenA * ab) / determinant
            b0 = (blueA * bb2 - blueB * ab) / determinant
            b1 = (blueB * aa - blueA * ab) / determinant
        }
        var red0 = channel(r0)
        var green0 = channel(g0)
        var blue0 = channel(b0)
        var red1 = channel(r1)
        var green1 = channel(g1)
        var blue1 = channel(b1)
        // And the weights again, for the endpoints as they are actually stored.
        assignWeights(
            red, green, blue,
            red0.toFloat(), green0.toFloat(), blue0.toFloat(),
            red1.toFloat(), green1.toFloat(), blue1.toFloat(),
            weights,
        )

        // An RGB-direct block whose second endpoint is the darker one is read
        // as "blue-contracted", another encoding altogether. Swapping the
        // endpoints, and with them the weights, says the same thing plainly.
        if (red1 + green1 + blue1 < red0 + green0 + blue0) {
            val r = red0
            val g = green0
            val b = blue0
            red0 = red1
            green0 = green1
            blue0 = blue1
            red1 = r
            green1 = g
            blue1 = b
            for (i in 0 until 16) weights[i] = WEIGHT_STEPS - weights[i]
        }

        // Bits 0–10 the block mode, 11–12 the partition count less one (zero),
        // 13–16 the endpoint mode, then the endpoints from bit 17, 8 bits each,
        // in the order the mode reads them: R0 R1 G0 G1 B0 B1.
        var lowBits = BLOCK_MODE or (COLOR_ENDPOINT_MODE shl 13)
        var highBits = 0L
        lowBits = lowBits or (red0.toLong() shl 17) or (red1.toLong() shl 25) or (green0.toLong() shl 33) or
            (green1.toLong() shl 41) or (blue0.toLong() shl 49) or (blue1.toLong() shl 57)
        // B1's top bit is bit 64.
        highBits = highBits or (blue1.toLong() ushr 7)

        // The weights fill the block from its far end, their bits reversed:
        // bit k of the weight stream is bit 127 − k of the block.
        for (i in 0 until 16) {
            val weight = weights[i]
            for (bit in 0 until 3) {
                if (weight shr bit and 1 != 0) highBits = highBits or (1L shl (63 - (i * 3 + bit)))
            }
        }

        for (byte in 0 until 8) {
            out[at + byte] = (lowBits ushr (byte * 8)).toByte()
            out[at + 8 + byte] = (highBits ushr (byte * 8)).toByte()
        }
    }

    /** Each pixel's weight: the step nearest to where it falls between the two endpoints. */
    private fun assignWeights(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        r0: Float,
        g0: Float,
        b0: Float,
        r1: Float,
        g1: Float,
        b1: Float,
        weights: IntArray,
    ) {
        val dr = r1 - r0
        val dg = g1 - g0
        val db = b1 - b0
        val lengthSquared = dr * dr + dg * dg + db * db
        for (i in 0 until 16) {
            weights[i] = if (lengthSquared < 1e-3f) {
                0
            } else {
                val along = ((red[i] - r0) * dr + (green[i] - g0) * dg + (blue[i] - b0) * db) / lengthSquared
                (along * WEIGHT_STEPS).roundToInt().coerceIn(0, WEIGHT_STEPS)
            }
        }
    }

    private fun channel(value: Float): Int = value.roundToInt().coerceIn(0, 255)
}
