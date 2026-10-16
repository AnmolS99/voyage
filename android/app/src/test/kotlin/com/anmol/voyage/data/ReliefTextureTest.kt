package com.anmol.voyage.data

import com.anmol.voyage.data.EarthTextureCache.Companion.MAX_TEXTURE_WIDTH
import com.anmol.voyage.data.EarthTextureCache.Companion.RELIEF_ASSET
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shaded-relief layer both renderers hard-light over the world. Hard light
 * leaves a color unchanged only at mid-gray, so the image's contract is that
 * everything flat — the ocean above all — is exactly 128, and that it is laid
 * out like the Earth textures: equirectangular, top-left at (180°W, 90°N).
 */
class ReliefTextureTest {

    private val image: BufferedImage = SharedFiles.open("shared/data/$RELIEF_ASSET").use { ImageIO.read(it) }

    /** The gray at a place, read as the equirectangular layout addresses it. */
    private fun grayAt(lat: Double, lon: Double): Int {
        val x = ((lon + 180) / 360 * image.width).toInt().coerceIn(0, image.width - 1)
        val y = ((90 - lat) / 180 * image.height).toInt().coerceIn(0, image.height - 1)
        // The raw sample: `getRGB` would run a gray PNG through a linear-to-sRGB
        // conversion and report 128 as 186.
        return image.raster.getSample(x, y, 0)
    }

    @Test
    fun `is a 2 to 1 world image the globe decodes at full size`() {
        assertEquals(image.width, image.height * 2)
        assertTrue(image.width <= MAX_TEXTURE_WIDTH)
    }

    @Test
    fun `open ocean is exactly neutral, so the ocean keeps its palette color`() {
        val oceans = listOf(
            0.0 to -150.0, // central Pacific
            -30.0 to -20.0, // South Atlantic
            -20.0 to 80.0, // Indian Ocean
            40.0 to -40.0, // North Atlantic
        )
        for ((lat, lon) in oceans) assertEquals("at $lat, $lon", 128, grayAt(lat, lon))
    }

    @Test
    fun `mountains shade both ways, so the layout puts them where they are`() {
        // A box around Everest: shadowed and lit slopes both, not one flat gray.
        val himalaya = (0 until 40).flatMap { i ->
            (0 until 40).map { j -> grayAt(lat = 27.0 + i * 0.05, lon = 85.5 + j * 0.05) }
        }
        assertTrue("no shadowed slope near Everest", himalaya.min() < 100)
        assertTrue("no lit slope near Everest", himalaya.max() > 150)
    }
}
