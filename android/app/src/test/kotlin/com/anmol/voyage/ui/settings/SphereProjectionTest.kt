package com.anmol.voyage.ui.settings

import com.anmol.voyage.globe.GlobeCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings previews' sphere: it must be Home's globe, not a lookalike.
 *
 * The pairing that matters is [SphereProjection] against
 * [GlobeCamera.latLonAt], the inverse of the projection Home's Filament globe
 * draws with: scaled to [SphereProjection.homeGlobeDiameter] and centred, every
 * preview pixel has to land on the latitude and longitude Home would show
 * there, or the preview would promise a picture Home does not draw.
 */
class SphereProjectionTest {

    private val camera = GlobeCamera(latitude = 20.0, longitude = 15.0)

    @Test
    fun `the disc's centre is the point the camera looks at`() {
        val out = DoubleArray(2)
        assertTrue(SphereProjection(camera).latLonAt(0.0, 0.0, out))
        assertEquals(20.0, out[0], 1e-9)
        assertEquals(15.0, out[1], 1e-9)
    }

    @Test
    fun `east is right and north is up`() {
        val projection = SphereProjection(camera)
        val right = DoubleArray(2).also { projection.latLonAt(0.3, 0.0, it) }
        val up = DoubleArray(2).also { projection.latLonAt(0.0, -0.3, it) }
        assertTrue("right of centre should be east", right[1] > 15.0)
        assertTrue("above centre (y down) should be north", up[0] > 20.0)
    }

    @Test
    fun `every pixel matches Home's projection at Home's size`() {
        val width = 1080f
        val height = 2340f
        val diameter = SphereProjection.homeGlobeDiameter(height, camera.distance)
        val radius = diameter / 2.0
        val projection = SphereProjection(camera)
        val preview = DoubleArray(2)
        var compared = 0
        for (fx in listOf(0.05, 0.2, 0.37, 0.5, 0.61, 0.8, 0.95)) {
            for (fy in listOf(0.3, 0.4, 0.45, 0.5, 0.55, 0.62, 0.7)) {
                val x = (width * fx).toFloat()
                val y = (height * fy).toFloat()
                val home = camera.latLonAt(x, y, width, height)
                val qx = (x - width / 2.0) / radius
                val qy = (y - height / 2.0) / radius
                if (qx * qx + qy * qy > 0.98) {
                    assertNull("Home hits ($x, $y) though it is off the preview's disc", home)
                    continue
                }
                assertNotNull("Home misses ($x, $y) though it is on the preview's disc", home)
                assertTrue(projection.latLonAt(qx, qy, preview))
                // Home's taps arrive as floats; 1e-4° is about ten metres.
                assertEquals(home!!.lat, preview[0], 1e-4)
                assertEquals(home.lon, preview[1], 1e-4)
                compared++
            }
        }
        assertTrue("too few points on the disc to mean anything: $compared", compared > 12)
    }

    @Test
    fun `the preview's edge is Home's horizon`() {
        // Home's tap ray cast snaps near misses onto the limb, so whether a ray
        // just outside hits says nothing; where one just inside lands does. From
        // distance 4 the horizon is acos(1/4) = 75.5° from the centre.
        val height = 2000f
        val width = 1000f
        val radius = SphereProjection.homeGlobeDiameter(height) / 2f
        val inside = GlobeCamera(latitude = 0.0, longitude = 0.0).latLonAt(width / 2f + radius - 0.5f, height / 2f, width, height)
        assertNotNull(inside)
        val preview = DoubleArray(2)
        val projection = SphereProjection(GlobeCamera(latitude = 0.0, longitude = 0.0))
        assertTrue(projection.latLonAt((radius - 0.5) / radius, 0.0, preview))
        assertEquals(inside!!.lon, preview[1], 1e-2)

        assertTrue(projection.latLonAt(1.0, 0.0, preview))
        assertEquals(0.0, preview[0], 1e-9)
        assertEquals(Math.toDegrees(kotlin.math.acos(0.25)), preview[1], 1e-6)
    }

