package com.anmol.voyage.ui.settings

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.graphics.scale
import com.anmol.voyage.data.EarthTextureCache
import com.anmol.voyage.state.GlobeStyle

/**
 * The Earth textures as the settings pickers and their preview draw them:
 * smaller than Home's, and with the relief already hard-lit in.
 *
 * They are decoded straight from the assets rather than taken from
 * [EarthTextureCache], whose two slots hold the globe's and the map's styles —
 * previewing the third style through it would evict one Home is about to draw.
 * Baking the relief in once is what lets every renderer here sample a single
 * image: it is the same blend, on the same encoded values, that Home applies
 * per pixel. Like Home, there is no relief below API 29 (see
 * [EarthTextureCache.relief]).
 */
internal object StyleTextures {

    /** Thumbnails: a 64 dp globe and an 88 dp map are ~200–270 px on a 3× screen. */
    const val THUMBNAIL_WIDTH = 512

    /**
     * The preview globe: its front half spans ~1,000 texels across a disc
     * about that many pixels wide, so it is sharp without shimmering at the limb.
     */
    const val GLOBE_PREVIEW_WIDTH = 2048

    /** The preview map is twice the screen's height across, as Home's is. */
    const val MAP_PREVIEW_WIDTH = EarthTextureCache.MAX_TEXTURE_WIDTH

    private data class Key(val style: GlobeStyle, val width: Int, val terrain: Boolean)

    /** Every thumbnail, both ways: six images of half a megabyte. */
    private val thumbnails = HashMap<Key, Bitmap>()

    /** The thumbnail texture if it has already been loaded, without loading it. */
    fun cachedThumbnail(style: GlobeStyle, terrain: Boolean): Bitmap? =
        synchronized(thumbnails) { thumbnails[Key(style, THUMBNAIL_WIDTH, terrain)] }

    /** The thumbnail texture, loading it on first call. Off the main thread. */
    fun thumbnail(assets: AssetManager, style: GlobeStyle, terrain: Boolean): Bitmap? {
        cachedThumbnail(style, terrain)?.let { return it }
        val loaded = load(assets, style, THUMBNAIL_WIDTH, terrain) ?: return null
        synchronized(thumbnails) { thumbnails[Key(style, THUMBNAIL_WIDTH, terrain)] = loaded }
        return loaded
    }

    /**
     * [style]'s texture [width] pixels wide, relief and all when [terrain].
     * Not cached: the preview holds what it shows and lets it go on close.
     * Off the main thread.
     */
    fun load(assets: AssetManager, style: GlobeStyle, width: Int, terrain: Boolean): Bitmap? {
        val texture = decode(assets, style.textureAsset, width) ?: return null
        if (!terrain || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return texture
        val shades = reliefShades(assets, width) ?: return texture
        val pixels = texture.pixels()
        for (i in pixels.indices) pixels[i] = hardLight(pixels[i], shades[i].toInt() and 0xFF)
        texture.setPixels(pixels, 0, texture.width, 0, 0, texture.width, texture.height)
        return texture
    }

    /** The relief's 8-bit shades at each width asked for: at most a few megabytes. */
    private val reliefs = HashMap<Int, ByteArray>()

    /**
     * The relief, [width] pixels wide, one byte a pixel. Decoded under a lock,
     * since it is decoded whole (see [decode]) and three thumbnails ask at once.
     */
    private fun reliefShades(assets: AssetManager, width: Int): ByteArray? = synchronized(reliefs) {
        reliefs[width] ?: decode(assets, EarthTextureCache.RELIEF_ASSET, width)?.let { relief ->
            val pixels = relief.pixels()
            relief.recycle()
            ByteArray(pixels.size) { pixels[it].toByte() }
        }?.also { reliefs[width] = it }
    }

    /**
     * An asset decoded to exactly [width] × [width] / 2, ARGB, mutable — and
     * averaged down rather than subsampled, or the relief's fine ridges alias
     * into noise at thumbnail size.
     *
     * A JPEG's decoder downsamples by averaging (DCT scaling, up to 8×), so the
     * textures use it; a PNG's skips pixels, so the relief is decoded whole.
     * Either is then halved with filtering until it is the size asked for.
     */
    private fun decode(assets: AssetManager, asset: String, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val averages = bounds.outMimeType == "image/jpeg"
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (averages) sampleSizeFor(bounds.outWidth, width) else 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = assets.open(asset).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        while (bitmap.width != width) {
            val next = if (bitmap.width >= width * 2) bitmap.width / 2 else width
            val scaled = bitmap.scale(next, next / 2, filter = true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
    }

    /**
     * The largest power-of-two downsampling, up to the 8× a JPEG decoder
     * averages over, that keeps the image at least [target] wide.
     */
    internal fun sampleSizeFor(sourceWidth: Int, target: Int): Int {
        var sample = 1
        while (sample < MAX_AVERAGED_SAMPLE && sourceWidth / (sample * 2) >= target) sample *= 2
        return sample
    }

    private const val MAX_AVERAGED_SAMPLE = 8
}

/** The bitmap's pixels as packed ARGB. */
internal fun Bitmap.pixels(): IntArray =
    IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

/** The bitmap as the projection's texture. */
internal fun Bitmap.toEquirect(): EquirectPixels = EquirectPixels(width, height, pixels())
