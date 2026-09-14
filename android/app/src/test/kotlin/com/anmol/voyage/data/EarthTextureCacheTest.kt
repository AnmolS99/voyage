package com.anmol.voyage.data

import com.anmol.voyage.data.EarthTextureCache.Companion.MAX_TEXTURE_WIDTH
import com.anmol.voyage.data.EarthTextureCache.Companion.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far the Earth textures are downsampled. Decoding itself is `BitmapFactory`,
 * which the JVM does not have; this is the decision that keeps an 8K image from
 * becoming a 128 MB bitmap.
 */
class EarthTextureCacheTest {

    @Test
    fun `an image within the limit is decoded at full size`() {
        assertEquals(1, sampleSizeFor(4096))
        assertEquals(1, sampleSizeFor(2048))
    }

    @Test
    fun `an 8K texture is halved`() {
        assertEquals(2, sampleSizeFor(8192))
    }

    @Test
    fun `anything wider is brought within the limit by a power of two`() {
        for (width in listOf(4097, 10_000, 16_384, 20_000)) {
            val sample = sampleSizeFor(width)
            assertEquals("sample $sample is not a power of two", 0, sample and (sample - 1))
            assertTrue("$width / $sample is still too wide", width / sample <= MAX_TEXTURE_WIDTH)
        }
    }
}
