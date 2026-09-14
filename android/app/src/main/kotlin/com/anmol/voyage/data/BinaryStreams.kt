package com.anmol.voyage.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.Flushable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The primitives the build-time caches are written in — [CountriesFile] and
 * `WorldMeshesFile`: little-endian numbers, and length-prefixed arrays and
 * strings.
 *
 * Little-endian because every device and build machine the app meets is, so a
 * bulk array read on the device is a straight copy rather than a byte swap per
 * element — and the meshes file is ~13 MB of floats and ints.
 *
 * Both halves live in the app's sources and are compiled into the build tool
 * too (`tools/world-cache`), so the writer and the reader are one definition of
 * the format and cannot disagree about it.
 */
class BinaryWriter(output: OutputStream) : Flushable {

    private val out = BufferedOutputStream(output, BUFFER_BYTES)
    private var scratch: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)

    /** Marks the file as [magic], at format [version] — see [BinaryReader.header]. */
    fun header(magic: Int, version: Int) {
        int(magic)
        int(version)
    }

    fun boolean(value: Boolean) = out.write(if (value) 1 else 0)

    fun int(value: Int) = emit(Int.SIZE_BYTES) { putInt(value) }

    fun double(value: Double) = emit(Double.SIZE_BYTES) { putDouble(value) }

    fun string(value: String) {
        val bytes = value.encodeToByteArray()
        int(bytes.size)
        out.write(bytes)
    }

    fun ints(values: IntArray) {
        int(values.size)
        emit(values.size * Int.SIZE_BYTES) { asIntBuffer().put(values) }
    }

    fun floats(values: FloatArray) {
        int(values.size)
        emit(values.size * Float.SIZE_BYTES) { asFloatBuffer().put(values) }
    }

    fun doubles(values: DoubleArray) {
        int(values.size)
        emit(values.size * Double.SIZE_BYTES) { asDoubleBuffer().put(values) }
    }

    /** A presence flag, then [value] written by [write] when there is one. */
    inline fun <T : Any> nullable(value: T?, write: BinaryWriter.(T) -> Unit) {
        boolean(value != null)
        if (value != null) write(value)
    }

    override fun flush() = out.flush()

    /** Writes [byteCount] bytes that [fill] puts into a little-endian buffer. */
    private inline fun emit(byteCount: Int, fill: ByteBuffer.() -> Unit) {
        if (scratch.capacity() < byteCount) {
            scratch = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        }
        scratch.clear()
        scratch.fill()
        out.write(scratch.array(), 0, byteCount)
    }
}

/** Reads what [BinaryWriter] wrote, in the same order. */
class BinaryReader(input: InputStream) {

    private val data = DataInputStream(BufferedInputStream(input, BUFFER_BYTES))
    private var scratch = ByteArray(0)

    /**
     * Checks the file is [magic] at format [version]. A mismatch means the app
     * and the file were built from different code, so it fails rather than
     * reading garbage.
     */
    fun header(magic: Int, version: Int) {
        val foundMagic = int()
        if (foundMagic != magic) {
            throw IOException("expected file magic ${magic.toString(16)}, found ${foundMagic.toString(16)}")
        }
        val foundVersion = int()
        if (foundVersion != version) {
            throw IOException("expected format version $version, found $foundVersion")
        }
    }

    fun boolean(): Boolean = when (val byte = data.read()) {
        0 -> false
        1 -> true
        -1 -> throw EOFException()
        else -> throw IOException("corrupt boolean $byte")
    }

    fun int(): Int = bytes(Int.SIZE_BYTES).int

    fun double(): Double = bytes(Double.SIZE_BYTES).double

    /** A length prefix — rejected when negative, so corruption fails here rather than in an allocation. */
    fun size(): Int = int().also { if (it < 0) throw IOException("corrupt length $it") }

    fun string(): String {
        val size = size()
        bytes(size)
        return scratch.decodeToString(0, size)
    }

    fun ints(): IntArray {
        val size = size()
        return IntArray(size).also { bytes(size * Int.SIZE_BYTES).asIntBuffer().get(it) }
    }

    fun floats(): FloatArray {
        val size = size()
        return FloatArray(size).also { bytes(size * Float.SIZE_BYTES).asFloatBuffer().get(it) }
    }

    fun doubles(): DoubleArray {
        val size = size()
        return DoubleArray(size).also { bytes(size * Double.SIZE_BYTES).asDoubleBuffer().get(it) }
    }

    /** Reads the value [BinaryWriter.nullable] wrote, or null when it wrote none. */
    inline fun <T : Any> nullable(read: BinaryReader.() -> T): T? = if (boolean()) read() else null

    /** Checks nothing follows — trailing bytes mean the reader skipped something the writer wrote. */
    fun end() {
        if (data.read() != -1) throw IOException("unexpected data after the end of the file")
    }

    private fun bytes(byteCount: Int): ByteBuffer {
        if (scratch.size < byteCount) scratch = ByteArray(byteCount)
        data.readFully(scratch, 0, byteCount)
        return ByteBuffer.wrap(scratch, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
    }
}

private const val BUFFER_BYTES = 1 shl 16
