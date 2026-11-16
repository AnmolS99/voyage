package com.anmol.voyage.data

import java.awt.Rectangle
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.log10
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ASTC blocks the globe's full-size Earth texture is made of. A GPU reads
 * them by the ASTC specification, so this decodes them by it too — the layout
 * of the one block type [Astc4x4] writes — and holds the result to the image.
 */
class Astc4x4Test {

    /** Decodes blocks of [Astc4x4]'s one mode back to RGB, as the specification has a GPU do it. */
    private fun decode(blocks: ByteArray, width: Int, height: Int): ByteArray {
        val rgb = ByteArray(width * height * 3)
        var at = 0
        for (blockY in 0 until height step 4) {
            for (blockX in 0 until width step 4) {
                var low = 0L
                var high = 0L
                for (byte in 0 until 8) {
                    low = low or ((blocks[at + byte].toLong() and 0xFF) shl (byte * 8))
                    high = high or ((blocks[at + 8 + byte].toLong() and 0xFF) shl (byte * 8))
                }
                at += Astc4x4.BLOCK_BYTES
                fun bit(index: Int): Int = ((if (index < 64) low ushr index else high ushr (index - 64)) and 1L).toInt()
                fun bits(from: Int, count: Int): Int = (0 until count).sumOf { bit(from + it) shl it }

                assertEquals("block mode: 4 x 4 grid of 3-bit weights", 0x53, bits(0, 11))
                assertEquals("one partition", 0, bits(11, 2))
                assertEquals("LDR RGB direct", 8, bits(13, 4))
                val values = IntArray(6) { bits(17 + it * 8, 8) }
                assertTrue(
                    "the second endpoint must not be the darker: that is blue contraction",
                    values[1] + values[3] + values[5] >= values[0] + values[2] + values[4],
                )
                for (i in 0 until 16) {
                    val x = blockX + (i and 3)
                    val y = blockY + (i shr 2)
                    if (x >= width || y >= height) continue
                    // Stored from the block's far end with its bits reversed.
                    val stored = (0 until 3).sumOf { bit(127 - (i * 3 + it)) shl it }
                    var weight = stored shl 3 or stored
                    if (weight > 32) weight += 1
                    for (channel in 0 until 3) {
                        val from = values[channel * 2] * 257
                        val to = values[channel * 2 + 1] * 257
                        val color = (from * (64 - weight) + to * weight + 32) shr 6
                        rgb[(y * width + x) * 3 + channel] = (color / 257f).roundToInt().toByte()
                    }
                }
            }
        }
        return rgb
    }

    private fun encode(rgb: ByteArray, width: Int, height: Int): ByteArray =
        ByteArray(Astc4x4.encodedBytes(width, height)).also { Astc4x4.encode(rgb, 0, width, height, it, 0) }

    private fun psnr(a: ByteArray, b: ByteArray): Double {
        val squared = a.indices.sumOf { i ->
            val difference = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            (difference * difference).toDouble()
        }
        return 10 * log10(255.0 * 255.0 * a.size / squared)
    }

    @Test
    fun `every block is 16 bytes, and a partial block is a whole one`() {
        assertEquals(16, Astc4x4.encodedBytes(4, 4))
        assertEquals(16, Astc4x4.encodedBytes(1, 1))
        assertEquals(32, Astc4x4.encodedBytes(5, 2))
        assertEquals(8192 / 4 * (4096 / 4) * 16, Astc4x4.encodedBytes(8192, 4096))
    }

    @Test
    fun `a flat block comes back as its color exactly`() {
        val color = byteArrayOf(0x2F, 0x86.toByte(), 0xA6.toByte())
        val rgb = ByteArray(16 * 3) { color[it % 3] }
        assertArrayEquals(rgb, decode(encode(rgb, 4, 4), 4, 4))
    }

    @Test
    fun `a block that darkens along its rows is not read as blue-contracted`() {
        // Bright first, dark last: the endpoints have to be swapped to stay
        // in the plain encoding, which decode() asserts.
        val rgb = ByteArray(16 * 3) { i -> (240 - (i / 3) * 12).toByte() }
        val decoded = decode(encode(rgb, 4, 4), 4, 4)
        // Sixteen grays on eight steps: each is at most half a step off.
        assertTrue("psnr ${psnr(rgb, decoded)}", psnr(rgb, decoded) > 30)
    }

    @Test
    fun `an image smaller than a block repeats its edge`() {
        val rgb = byteArrayOf(10, 20, 30, 200.toByte(), 180.toByte(), 160.toByte())
        val decoded = decode(encode(rgb, 2, 1), 2, 1)
        for (i in rgb.indices) {
            assertEquals((rgb[i].toInt() and 0xFF).toFloat(), (decoded[i].toInt() and 0xFF).toFloat(), 1f)
        }
    }

    @Test
    fun `the Earth survives it, desert and coast alike`() {
        // The Red Sea and the Arabian desert at full size: smooth sand, where a
        // coarser format shows its blocks, beside a hard coastline.
        val region = Rectangle(4864, 1280, 512, 512)
        val image = SharedFiles.file("shared/data/textures/earth_texture.jpg").let { file ->
            ImageIO.createImageInputStream(file).use { stream ->
                val reader = ImageIO.getImageReaders(stream).next()
                reader.input = stream
                reader.read(0, reader.defaultReadParam.apply { sourceRegion = region }).also { reader.dispose() }
            }
        }
        val rgb = ByteArray(region.width * region.height * 3)
        for (y in 0 until region.height) {
            for (x in 0 until region.width) {
                val pixel = image.getRGB(x, y)
                val at = (y * region.width + x) * 3
                rgb[at] = (pixel shr 16).toByte()
                rgb[at + 1] = (pixel shr 8).toByte()
                rgb[at + 2] = pixel.toByte()
            }
        }
        val blocks = encode(rgb, region.width, region.height)
        val quality = psnr(rgb, decode(blocks, region.width, region.height))
        System.getenv("ASTC_DUMP")?.let { path ->
            // An .astc file, for checking the blocks against another decoder.
            val header = byteArrayOf(0x13, 0xAB.toByte(), 0xA1.toByte(), 0x5C, 4, 4, 1, 0, 2, 0, 0, 2, 0, 1, 0, 0)
            File(path).writeBytes(header + blocks)
            println("psnr $quality")
        }
        assertTrue("psnr $quality dB", quality > 41)
    }
}