    @Test
    fun `the disc is opaque inside, transparent in the corners and soft at the limb`() {
        val texture = EquirectPixels(4, 2, IntArray(8) { 0xFF336699.toInt() })
        val size = 64
        val pixels = SphereProjection(camera).render(texture, size)
        assertEquals(0xFF336699.toInt(), pixels[size / 2 * size + size / 2])
        assertEquals(0, pixels[0])
        assertEquals(0, pixels[size * size - 1])
        val edgeAlphas = (0 until size).map { pixels[size / 2 * size + it] ushr 24 }
        assertTrue("the limb should fade over a pixel", edgeAlphas.any { it in 1..254 })
    }

    @Test
    fun `a texture's columns wrap east to west`() {
        // West edge red, east edge blue: sampling on the antimeridian blends both.
        val texture = EquirectPixels(2, 1, intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
        val color = SphereProjection.sampleBilinear(texture, 0.0, 180.0)
        assertEquals(128.0, ((color shr 16) and 0xFF).toDouble(), 1.0)
        assertEquals(128.0, (color and 0xFF).toDouble(), 1.0)
    }

    @Test
    fun `a spun turntable is the projection at that longitude`() {
        val width = 360
        val height = 180
        // Every texel names its own column, so a frame says where it sampled.
        val texture = EquirectPixels(width, height, IntArray(width * height) { 0xFF000000.toInt() or (it % width) })
        val size = 48
        val lookup = TurntableLookup(camera.latitude, camera.distance, size, width, height)
        val frame = IntArray(size * size)
        lookup.frame(texture, camera.longitude, frame)

        val projection = SphereProjection(camera)
        val out = DoubleArray(2)
        val center = size / 2 * size + size / 2
        projection.latLonAt(1.0 / size, 1.0 / size, out)
        val expectedColumn = ((out[1] + 180.0) / 360.0 * width).toInt()
        assertEquals(expectedColumn.toDouble(), (frame[center] and 0xFFFFFF).toDouble(), 1.0)
        assertEquals(0, frame[0])
    }

    @Test
    fun `closing turns the camera back the short way round`() {
        val home = GlobeCamera(latitude = 20.0, longitude = 170.0)
        val turned = GlobeCamera(latitude = 40.0, longitude = -170.0)
        assertEquals(-170.0, lerpCamera(home, turned, 1f).longitude, 1e-9)
        assertEquals(170.0, lerpCamera(home, turned, 0f).longitude, 1e-9)
        val halfway = lerpCamera(home, turned, 0.5f)
        assertEquals(180.0, kotlin.math.abs(halfway.longitude), 1e-9)
        assertEquals(30.0, halfway.latitude, 1e-9)
    }

    @Test
    fun `hard light leaves flat ground alone and shades slopes`() {
        val color = 0xFF34BE82.toInt()
        val flat = hardLight(color, 128)
        for (shift in listOf(16, 8, 0)) {
            assertEquals(((color shr shift) and 0xFF).toDouble(), ((flat shr shift) and 0xFF).toDouble(), 1.0)
        }
        assertEquals(0xFF000000.toInt(), hardLight(color, 0))
        assertEquals(0xFFFFFFFF.toInt(), hardLight(color, 255))
        assertFalse(hardLight(color, 90) == color)
    }

    @Test
    fun `textures decode at the largest averaged power of two that keeps the target`() {
        assertEquals(4, StyleTextures.sampleSizeFor(8192, 2048))
        assertEquals(2, StyleTextures.sampleSizeFor(4096, 2048))
        assertEquals(1, StyleTextures.sampleSizeFor(4096, 4096))
        assertEquals(2, StyleTextures.sampleSizeFor(8192, 4096))
        assertEquals(8, StyleTextures.sampleSizeFor(8192, 512))
        assertEquals(8, StyleTextures.sampleSizeFor(4096, 512))
    }
}
