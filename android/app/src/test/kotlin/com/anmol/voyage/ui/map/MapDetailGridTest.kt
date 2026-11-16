package com.anmol.voyage.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tiles of the full-size Earth image the map decodes. Decoding itself is
 * `BitmapRegionDecoder`, which the JVM does not have; this is the decision that
 * keeps the map from ever holding the whole 128 MB image.
 */
class MapDetailGridTest {

    private val grid = MapDetailGrid(imageWidth = 8192, imageHeight = 4096)
    private val tile = MapDetailGrid.TILE

    /** A phone held upright: the map fills its height, so only a slice of its width shows. */
    private val portrait = MapProjection(viewWidth = 1080f, viewHeight = 2160f)

    /** The same phone on its side: the whole map, exactly as wide as the view. */
    private val landscape = MapProjection(viewWidth = 2160f, viewHeight = 1080f)

    private fun visible(projection: MapProjection, scale: Float = 1f, offsetX: Float = 0f, offsetY: Float = 0f) =
        grid.visible(projection, scale, offsetX, offsetY, bitmapWidth = 4096)

    @Test
    fun `the image is cut into whole tiles`() {
        assertEquals(8192 / tile, grid.columns)
        assertEquals(4096 / tile, grid.rows)
        assertEquals(DetailBounds(tile, 0, 2 * tile, tile), grid.bounds(DetailTile(column = 1, row = 0)))
    }

    @Test
    fun `a tile is decoded one pixel past each edge the image has`() {
        assertEquals(
            DetailBounds(tile - 1, tile - 1, 2 * tile + 1, 2 * tile + 1),
            grid.decodedBounds(DetailTile(column = 1, row = 1)),
        )
        assertEquals(DetailBounds(0, 0, tile + 1, tile + 1), grid.decodedBounds(DetailTile(0, 0)))
        val last = DetailTile(grid.columns - 1, grid.rows - 1)
        assertEquals(DetailBounds(8192 - tile - 1, 4096 - tile - 1, 8192, 4096), grid.decodedBounds(last))
    }

    @Test
    fun `no tiles while the bitmap has a pixel for every pixel of screen`() {
        // 2160 px of screen across the whole map: the 4096 px bitmap is being shrunk.
        assertTrue(visible(landscape).isEmpty())
        // Zoomed in until the map is as wide as the bitmap, and no further.
        assertTrue(visible(landscape, scale = 4096f / 2160f).isEmpty())
    }

    @Test
    fun `an upright phone needs detail before any zooming`() {
        // The map is 4320 px wide to fill 2160 px of height: wider than the bitmap.
        val tiles = visible(portrait)
        // A quarter of the map's width is in view, centred: image columns 3072 to 5120.
        assertEquals((3072 / tile until 5120 / tile).toSet(), tiles.map { it.column }.toSet())
        assertEquals((0 until grid.rows).toSet(), tiles.map { it.row }.toSet())
        assertEquals(tiles.size, tiles.toSet().size)
    }

    @Test
    fun `zooming in asks for fewer tiles, not more`() {
        val wide = visible(landscape, scale = 2f)
        val close = visible(landscape, scale = 8f)
        assertTrue(wide.isNotEmpty())
        assertTrue("${close.size} tiles at 8x, ${wide.size} at 2x", close.size < wide.size)
    }

    @Test
    fun `panning moves the tiles with the view`() {
        // Dragging the map right brings its western half into view.
        val scale = 4f
        val (offsetX, _) = landscape.clampOffset(10_000f, 0f, scale)
        val west = visible(landscape, scale, offsetX = offsetX)
        assertTrue(west.all { it.column < grid.columns / 2 })
        assertTrue(west.any { it.column == 0 })
    }

    @Test
    fun `the tiles nearest the middle of the screen come first`() {
        val tiles = visible(portrait)
        val middleRows = setOf(grid.rows / 2 - 1, grid.rows / 2)
        assertTrue(tiles.first().row in middleRows)
        assertTrue(tiles.last().row !in middleRows)
    }
}
