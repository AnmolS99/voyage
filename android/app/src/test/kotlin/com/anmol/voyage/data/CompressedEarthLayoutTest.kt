package com.anmol.voyage.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the globe's full-size Earth texture is laid out on disk and on the GPU.
 * Compressing and mapping the file are Android's; the arithmetic both rest on
 * is here.
 */
class CompressedEarthLayoutTest {

    @Test
    fun `an 8K texture has every level down to one pixel`() {
        val layout = CompressedEarthLayout(8192, 4096)
        assertEquals(14, layout.levels.size)
        assertEquals(listOf(8192 to 4096, 4096 to 2048), layout.levels.take(2).map { it.width to it.height })
        // 2 x 1, then 1 x 1: the short side stops at one while the long one catches up.
        assertEquals(listOf(2 to 1, 1 to 1), layout.levels.takeLast(2).map { it.width to it.height })
    }

    @Test
    fun `levels follow the header one after another`() {
        val layout = CompressedEarthLayout(8192, 4096)
        assertEquals(CompressedEarthLayout.HEADER_BYTES.toLong(), layout.levels.first().offset)
        for ((level, next) in layout.levels.zipWithNext()) {
            assertEquals(level.offset + level.bytes, next.offset)
        }
        assertEquals(layout.levels.last().offset + layout.levels.last().bytes, layout.fileBytes)
    }

    @Test
    fun `it is 43 MB, a third of the image's 128 MB bitmap before that has any mipmaps`() {
        val layout = CompressedEarthLayout(8192, 4096)
        // Level 0 is a byte a pixel; the rest add a third.
        assertEquals(8192 * 4096, layout.levels.first().bytes)
        assertEquals(44_739_304L, layout.fileBytes)
    }

    @Test
    fun `only power-of-two images are compressed`() {
        assertTrue(CompressedEarthLayout.isSupported(8192, 4096))
        assertTrue(CompressedEarthLayout.isSupported(4096, 2048))
        assertFalse(CompressedEarthLayout.isSupported(8000, 4000))
        assertFalse(CompressedEarthLayout.isSupported(0, 0))
    }

    @Test
    fun `a level down is each 2 x 2 block averaged`() {
        // Two pixels wide, two tall, one channel differing: 0, 10, 20 and 31 average to 15.25.
        val pixels = byteArrayOf(0, 100, 50, 10, 100, 50, 20, 100, 50, 31, 100, 50)
        assertArrayEquals(byteArrayOf(15, 100, 50), CompressedEarthTextureFile.halved(pixels, 2, 2))
    }

    @Test
    fun `a level one pixel tall halves along its width alone`() {
        val pixels = byteArrayOf(10, 0, 0, 30, 0, 0)
        assertArrayEquals(byteArrayOf(20, 0, 0), CompressedEarthTextureFile.halved(pixels, 2, 1))
    }
}
