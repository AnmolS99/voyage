package com.anmol.voyage.globe

/**
 * Geometry produced by [PolygonTriangulator], as plain buffers.
 *
 * On iOS the triangulator returns `SCNGeometry` directly; here the meshes stay
 * renderer-agnostic so the pipeline is unit-testable on the JVM and Filament
 * (Phase 7.4/7.5) merely wraps the arrays in vertex/index buffers.
 */

/** A point on (or direction from the center of) the globe, single precision. */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** A direction in double precision, used by tap-ray math. */
data class Vec3d(val x: Double, val y: Double, val z: Double)

/**
 * A country's fill mesh: triangles on the sphere surface.
 *
 * @property positions Vertex positions, three floats per vertex.
 * @property uvs Texture coordinates, two floats per vertex, spanning the
 *   country's lon/lat bounding box (the map's both-lists gradient uses them).
 * @property indices Triangle vertex indices, three per triangle.
 * @property gridFallbackRingCount How many rings earcut could not triangulate
 *   and the legacy grid fill covered instead. The shipped dataset needs no
 *   fallbacks, so anything non-zero is worth logging.
 */
class CountryMesh(
    val positions: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val gridFallbackRingCount: Int,
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * A border outline mesh: degenerate quad strips on the border centerline.
 *
 * Every vertex sits ON the border with its miter displacement direction in
 * [miters]; the outline material widens the strip at render time by pushing
 * vertices along their miter (the Filament analogue of the iOS
 * `outlineShaderModifier`), so zoom changes one uniform instead of rebuilding
 * ~335k vertices. Without that displacement the geometry has zero width.
 */
class OutlineMesh(
    val positions: FloatArray,
    val miters: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
}

/** Growable primitive buffers for mesh assembly — no boxing on the hot path. */
internal class FloatArrayBuilder(initialCapacity: Int = 1024) {
    private var storage = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == storage.size) storage = storage.copyOf(storage.size * 2)
        storage[size++] = value
    }

    fun add(x: Float, y: Float, z: Float) {
        add(x); add(y); add(z)
    }

    fun toArray(): FloatArray = storage.copyOf(size)
}

internal class IntArrayBuilder(initialCapacity: Int = 1024) {
    private var storage = IntArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == storage.size) storage = storage.copyOf(storage.size * 2)
        storage[size++] = value
    }

    fun toArray(): IntArray = storage.copyOf(size)
}

internal class DoubleArrayBuilder(initialCapacity: Int = 1024) {
    private var storage = DoubleArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Double) {
        if (size == storage.size) storage = storage.copyOf(storage.size * 2)
        storage[size++] = value
    }

    operator fun get(index: Int): Double = storage[index]

    fun toArray(): DoubleArray = storage.copyOf(size)
}
