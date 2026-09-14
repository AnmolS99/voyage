package com.anmol.voyage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.anmol.voyage.state.GlobeStyle
import java.io.InputStream

/**
 * The decoded Earth texture for each [GlobeStyle], shared by the globe and the
 * flat map and kept for the life of the process.
 *
 * The images live in `shared/data/textures/`, the same files iOS bundles. They
 * are decoded to at most [MAX_TEXTURE_WIDTH] pixels wide: two of the three are
 * 8192 × 4096, which is 128 MB as a bitmap — over the 100 MB a `Canvas` will
 * draw, and past the texture size a GPU is guaranteed to accept. Halved, they
 * are 32 MB and within both.
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

    private fun decode(style: GlobeStyle): Bitmap? {
        val started = SystemClock.elapsedRealtime()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openAsset(style.textureAsset).use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth)
            // Not HARDWARE, the platform's default choice for large images: the
            // globe copies these pixels into a Filament texture, and a hardware
            // bitmap's pixels cannot be read back.
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openAsset(style.textureAsset).use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            Log.w(TAG, "could not decode ${style.textureAsset}")
            return null
        }
        // Drawn far smaller than its size on the map, so it needs mipmaps there
        // to not shimmer; the globe generates its own.
        bitmap.setHasMipMap(true)
        Log.i(
            TAG,
            "decoded ${style.textureAsset} at ${bitmap.width}x${bitmap.height} " +
                "in ${SystemClock.elapsedRealtime() - started} ms",
        )
        return bitmap
    }

    companion object {
        /** See the class comment: the widest texture a GPU and a `Canvas` both take. */
        const val MAX_TEXTURE_WIDTH = 4096

        private const val RETAINED_STYLES = 2

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
            val assets = context.applicationContext.assets
            instance = EarthTextureCache { name -> assets.open(name) }
        }

        private const val TAG = "EarthTextureCache"
    }
}
