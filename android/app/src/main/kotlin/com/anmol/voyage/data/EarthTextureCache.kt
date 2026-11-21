package com.anmol.voyage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.anmol.voyage.state.GlobeStyle
import java.io.InputStream

/**
 * The decoded Earth texture for each [GlobeStyle], shared by the globe and the
 * flat map and kept for the life of the process.
 *
 * The build packages them no wider than [EarthTextureFiles.MAX_WIDTH].
 *
 * Decoding costs hundreds of milliseconds, so callers must be off the main
 * thread the first time. At most [RETAINED_STYLES] are held — the globe's style
 * and the map's — so switching styles back and forth never grows the cache.
 */
class EarthTextureCache(private val openAsset: (String) -> InputStream) {

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

    private fun decode(style: GlobeStyle): Bitmap? = decodeAsset(style.textureAsset)

    private fun decodeAsset(asset: String): Bitmap? {
        val started = SystemClock.elapsedRealtime()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openAsset(asset).use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = EarthTextureFiles.sampleSizeFor(bounds.outWidth)
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
        private const val RETAINED_STYLES = 2

        /**
         * Neutral (128) on flat ground and the ocean, darker on slopes facing
         * away from a northwest light — `scripts/generate_relief_texture.py`.
         */
        internal const val RELIEF_ASSET = "textures/relief.png"

        @Volatile
        private var instance: EarthTextureCache? = null

        /** The process-wide cache. [install] runs from `VoyageApplication.onCreate`. */
        val shared: EarthTextureCache
            get() = checkNotNull(instance) { "EarthTextureCache.install(context) has not run" }

        fun install(context: Context) {
            val assets = context.applicationContext.assets
            instance = EarthTextureCache { name -> assets.open(EarthTextureFiles.packagedAsset(name)) }
        }

        private const val TAG = "EarthTextureCache"
    }
}
