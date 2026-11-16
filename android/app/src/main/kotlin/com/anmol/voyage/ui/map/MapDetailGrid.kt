package com.anmol.voyage.ui.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** One [MapDetailGrid.TILE]-pixel square of the full-size Earth image, counted from its top-left. */
data class DetailTile(val column: Int, val row: Int)

/** A rectangle of the full-size image, in its pixels; [right] and [bottom] are exclusive. */
data class DetailBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The full-size Earth image cut into tiles, and which of them the map is
 * showing.
 *
 * The map's bitmap stops at 4096 px — a `Canvas` will not draw the 128 MB an
 * 8192 px one would be — and a phone held upright already stretches that: the
 * map fills the screen's height, so its 2048 rows cover some 2400 pixels before
 * any zooming. So wherever the bitmap is being magnified the map draws the
 * image itself over it, a tile at a time, decoding only what is on screen.
 *
 * Pure geometry: `MapDetail` does the decoding.
 */
class MapDetailGrid(val imageWidth: Int, val imageHeight: Int) {

    val columns: Int = (imageWidth + TILE - 1) / TILE
    val rows: Int = (imageHeight + TILE - 1) / TILE

    /** The pixels [tile] covers. */
    fun bounds(tile: DetailTile): DetailBounds = DetailBounds(
        left = tile.column * TILE,
        top = tile.row * TILE,
        right = minOf((tile.column + 1) * TILE, imageWidth),
        bottom = minOf((tile.row + 1) * TILE, imageHeight),
    )

    /**
     * [bounds] and one pixel more on every side the image has one. Drawn
     * clipped to [bounds], a tile then blends into its neighbour's first pixel
     * at each edge instead of repeating its own, which showed as a faint grid
     * over smooth ground at full zoom.
     */
    fun decodedBounds(tile: DetailTile): DetailBounds = bounds(tile).let {
        DetailBounds(
            left = maxOf(it.left - 1, 0),
            top = maxOf(it.top - 1, 0),
            right = minOf(it.right + 1, imageWidth),
            bottom = minOf(it.bottom + 1, imageHeight),
        )
    }

    /**
     * The tiles in view, nearest the middle of the screen first, or none
     * while the bitmap — [bitmapWidth] pixels wide — still has a pixel for
     * every pixel of screen the map covers.
     */
    fun visible(
        projection: MapProjection,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        bitmapWidth: Int,
    ): List<DetailTile> {
        if (projection.mapWidth * scale <= bitmapWidth) return emptyList()

        // The view's edges, taken back through the pan and zoom to the map
        // rectangle, then to image pixels.
        fun imageX(viewX: Float): Float {
            val mapX = (viewX - projection.viewWidth / 2f - offsetX) / scale +
                projection.viewWidth / 2f - projection.horizontalOffset
            return mapX / projection.mapWidth * imageWidth
        }
        fun imageY(viewY: Float): Float {
            val mapY = (viewY - projection.viewHeight / 2f - offsetY) / scale +
                projection.viewHeight / 2f - projection.verticalOffset
            return mapY / projection.mapHeight * imageHeight
        }
        val left = imageX(0f).coerceIn(0f, imageWidth.toFloat())
        val right = imageX(projection.viewWidth).coerceIn(0f, imageWidth.toFloat())
        val top = imageY(0f).coerceIn(0f, imageHeight.toFloat())
        val bottom = imageY(projection.viewHeight).coerceIn(0f, imageHeight.toFloat())
        if (right <= left || bottom <= top) return emptyList()

        val firstColumn = floor(left / TILE).toInt()
        val lastColumn = minOf(ceil(right / TILE).toInt(), columns) - 1
        val firstRow = floor(top / TILE).toInt()
        val lastRow = minOf(ceil(bottom / TILE).toInt(), rows) - 1
        val centreX = (left + right) / 2f
        val centreY = (top + bottom) / 2f
        return (firstRow..lastRow)
            .flatMap { row -> (firstColumn..lastColumn).map { column -> DetailTile(column, row) } }
            .sortedBy { tile ->
                abs((tile.column + 0.5f) * TILE - centreX) + abs((tile.row + 0.5f) * TILE - centreY)
            }
    }

    companion object {
        /**
         * 1 MB decoded. Tiles twice this size held twice the memory for the
         * same view, most of it past the screen's edges; smaller ones would
         * each cost a pass over the JPEG down to their row for little more.
         */
        const val TILE = 512
    }
}
