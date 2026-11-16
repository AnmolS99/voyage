package com.anmol.voyage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.anmol.voyage.state.GlobeStyle
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The decoded Earth texture for each [GlobeStyle], shared by the globe and the
 * flat map and kept for the life of the process.
 *
 * The images live in `shared/data/textures/`, the same files iOS bundles. As
 * bitmaps they are decoded to at most [MAX_TEXTURE_WIDTH] pixels wide: two of
 * the three are 8192 × 4096, which is 128 MB as a bitmap — over the 100 MB a
 * `Canvas` will draw. Halved, they are 32 MB.
 *
 * That bitmap is not the most either renderer shows. The globe takes a wider
 * image at full size as a [CompressedEarthTexture] ([detail]), and the map
 * reads the part of it on screen ([regionDecoder]).
 *
 * Decoding costs hundreds of milliseconds, so callers must be off the main
 * thread the first time. At most [RETAINED_STYLES] are held — the globe's style
 * and the map's — so switching styles back and forth never grows the cache.
 */
class EarthTextureCache(
    private val openAsset: (String) -> InputStream,
    /** Where compressed textures are kept; null keeps none, and [detail] is then always null. */
    private val detailDirectory: File? = null,
    /** Identifies an asset's contents, so a texture compressed from an older image is not reused. */
    private val assetKey: (String) -> Long = { 0L },
) {

    private val decoded = object : LinkedHashMap<GlobeStyle, Bitmap>(RETAINED_STYLES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GlobeStyle, Bitmap>) =
            size > RETAINED_STYLES
    }

    /** The texture for [style] if it is already decoded, without decoding it. */
    fun cached(style: GlobeStyle): Bitmap? = synchronized(decoded) { decoded[style] }

    /**
     * The texture for [style], decoding it on first call. Null only if the image
     * could not be decoded, in which case the renderers fall back to flat colors
     * as iOS's do when `UIImage(named:)` finds nothing.
     */
    fun get(style: GlobeStyle): Bitmap? = synchronized(decoded) {
        decoded[style] ?: decode(style)?.also { decoded[style] = it }
    }

    @Volatile
    private var reliefDecoded = false
    private var relief: Bitmap? = null

    /**
     * The shaded-relief layer both renderers hard-light over everything they
     * paint, decoding it on first call — off the main thread, like [get].
     *
     * Null where it cannot be drawn, and then neither renderer shades: the map
     * blends it with `BlendMode.Hardlight`, which a hardware `Canvas` only has
     * from API 29, and the globe going without it too is what keeps the two
     * looking alike on the devices below that.
     */
    fun relief(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (reliefDecoded) return relief
        return synchronized(this) {
            if (!reliefDecoded) {
                relief = decodeAsset(RELIEF_ASSET)
                reliefDecoded = true
            }
            relief
        }
    }

    /** [relief] if it is already decoded, without decoding it. */
    fun cachedRelief(): Bitmap? = relief

    private val widths = HashMap<GlobeStyle, Int>()

    /** How wide [style]'s image is, read from its header alone. */
    private fun widthOf(style: GlobeStyle): Int = synchronized(widths) {
        widths.getOrPut(style) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openAsset(style.textureAsset).use { BitmapFactory.decodeStream(it, null, bounds) }
            bounds.outWidth
        }
    }

    /** Whether [style]'s image holds more than its bitmap — [get] — shows. */
    private fun hasDetail(style: GlobeStyle): Boolean = widthOf(style) > MAX_TEXTURE_WIDTH

    private val detailLock = Any()
    private var loadedDetail: Pair<GlobeStyle, CompressedEarthTexture>? = null

    /** [style]'s full-size texture if this process already has it open; never touches the disk. */
    fun loadedDetail(style: GlobeStyle): CompressedEarthTexture? =
        loadedDetail?.takeIf { it.first == style }?.second

    /**
     * [style]'s image at full size for the globe, if it was compressed on an
     * earlier run — a file to map, so milliseconds — and null if it was not, or
     * if the bitmap already is the whole image.
     *
     * @param maxWidth the widest compressed texture the GPU takes; 0 if it
     *   takes none. A wider image is left to its bitmap.
     */
    fun cachedDetail(style: GlobeStyle, maxWidth: Int): CompressedEarthTexture? =
        detail(style, maxWidth, compress = false)

    /**
     * As [cachedDetail], but compresses the image if that has not been done:
     * seconds of work on a few background threads, once per image. Only the
     * style last asked for is kept on disk, as on iOS.
     */
    fun detail(style: GlobeStyle, maxWidth: Int): CompressedEarthTexture? =
        detail(style, maxWidth, compress = true)

    private fun detail(style: GlobeStyle, maxWidth: Int, compress: Boolean): CompressedEarthTexture? {
        val directory = detailDirectory ?: return null
        synchronized(detailLock) {
            loadedDetail(style)?.let { return it }
            if (!hasDetail(style) || widthOf(style) > maxWidth) return null
            val asset = style.textureAsset
            val file = File(directory, asset.substringAfterLast('/').substringBeforeLast('.') + ".astc")
            val key = assetKey(asset)
            val texture = CompressedEarthTextureFile.open(file, key)
                ?: if (compress) {
                    directory.listFiles()?.forEach { it.delete() }
                    CompressedEarthTextureFile.write(file, key) { openAsset(asset) }
                } else {
                    null
                }
            if (texture != null) loadedDetail = style to texture
            return texture
        }
    }

    /**
     * A decoder over [style]'s image at full size, for the map to read the part
     * on screen from; null if the bitmap already is the whole image. The
     * caller recycles it.
     */
    fun regionDecoder(style: GlobeStyle): BitmapRegionDecoder? {
        if (!hasDetail(style)) return null
        @Suppress("DEPRECATION") // The replacement is API 31; this is the same call.
        return openAsset(style.textureAsset).use { BitmapRegionDecoder.newInstance(it, false) }
    }

    private fun decode(style: GlobeStyle): Bitmap? = decodeAsset(style.textureAsset)

    private fun decodeAsset(asset: String): Bitmap? {
        val started = SystemClock.elapsedRealtime()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openAsset(asset).use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth)
            // Not HARDWARE, the platform's default choice for large images: the
            // globe copies these pixels into a Filament texture, and a hardware
            // bitmap's pixels cannot be read back.
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openAsset(asset).use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            Log.w(TAG, "could not decode $asset")
            return null
        }
        // Drawn far smaller than its size on the map, so it needs mipmaps there
        // to not shimmer; the globe generates its own.
        bitmap.setHasMipMap(true)
        Log.i(
            TAG,
            "decoded $asset at ${bitmap.width}x${bitmap.height} " +
                "in ${SystemClock.elapsedRealtime() - started} ms",
        )
        return bitmap
    }

    companion object {
        /** See the class comment: the widest texture a GPU and a `Canvas` both take. */
        const val MAX_TEXTURE_WIDTH = 4096

        private const val RETAINED_STYLES = 2

        /**
         * Neutral (128) on flat ground and the ocean, darker on slopes facing
         * away from a northwest light — `scripts/generate_relief_texture.py`.
         */
        internal const val RELIEF_ASSET = "textures/relief.png"

        /** The power-of-two downsampling that brings [width] within [MAX_TEXTURE_WIDTH]. */
        internal fun sampleSizeFor(width: Int): Int {
            var sample = 1
            while (width / sample > MAX_TEXTURE_WIDTH) sample *= 2
            return sample
        }

        @Volatile
        private var instance: EarthTextureCache? = null

        /** The process-wide cache. [install] runs from `VoyageApplication.onCreate`. */
        val shared: EarthTextureCache
            get() = checkNotNull(instance) { "EarthTextureCache.install(context) has not run" }

        fun install(context: Context) {
            val app = context.applicationContext
            val assets = app.assets
            instance = EarthTextureCache(
                openAsset = { name -> assets.open(name) },
                // Caches: the system may clear it, and the texture is rebuilt.
                detailDirectory = File(app.cacheDir, "earth"),
                // The image's size where the APK stores it as it is, which it
                // does a JPEG; otherwise when the app was last updated.
                assetKey = { name ->
                    try {
                        assets.openFd(name).use { it.length }
                    } catch (_: IOException) {
                        app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
                    }
                },
            )
        }

        private const val TAG = "EarthTextureCache"
    }
}
