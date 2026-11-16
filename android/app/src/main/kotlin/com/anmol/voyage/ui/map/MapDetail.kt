package com.anmol.voyage.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The tiles of the full-size Earth image the map currently has decoded — see
 * [MapDetailGrid] for why it draws any.
 *
 * Tiles are hardware bitmaps: their pixels live on the GPU only, where a
 * bitmap decoded the usual way is held twice once it has been drawn. What is
 * kept is what is on screen and a few more, so the map never holds the 128 MB
 * the whole image would be — a phone showing the whole height of the world
 * has about a quarter of it.
 *
 * @param decoder over the image at full size; owned by this and recycled by [release].
 */
@Stable
class MapDetail(private val decoder: BitmapRegionDecoder) {

    val grid = MapDetailGrid(decoder.width, decoder.height)

    /** What is decoded. Snapshot state, so the map redraws as tiles arrive and leave. */
    val tiles: Map<DetailTile, Bitmap> get() = decoded

    private val decoded = mutableStateMapOf<DetailTile, Bitmap>()

    /** One at a time: a region decoder serves one caller, and the tiles are wanted in order. */
    private val decoding = Dispatchers.IO.limitedParallelism(1)

    private var released = false

    /**
     * Makes [wanted] what is shown: decodes the ones missing, in the order
     * given, and lets go of tiles no longer in view beyond [SPARE_TILES].
     * Call from the main thread; cancel it when what is wanted changes.
     */
    suspend fun show(wanted: List<DetailTile>) {
        if (released) return
        val spare = decoded.keys.filter { it !in wanted }
        for (tile in spare.dropLast(SPARE_TILES)) decoded.remove(tile)?.recycle()

        for (tile in wanted) {
            if (tile in decoded) continue
            val bitmap = withContext(decoding) { decode(tile) } ?: continue
            if (released) {
                bitmap.recycle()
                return
            }
            decoded[tile] = bitmap
        }
    }

    private fun decode(tile: DetailTile): Bitmap? {
        val bounds = grid.decodedBounds(tile)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        val started = SystemClock.elapsedRealtime()
        return try {
            decoder.decodeRegion(Rect(bounds.left, bounds.top, bounds.right, bounds.bottom), options)
                .also { Log.d(TAG, "decoded $tile in ${SystemClock.elapsedRealtime() - started} ms") }
        } catch (e: Exception) {
            // Out of memory for a tile, or a decoder recycled under it: the
            // bitmap underneath is still drawn.
            Log.w(TAG, "could not decode $tile", e)
            null
        }
    }

    /** Frees every tile and the decoder. The map draws its bitmap alone from here on. */
    fun release() {
        if (released) return
        released = true
        for (bitmap in decoded.values) bitmap.recycle()
        decoded.clear()
        // Behind any decode still running, which a recycled decoder would fail.
        decoding.dispatch(EmptyCoroutineContext) { decoder.recycle() }
    }

    private companion object {
        const val TAG = "MapDetail"

        /** Tiles kept after they leave the screen, so a small pan back costs nothing. */
        const val SPARE_TILES = 4
    }
}
