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

/**
 * A direction in double precision, used by the tap-ray and camera math.
 *
 * The operators live on the type rather than as private helpers in one file:
 * both [PolygonTriangulator]'s ray/sphere intersection and [GlobeCamera]'s
 * inverse projection need them, and a second private copy is how the two would
 * drift apart.
 */
data class Vec3d(val x: Double, val y: Double, val z: Double) {

    operator fun plus(other: Vec3d) = Vec3d(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3d) = Vec3d(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Double) = Vec3d(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Double) = Vec3d(x / scalar, y / scalar, z / scalar)

    infix fun dot(other: Vec3d): Double = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vec3d) = Vec3d(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )

    fun normalized(): Vec3d = this / kotlin.math.sqrt(this dot this)
}

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
 *
 * @property miters **Four** floats per vertex, not three: `xyz` is the miter
 *   direction and `w` is the visited+wishlist gradient parameter — 0 at the
 *   bottom-left of the outlined shape's lon/lat box, 1 at its top-right, the
 *   same diagonal `CountryStyles` documents for the flat map. They travel
 *   together because Filament custom vertex attributes are `vec4`, so the
 *   gradient rides along in a component the miter was wasting anyway.
 * @property center Center of the mesh's bounding sphere, and [boundingRadius]
 *   its radius. The renderer needs both: Filament culls by bounding volume, and
 *   hiding the sectors past the globe's horizon is a test on this sphere.
 */
class OutlineMesh(
    val positions: FloatArray,
    val miters: FloatArray,
    val indices: IntArray,
    val center: Vec3,
    val boundingRadius: Float,
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

/** A country's fill mesh with the name its color is looked up by. */
class NamedCountryMesh(val name: String, val mesh: CountryMesh)
