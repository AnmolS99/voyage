package com.anmol.voyage.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * An Earth texture at its full size, in the form the GPU holds it: every mip
 * level as ASTC 4 × 4 blocks, each a view of one memory-mapped file.
 *
 * This is how the globe shows an 8192 × 4096 image. As a bitmap that is 128 MB,
 * and 171 MB on the GPU once it has mipmaps; compressed it is 43 MB — what the
 * 4096 px bitmap it replaces costs there, for four times the pixels — and
 * nothing on the Java heap, since the levels are read straight from the page
 * cache. iOS holds the same image the same way (`ios/voyage/EarthTexture.swift`).
 *
 * @property levels level 0 first, down to 1 × 1. Direct buffers, as Filament
 *   reads a texture from native memory.
 */
class CompressedEarthTexture internal constructor(
    val width: Int,
    val height: Int,
    val levels: List<ByteBuffer>,
)

/**
 * Where each mip level sits in a compressed texture file, and how large one is.
 *
 * Every 4 × 4 block of pixels is [Astc4x4.BLOCK_BYTES], so a level smaller
 * than a block still takes a whole one.
 */
internal class CompressedEarthLayout(val width: Int, val height: Int) {

    init {
        require(isSupported(width, height)) { "$width x $height is not a power-of-two texture" }
    }

    class Level(val width: Int, val height: Int, val offset: Long, val bytes: Int)

    val levels: List<Level> = buildList {
        var w = width
        var h = height
        var offset = HEADER_BYTES.toLong()
        while (true) {
            val bytes = Astc4x4.encodedBytes(w, h)
            add(Level(w, h, offset, bytes))
            offset += bytes
            if (w == 1 && h == 1) break
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
        }
    }

    val fileBytes: Long = levels.last().let { it.offset + it.bytes }

    companion object {
        /** Magic, format version, width, height, then the source's key as a long. */
        const val HEADER_BYTES = 24
        const val MAGIC = 0x41535443 // "ASTC"
        const val VERSION = 1

        /**
         * Powers of two only: every level then halves exactly, and the Earth
         * textures are all 2:1 powers of two.
         */
        fun isSupported(width: Int, height: Int): Boolean =
            width >= 4 && height >= 4 && width and (width - 1) == 0 && height and (height - 1) == 0
    }
}

/**
 * Reads and writes [CompressedEarthTexture] files.
 *
 * The encoder is [Astc4x4]. The image is never decoded whole — it is read in
 * [BAND_ROWS]-row strips, each a few megabytes, encoded on a few low-priority
 * threads — so compressing an 8K texture peaks at tens of megabytes rather
 * than the 128 MB its bitmap would be.
 */
internal object CompressedEarthTextureFile {

    private const val TAG = "CompressedEarthTexture"

    /** A multiple of a block's 4 rows, and even, so each strip halves on its own. */
    private const val BAND_ROWS = 128

    /**
     * The texture in [file] if it was compressed from the source [sourceKey]
     * names; null if there is none, or it is another image's or cut short.
     */
    fun open(file: File, sourceKey: Long): CompressedEarthTexture? {
        if (!file.isFile || file.length() < CompressedEarthLayout.HEADER_BYTES) return null
        return try {
            RandomAccessFile(file, "r").use { handle ->
                val map = handle.channel.map(FileChannel.MapMode.READ_ONLY, 0, handle.length())
                    .order(ByteOrder.BIG_ENDIAN)
                val magic = map.getInt(0)
                val version = map.getInt(4)
                val width = map.getInt(8)
                val height = map.getInt(12)
                val key = map.getLong(16)
                if (magic != CompressedEarthLayout.MAGIC || version != CompressedEarthLayout.VERSION || key != sourceKey) return null
                if (!CompressedEarthLayout.isSupported(width, height)) return null
                val layout = CompressedEarthLayout(width, height)
                if (layout.fileBytes != handle.length()) return null
                CompressedEarthTexture(width, height, layout.levels.map { map.slice(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not read ${file.name}", e)
            null
        }
    }

    /**
     * Compresses the image [source] opens into [file] and returns it, or null
     * if it cannot be: not a power-of-two image, or no room to write it.
     *
     * Written beside [file] and moved into place, so a half-written texture is
     * never read back.
     */
    fun write(file: File, sourceKey: Long, source: () -> InputStream): CompressedEarthTexture? {
        val started = SystemClock.elapsedRealtime()
        val partial = File(file.parentFile, "${file.name}.partial")
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 6)
        val pool = Executors.newFixedThreadPool(threads) { work ->
            Thread {
                // The globe is on screen and turning while this runs.
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                work.run()
            }
        }
        try {
            file.parentFile?.mkdirs()
            val decoder = source().use { newRegionDecoder(it) } ?: return null
            try {
                if (!CompressedEarthLayout.isSupported(decoder.width, decoder.height)) {
                    Log.w(TAG, "${file.name}: ${decoder.width} x ${decoder.height} is not a power of two")
                    return null
                }
                val layout = CompressedEarthLayout(decoder.width, decoder.height)
                RandomAccessFile(partial, "rw").use { handle ->
                    handle.setLength(layout.fileBytes)
                    // Big-endian, a new buffer's order, which is what open() reads.
                    val map = handle.channel.map(FileChannel.MapMode.READ_WRITE, 0, layout.fileBytes)
                    map.putInt(0, CompressedEarthLayout.MAGIC)
                    map.putInt(4, CompressedEarthLayout.VERSION)
                    map.putInt(8, layout.width)
                    map.putInt(12, layout.height)
                    map.putLong(16, sourceKey)

                    var pixels = encodeFromDecoder(decoder, layout.levels[0], map, pool, threads)
                    for (level in layout.levels.drop(1)) {
                        encodeLevel(pixels, level, map, pool)
                        if (level.width > 1 || level.height > 1) {
                            pixels = halved(pixels, level.width, level.height)
                        }
                    }
                    map.force()
                }
            } finally {
                decoder.recycle()
            }
            if (!partial.renameTo(file)) return null
            Log.i(TAG, "compressed ${file.name} on $threads threads in ${SystemClock.elapsedRealtime() - started} ms")
            return open(file, sourceKey)
        } catch (e: Exception) {
            Log.w(TAG, "could not compress ${file.name}", e)
            return null
        } finally {
            pool.shutdownNow()
            partial.delete()
        }
    }

    @Suppress("DEPRECATION") // The replacement is API 31; this is the same call.
    private fun newRegionDecoder(stream: InputStream): BitmapRegionDecoder? =
        BitmapRegionDecoder.newInstance(stream, false)

    /**
     * Encodes level 0 strip by strip from the image itself, and returns level
     * 1's pixels — each strip averaged down as it goes by.
     */
    private fun encodeFromDecoder(
        decoder: BitmapRegionDecoder,
        level: CompressedEarthLayout.Level,
        map: ByteBuffer,
        pool: ExecutorService,
        threads: Int,
    ): ByteArray {
        val width = level.width
        val half = ByteArray((width / 2) * (level.height / 2) * 3)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        // Decoding outruns encoding; this keeps the strips waiting their turn
        // to a couple per thread instead of the whole image.
        val waiting = Semaphore(threads + 1)
        val work = (0 until level.height step BAND_ROWS).map { top ->
            val rows = minOf(BAND_ROWS, level.height - top)
            waiting.acquire()
            val strip = decoder.decodeRegion(Rect(0, top, width, top + rows), options)
                ?: error("could not decode rows $top to ${top + rows}")
            pool.submit {
                try {
                    val scratch = Scratch.forWidth(width)
                    strip.getPixels(scratch.argb, 0, width, 0, 0, width, rows)
                    strip.recycle()
                    val rgb = scratch.rgb
                    for (i in 0 until width * rows) {
                        val pixel = scratch.argb[i]
                        rgb[i * 3] = (pixel shr 16).toByte()
                        rgb[i * 3 + 1] = (pixel shr 8).toByte()
                        rgb[i * 3 + 2] = pixel.toByte()
                    }
                    encodeBand(rgb, 0, width, rows, map.slice(level, top))
                    halveInto(rgb, 0, width, rows, half, (top / 2) * (width / 2) * 3)
                } finally {
                    waiting.release()
                }
            }
        }
        work.forEach { it.get() }
        return half
    }

    /** Encodes one whole level from its [pixels], RGB bytes. */
    private fun encodeLevel(pixels: ByteArray, level: CompressedEarthLayout.Level, map: ByteBuffer, pool: ExecutorService) {
        val work = (0 until level.height step BAND_ROWS).map { top ->
            Callable {
                val rows = minOf(BAND_ROWS, level.height - top)
                encodeBand(pixels, top * level.width * 3, level.width, rows, map.slice(level, top))
            }
        }
        pool.invokeAll(work).forEach { it.get() }
    }

    private fun encodeBand(rgb: ByteArray, offset: Int, width: Int, rows: Int, out: ByteBuffer) {
        val blocks = Scratch.forWidth(width).blocks
        Astc4x4.encode(rgb, offset, width, rows, blocks, 0)
        out.put(blocks, 0, Astc4x4.encodedBytes(width, rows))
    }

    /** The next level down: each 2 × 2 block averaged, rounded rather than truncated. */
    internal fun halved(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val half = ByteArray(maxOf(1, width / 2) * maxOf(1, height / 2) * 3)
        halveInto(pixels, 0, width, height, half, 0)
        return half
    }

    private fun halveInto(source: ByteArray, sourceOffset: Int, width: Int, rows: Int, target: ByteArray, targetOffset: Int) {
        val halfWidth = maxOf(1, width / 2)
        val halfRows = maxOf(1, rows / 2)
        // A level one pixel wide or tall has no neighbour on that axis to
        // average with, so it is read twice.
        val right = if (width > 1) 3 else 0
        val below = if (rows > 1) width * 3 else 0
        var out = targetOffset
        for (y in 0 until halfRows) {
            var at = sourceOffset + y * 2 * width * 3
            for (x in 0 until halfWidth) {
                for (channel in 0 until 3) {
                    val sum = (source[at + channel].toInt() and 0xFF) +
                        (source[at + channel + right].toInt() and 0xFF) +
                        (source[at + channel + below].toInt() and 0xFF) +
                        (source[at + channel + below + right].toInt() and 0xFF)
                    target[out++] = ((sum + 2) / 4).toByte()
                }
                at += 6
            }
        }
    }

    /** The bytes of [level] from row [top] on — whole blocks, as [BAND_ROWS] is. */
    private fun ByteBuffer.slice(level: CompressedEarthLayout.Level, top: Int = 0): ByteBuffer {
        val start = level.offset.toInt() + Astc4x4.encodedBytes(level.width, top)
        val view = duplicate()
        view.position(start)
        view.limit(level.offset.toInt() + level.bytes)
        return view.slice()
    }

    /** One strip's working memory, kept per encoding thread rather than per strip. */
    private class Scratch(width: Int) {
        val argb = IntArray(width * BAND_ROWS)
        val rgb = ByteArray(width * BAND_ROWS * 3)
        val blocks = ByteArray(Astc4x4.encodedBytes(width, BAND_ROWS))
        val width = width

        companion object {
            private val perThread = ThreadLocal<Scratch>()

            /** Level 0 comes first and is the widest, so later levels reuse its buffers. */
            fun forWidth(width: Int): Scratch =
                perThread.get()?.takeIf { it.width >= width } ?: Scratch(width).also(perThread::set)
        }
    }
}
