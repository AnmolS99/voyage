package com.anmol.voyage.globe

import com.anmol.voyage.data.LatLon
import com.anmol.voyage.data.Polygons
import com.anmol.voyage.data.Ring
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds globe geometry from country rings — the Kotlin port of the iOS
 * `PolygonTriangulator.swift`, deliberately identical in behaviour: country
 * polygons are ear-clipped (with enclave holes) in lon/lat space, triangles
 * and border segments longer than ~2.5° are subdivided so they follow the
 * sphere's curvature, and the result is projected onto the sphere with
 * [latLonToSphere].
 *
 * Where iOS emits `SCNGeometry`, this emits [CountryMesh]/[OutlineMesh]
 * buffers for Filament to wrap. The legacy grid-based fill remains as an
 * automatic fallback for rings earcut cannot triangulate (the current dataset
 * needs no fallbacks — [CountryMesh.gridFallbackRingCount] reports any).
 */
object PolygonTriangulator {

    /**
     * Max triangle edge / border segment length in true angular degrees before
     * subdivision. Keeps fill and outline geometry hugging the sphere instead
     * of cutting chords through it.
     */
    private const val MAX_EDGE_DEGREES = 2.5

    private const val PI_F = Math.PI.toFloat()

    // Convert lat/lon to 3D point on sphere.
    // Computed in single precision exactly as iOS does, so both globes place
    // vertices identically.
    fun latLonToSphere(lat: Double, lon: Double, radius: Float): Vec3 {
        val latRad = lat.toFloat() * PI_F / 180f
        val lonRad = (-lon).toFloat() * PI_F / 180f

        val x = radius * cos(latRad) * cos(lonRad)
        val y = radius * sin(latRad)
        val z = radius * cos(latRad) * sin(lonRad)

        return Vec3(x, y, z)
    }

    /** Inverse of [latLonToSphere]: direction from the globe's center to lat/lon degrees. */
    fun sphereToLatLon(direction: Vec3d): LatLon {
        val length = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        val unitY = direction.y / length
        val lat = asin(unitY.coerceIn(-1.0, 1.0)) * 180 / Math.PI
        val lon = -atan2(direction.z, direction.x) * 180 / Math.PI
        return LatLon(lat = lat, lon = lon)
    }

    /**
     * First crossing of a ray with a sphere of [radius] centered at the origin,
     * returned as the surface direction (unit vector), or null if the ray misses.
     *
     * Tap handling uses this instead of the engine's mesh hit-test, for the same
     * reason iOS does: the atmosphere shell and raised fills/outlines are struck
     * first by oblique rays, and normalizing those hit points skews the tap
     * toward screen center — by several degrees near the screen edge at close
     * zoom.
     *
     * Rays that narrowly miss the sphere — within [limbSlack] × radius at
     * closest approach — are clamped to the closest-approach direction so taps
     * just off the globe's limb still resolve to the country on the horizon.
     */
    fun raySphereSurfaceDirection(
        origin: Vec3d,
        direction: Vec3d,
        radius: Double = 1.0,
        limbSlack: Double = 1.05,
    ): Vec3d? {
        val lengthSquared = direction.dot(direction)
        if (lengthSquared <= 0) return null
        val unitDirection = direction / sqrt(lengthSquared)

        // Parameter of the ray's closest approach to the sphere's center
        val tClosest = -origin.dot(unitDirection)
        if (tClosest <= 0) return null // sphere is behind the ray

        val closest = origin + unitDirection * tClosest
        val closestDistanceSquared = closest.dot(closest)
        val radiusSquared = radius * radius

        if (closestDistanceSquared <= radiusSquared) {
            // Entry point: pull back from closest approach by half the chord
            val halfChord = sqrt(radiusSquared - closestDistanceSquared)
            return (origin + unitDirection * (tClosest - halfChord)).normalized()
        }
        val slack = radius * limbSlack
        if (closestDistanceSquared <= slack * slack) {
            return closest.normalized()
        }
        return null
    }

    // Compute UV texture coordinates from 3D vertices by reverse-mapping to lat/lon
    private fun computeTexCoords(positions: FloatArray, polygons: List<Ring>): FloatArray {
        // Compute overall bounding box across all polygons
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (polygon in polygons) {
            for (i in 0 until polygon.size) {
                val lon = polygon.lon(i)
                val lat = polygon.lat(i)
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
            }
        }
        val lonSpan = maxLon - minLon
        val latSpan = maxLat - minLat

        val uvs = FloatArray(positions.size / 3 * 2)
        for (v in 0 until positions.size / 3) {
            val x = positions[v * 3].toDouble()
            val y = positions[v * 3 + 1].toDouble()
            val z = positions[v * 3 + 2].toDouble()
            val len = sqrt(x * x + y * y + z * z)
            val latDeg = asin(y / len) * 180.0 / Math.PI
            val lonDeg = -atan2(z, x) * 180.0 / Math.PI
            val u = if (lonSpan > 0) (lonDeg - minLon) / lonSpan else 0.5
            val vv = if (latSpan > 0) (latDeg - minLat) / latSpan else 0.5
            uvs[v * 2] = u.toFloat()
            uvs[v * 2 + 1] = vv.toFloat()
        }
        return uvs
    }

    // Ring helpers

    /**
     * Drops consecutive duplicate points (zero-length segments break the miter
     * math) and the duplicate closing point; null if fewer than 3 points remain.
     * (iOS also drops malformed short coordinates, which the flat [Ring] layout
     * cannot represent.)
     */
    private fun cleanRing(ring: Ring): Ring? {
        val coords = DoubleArrayBuilder(ring.lonLat.size)
        for (i in 0 until ring.size) {
            val lon = ring.lon(i)
            val lat = ring.lat(i)
            if (coords.size >= 2 && coords[coords.size - 2] == lon && coords[coords.size - 1] == lat) continue
            coords.add(lon)
            coords.add(lat)
        }
        var count = coords.size / 2
        if (count > 1 && coords[0] == coords[(count - 1) * 2] && coords[1] == coords[(count - 1) * 2 + 1]) {
            count -= 1
        }
        if (count < 3) return null
        return Ring(coords.toArray().copyOf(count * 2))
    }

    /** Absolute shoelace area of a ring in lon/lat space. */
    private fun ringArea(coords: Ring): Double {
        var sum = 0.0
        var j = coords.size - 1
        for (i in 0 until coords.size) {
            sum += (coords.lon(j) - coords.lon(i)) * (coords.lat(j) + coords.lat(i))
            j = i
        }
        return abs(sum / 2)
    }

    /** Length of a lon/lat segment in true angular degrees (longitude scaled by latitude). */
    private fun angularLength(aLon: Double, aLat: Double, bLon: Double, bLat: Double): Double {
        val midLat = (aLat + bLat) / 2 * Math.PI / 180
        val dLon = (bLon - aLon) * cos(midLat)
        val dLat = bLat - aLat
        return sqrt(dLon * dLon + dLat * dLat)
    }

    // Country fill

    /**
     * Create country fill geometry by ear-clipping triangulation (with holes) in
     * lon/lat space, subdividing large triangles to follow sphere curvature,
     * then projecting onto the sphere.
     *
     * @param holes Inner ring coordinates to exclude from the fill (e.g., the
     *   Lesotho enclave in South Africa).
     */
    fun createCountryGeometry(polygons: List<Ring>, holes: List<Ring> = emptyList(), radius: Float = 1.003f): CountryMesh? {
        val allPositions = FloatArrayBuilder()
        val allIndices = IntArrayBuilder()
        var gridFallbacks = 0

        // Assign each hole ring to the outer ring that contains it
        val holesForPolygon = HashMap<Int, MutableList<Ring>>()
        for (hole in holes) {
            if (hole.size == 0) continue
            val index = polygons.indexOfFirst { Polygons.contains(it, lon = hole.lon(0), lat = hole.lat(0)) }
            if (index >= 0) {
                holesForPolygon.getOrPut(index) { mutableListOf() }.add(hole)
            }
        }

        for ((index, polygon) in polygons.withIndex()) {
            val ringHoles = holesForPolygon[index] ?: emptyList()
            if (!appendEarcutFill(polygon, ringHoles, radius, allPositions, allIndices)) {
                // Ring earcut couldn't handle (e.g. self-intersecting) — fall back to grid fill
                gridFallbacks += 1
                appendGridFill(polygon, ringHoles, radius, allPositions, allIndices)
            }
        }

        if (allPositions.size == 0 || allIndices.size == 0) return null

        val positions = allPositions.toArray()
        return CountryMesh(
            positions = positions,
            uvs = computeTexCoords(positions, polygons),
            indices = allIndices.toArray(),
            gridFallbackRingCount = gridFallbacks,
        )
    }

    /**
     * Triangulates one outer ring (+ its holes) with earcut. Returns false if
     * the triangulation fails or covers a wrong area, so the caller can fall
     * back.
     */
    private fun appendEarcutFill(
        polygon: Ring,
        holes: List<Ring>,
        radius: Float,
        positions: FloatArrayBuilder,
        indices: IntArrayBuilder,
    ): Boolean {
        val outer = cleanRing(polygon) ?: return false

        val flat = DoubleArrayBuilder(outer.lonLat.size + holes.sumOf { it.lonLat.size })
        val holeIndices = IntArrayBuilder(holes.size.coerceAtLeast(1))
        var expectedArea = ringArea(outer)

        for (value in outer.lonLat) {
            flat.add(value)
        }
        for (hole in holes) {
            val cleaned = cleanRing(hole) ?: continue
            holeIndices.add(flat.size / 2)
            for (value in cleaned.lonLat) {
                flat.add(value)
            }
            expectedArea -= ringArea(cleaned)
        }
        if (expectedArea <= 0) return false

        val triangulated = Earcut.triangulate(flat.toArray(), holeIndices.toArray())
        if (triangulated.isEmpty()) return false

        // Points as flat [lon, lat] pairs; subdivision appends midpoints
        val points = DoubleArrayBuilder(flat.size)
        for (i in 0 until flat.size) {
            points.add(flat[i])
        }

        // Validate coverage: a degenerate input (e.g. self-intersecting ring) produces a
        // triangulation whose total area deviates from the polygon's true area.
        var triangulatedArea = 0.0
        var t = 0
        while (t < triangulated.size) {
            val a = triangulated[t] * 2
            val b = triangulated[t + 1] * 2
            val c = triangulated[t + 2] * 2
            val cross1 = (points[b] - points[a]) * (points[c + 1] - points[a + 1])
            val cross2 = (points[c] - points[a]) * (points[b + 1] - points[a + 1])
            triangulatedArea += abs(cross1 - cross2) / 2
            t += 3
        }
        if (abs(triangulatedArea - expectedArea) > expectedArea * 0.05) return false

        val triangles = subdivideForCurvature(points, triangulated)

        val base = positions.size / 3
        for (i in 0 until points.size / 2) {
            val v = latLonToSphere(lat = points[i * 2 + 1], lon = points[i * 2], radius = radius)
            positions.add(v.x, v.y, v.z)
        }
        for (index in triangles) {
            indices.add(base + index)
        }
        return true
    }

    /**
     * Splits triangles until no edge exceeds [MAX_EDGE_DEGREES]. Midpoints are
     * shared between neighboring triangles (edge cache), keeping the mesh
     * watertight.
     */
    private fun subdivideForCurvature(points: DoubleArrayBuilder, triangles: IntArray): IntArray {
        val midpointCache = HashMap<Long, Int>()

        fun midpoint(i: Int, j: Int): Int {
            val key = (minOf(i, j).toLong() shl 32) or maxOf(i, j).toLong()
            midpointCache[key]?.let { return it }
            val index = points.size / 2
            points.add((points[i * 2] + points[j * 2]) / 2)
            points.add((points[i * 2 + 1] + points[j * 2 + 1]) / 2)
            midpointCache[key] = index
            return index
        }

        val output = IntArrayBuilder(triangles.size)
        // Stack of triangles, three indices per entry
        var stack = IntArray(triangles.size.coerceAtLeast(3))
        var top = 0

        fun push(a: Int, b: Int, c: Int) {
            if (top + 3 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top] = a
            stack[top + 1] = b
            stack[top + 2] = c
            top += 3
        }

        var t = 0
        while (t < triangles.size) {
            push(triangles[t], triangles[t + 1], triangles[t + 2])
            t += 3
        }

        while (top > 0) {
            top -= 3
            val a = stack[top]
            val b = stack[top + 1]
            val c = stack[top + 2]

            val ab = angularLength(points[a * 2], points[a * 2 + 1], points[b * 2], points[b * 2 + 1])
            val bc = angularLength(points[b * 2], points[b * 2 + 1], points[c * 2], points[c * 2 + 1])
            val ca = angularLength(points[c * 2], points[c * 2 + 1], points[a * 2], points[a * 2 + 1])
            val longest = maxOf(ab, bc, ca)

            if (longest <= MAX_EDGE_DEGREES) {
                output.add(a); output.add(b); output.add(c)
            } else if (longest == ab) {
                val m = midpoint(a, b)
                push(a, m, c)
                push(m, b, c)
            } else if (longest == bc) {
                val m = midpoint(b, c)
                push(a, b, m)
                push(a, m, c)
            } else {
                val m = midpoint(c, a)
                push(a, b, m)
                push(m, b, c)
            }
        }
        return output.toArray()
    }

    // Grid fill fallback

    /**
     * Legacy adaptive grid-based point-in-polygon fill. Large cells cover the
     * interior; cells near borders subdivide to finer resolution. Kept as a
     * fallback for rings that earcut cannot triangulate.
     */
    private fun appendGridFill(
        polygon: Ring,
        holes: List<Ring>,
        radius: Float,
        allPositions: FloatArrayBuilder,
        allIndices: IntArrayBuilder,
    ) {
        val coords = cleanRing(polygon) ?: return

        // Get bounding box
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (i in 0 until coords.size) {
            val lon = coords.lon(i)
            val lat = coords.lat(i)
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
        }

        val maxSpan = maxOf(maxLon - minLon, maxLat - minLat)
        val startSize: Double
        val minSize: Double
        when {
            maxSpan < 0.01 -> { startSize = 0.0005; minSize = 0.0005 }
            maxSpan < 0.05 -> { startSize = 0.002; minSize = 0.002 }
            maxSpan < 0.2 -> { startSize = 0.01; minSize = 0.01 }
            maxSpan < 0.5 -> { startSize = 0.04; minSize = 0.02 }
            maxSpan < 2.0 -> { startSize = 0.16; minSize = 0.04 }
            maxSpan < 10.0 -> { startSize = 0.5; minSize = 0.125 }
            maxSpan < 30.0 -> { startSize = 2.0; minSize = 0.125 }
            else -> { startSize = 4.0; minSize = 0.125 }
        }

        var cellCount = 0

        fun emitCell(lat: Double, lon: Double, size: Double) {
            // Expand interior cells slightly to eliminate T-junction gaps; border cells stay tight
            val overlap = if (size > minSize) size * 0.02 else 0.0
            val baseIndex = allPositions.size / 3
            val v0 = latLonToSphere(lat = lat - overlap, lon = lon - overlap, radius = radius)
            val v1 = latLonToSphere(lat = lat - overlap, lon = lon + size + overlap, radius = radius)
            val v2 = latLonToSphere(lat = lat + size + overlap, lon = lon + size + overlap, radius = radius)
            val v3 = latLonToSphere(lat = lat + size + overlap, lon = lon - overlap, radius = radius)
            allPositions.add(v0.x, v0.y, v0.z)
            allPositions.add(v1.x, v1.y, v1.z)
            allPositions.add(v2.x, v2.y, v2.z)
            allPositions.add(v3.x, v3.y, v3.z)
            allIndices.add(baseIndex); allIndices.add(baseIndex + 1); allIndices.add(baseIndex + 2)
            allIndices.add(baseIndex); allIndices.add(baseIndex + 2); allIndices.add(baseIndex + 3)
            cellCount += 1
        }

        fun hasVertexInCell(ring: Ring, lon: Double, lat: Double, size: Double): Boolean {
            for (i in 0 until ring.size) {
                if (ring.lon(i) >= lon && ring.lon(i) <= lon + size &&
                    ring.lat(i) >= lat && ring.lat(i) <= lat + size
                ) {
                    return true
                }
            }
            return false
        }

        fun addCell(lat: Double, lon: Double, size: Double) {
            val centerLon = lon + size / 2
            val centerLat = lat + size / 2
            val centerIn = Polygons.contains(coords, lon = centerLon, lat = centerLat)

            if (size <= minSize) {
                if (centerIn) {
                    // Exclude cells whose center falls inside a hole (e.g., Lesotho within South Africa)
                    val inHole = holes.any { Polygons.contains(it, lon = centerLon, lat = centerLat) }
                    if (!inHole) emitCell(lat, lon, size)
                }
                return
            }

            val c00 = Polygons.contains(coords, lon = lon, lat = lat)
            val c10 = Polygons.contains(coords, lon = lon + size, lat = lat)
            val c01 = Polygons.contains(coords, lon = lon, lat = lat + size)
            val c11 = Polygons.contains(coords, lon = lon + size, lat = lat + size)

            if (c00 && c10 && c01 && c11 && centerIn) {
                // Corners + center inside — verify edge midpoints to catch concavities
                val half = size / 2
                val edgesIn = Polygons.contains(coords, lon = lon + half, lat = lat) &&
                    Polygons.contains(coords, lon = lon + size, lat = lat + half) &&
                    Polygons.contains(coords, lon = lon + half, lat = lat + size) &&
                    Polygons.contains(coords, lon = lon, lat = lat + half)
                if (edgesIn) {
                    // Before emitting, check whether a hole overlaps this cell.
                    // A hole overlaps if its center lands inside, or any hole vertex is within the cell bounds.
                    val holeOverlaps = holes.isNotEmpty() && holes.any { hole ->
                        Polygons.contains(hole, lon = centerLon, lat = centerLat) ||
                            hasVertexInCell(hole, lon, lat, size)
                    }
                    if (holeOverlaps) {
                        // Subdivide so the hole boundary is respected at finer resolution
                        addCell(lat, lon, half)
                        addCell(lat, lon + half, half)
                        addCell(lat + half, lon, half)
                        addCell(lat + half, lon + half, half)
                    } else {
                        emitCell(lat, lon, size)
                    }
                } else {
                    // Edge crosses concavity — subdivide
                    addCell(lat, lon, half)
                    addCell(lat, lon + half, half)
                    addCell(lat + half, lon, half)
                    addCell(lat + half, lon + half, half)
                }
            } else if (!c00 && !c10 && !c01 && !c11 && !centerIn) {
                // All test points outside — but a narrow feature (peninsula, isthmus)
                // might still pass through. Subdivide if any polygon vertex is in the cell.
                if (!hasVertexInCell(coords, lon, lat, size)) return
                val half = size / 2
                addCell(lat, lon, half)
                addCell(lat, lon + half, half)
                addCell(lat + half, lon, half)
                addCell(lat + half, lon + half, half)
            } else {
                // Near border — subdivide
                val half = size / 2
                addCell(lat, lon, half)
                addCell(lat, lon + half, half)
                addCell(lat + half, lon, half)
                addCell(lat + half, lon + half, half)
            }
        }

        var lat = minLat
        while (lat < maxLat) {
            var lon = minLon
            while (lon < maxLon) {
                addCell(lat, lon, startSize)
                lon += startSize
            }
            lat += startSize
        }

        // Fallback for tiny polygons where no grid cell center falls inside
        if (cellCount == 0) {
            val baseIndex = allPositions.size / 3
            var centroidLon = 0.0
            var centroidLat = 0.0
            for (i in 0 until coords.size) {
                centroidLon += coords.lon(i)
                centroidLat += coords.lat(i)
            }
            centroidLon /= coords.size
            centroidLat /= coords.size
            val center = latLonToSphere(lat = centroidLat, lon = centroidLon, radius = radius)
            allPositions.add(center.x, center.y, center.z)
            for (i in 0 until coords.size) {
                val v = latLonToSphere(lat = coords.lat(i), lon = coords.lon(i), radius = radius)
                allPositions.add(v.x, v.y, v.z)
            }
            for (i in 0 until coords.size) {
                val next = (i + 1) % coords.size
                allIndices.add(baseIndex)
                allIndices.add(baseIndex + i + 1)
                allIndices.add(baseIndex + next + 1)
            }
        }
    }

    // Border outline

    /**
     * Inserts interpolated points into segments longer than [MAX_EDGE_DEGREES]
     * so the outline follows the sphere surface (a long straight chord would
     * dip below it and disappear). Returns flat `[lon, lat]` pairs.
     */
    private fun densifyRing(coords: Ring): DoubleArray {
        val result = DoubleArrayBuilder(coords.lonLat.size)
        for (i in 0 until coords.size) {
            val next = (i + 1) % coords.size
            val curLon = coords.lon(i)
            val curLat = coords.lat(i)
            val nextLon = coords.lon(next)
            val nextLat = coords.lat(next)
            result.add(curLon)
            result.add(curLat)
            val length = angularLength(curLon, curLat, nextLon, nextLat)
            if (length > MAX_EDGE_DEGREES) {
                val segments = ceil(length / MAX_EDGE_DEGREES).toInt()
                for (s in 1 until segments) {
                    val t = s.toDouble() / segments
                    result.add(curLon + (nextLon - curLon) * t)
                    result.add(curLat + (nextLat - curLat) * t)
                }
            }
        }
        return result.toArray()
    }

    /**
     * Buckets outline rings into longitude sectors and builds one outline mesh
     * per sector, exactly as iOS builds one `outline_sector_N` node per sector:
     * per-frame horizon culling can then skip sectors on the globe's far side —
     * the outline mesh dominates the scene's vertex count. Rings are assigned
     * whole (by centroid longitude), so wide rings simply make their sector's
     * bounding volume larger and it culls less often.
     *
     * Whether Filament needs that culling at all is measured in Phase 7.5; the
     * sectored form keeps the option open without rebuilding geometry.
     */
    fun createSectoredOutlineGeometries(polygons: List<Ring>, sectors: Int = 12): List<OutlineMesh> {
        val buckets = List(sectors) { mutableListOf<Ring>() }
        for (polygon in polygons) {
            val cleaned = cleanRing(polygon) ?: continue
            var centroidLon = 0.0
            for (i in 0 until cleaned.size) {
                centroidLon += cleaned.lon(i)
            }
            centroidLon /= cleaned.size
            val index = ((centroidLon + 180.0) / 360.0 * sectors).toInt().coerceIn(0, sectors - 1)
            buckets[index].add(polygon)
        }
        return buckets.mapNotNull { if (it.isEmpty()) null else createBorderOutlineGeometry(it) }
    }

    /**
     * Create border outline as a continuous quad strip with shared vertices at
     * joins. Width is applied by the outline material at render time (see
     * [OutlineMesh]); without it this geometry is degenerate (zero width).
     */
    fun createBorderOutlineGeometry(polygons: List<Ring>, radius: Float = 1.005f): OutlineMesh? {
        val allPositions = FloatArrayBuilder()
        val allMiters = FloatArrayBuilder()
        val allIndices = IntArrayBuilder()

        for (polygon in polygons) {
            val cleaned = cleanRing(polygon) ?: continue
            val coords = densifyRing(cleaned)

            val n = coords.size / 2

            // Convert all coordinates to 3D positions on sphere
            val positions = FloatArray(n * 3)
            for (i in 0 until n) {
                val p = latLonToSphere(lat = coords[i * 2 + 1], lon = coords[i * 2], radius = radius)
                positions[i * 3] = p.x
                positions[i * 3 + 1] = p.y
                positions[i * 3 + 2] = p.z
            }

            // Build inner/outer vertex pairs; the miter offset direction is stored per
            // vertex and applied by the outline material
            val baseIndex = allPositions.size / 3
            for (i in 0 until n) {
                val px = positions[i * 3]
                val py = positions[i * 3 + 1]
                val pz = positions[i * 3 + 2]
                val prev = (i - 1 + n) % n
                val next = (i + 1) % n

                val normalX = px / radius
                val normalY = py / radius
                val normalZ = pz / radius

                // Perpendicular to each adjacent edge, projected onto sphere surface
                val e1x = px - positions[prev * 3]
                val e1y = py - positions[prev * 3 + 1]
                val e1z = pz - positions[prev * 3 + 2]
                var perp1X = e1y * normalZ - e1z * normalY
                var perp1Y = e1z * normalX - e1x * normalZ
                var perp1Z = e1x * normalY - e1y * normalX
                val len1 = sqrt(perp1X * perp1X + perp1Y * perp1Y + perp1Z * perp1Z)
                perp1X /= len1; perp1Y /= len1; perp1Z /= len1

                val e2x = positions[next * 3] - px
                val e2y = positions[next * 3 + 1] - py
                val e2z = positions[next * 3 + 2] - pz
                var perp2X = e2y * normalZ - e2z * normalY
                var perp2Y = e2z * normalX - e2x * normalZ
                var perp2Z = e2x * normalY - e2y * normalX
                val len2 = sqrt(perp2X * perp2X + perp2Y * perp2Y + perp2Z * perp2Z)
                perp2X /= len2; perp2Y /= len2; perp2Z /= len2

                // Miter direction: average of the two perpendiculars, lengthened on sharp
                // corners (capped at 2x) so the strip keeps its visual width
                var miterX = perp1X + perp2X
                var miterY = perp1Y + perp2Y
                var miterZ = perp1Z + perp2Z
                val miterLen = sqrt(miterX * miterX + miterY * miterY + miterZ * miterZ)
                miterX /= miterLen; miterY /= miterLen; miterZ /= miterLen
                val dot = miterX * perp1X + miterY * perp1Y + miterZ * perp1Z
                val scale = if (dot > 0.3f) minOf(1.0f / dot, 2.0f) else 1.0f
                miterX *= scale; miterY *= scale; miterZ *= scale

                allPositions.add(px, py, pz)
                allMiters.add(-miterX, -miterY, -miterZ)
                allPositions.add(px, py, pz)
                allMiters.add(miterX, miterY, miterZ)
            }

            // Connect as continuous quad strip wrapping around
            for (i in 0 until n) {
                val next = (i + 1) % n
                val i0 = baseIndex + i * 2
                val i1 = i0 + 1
                val i2 = baseIndex + next * 2
                val i3 = i2 + 1

                allIndices.add(i0); allIndices.add(i1); allIndices.add(i3)
                allIndices.add(i0); allIndices.add(i3); allIndices.add(i2)
            }
        }

        if (allPositions.size == 0 || allIndices.size == 0) return null

        return OutlineMesh(
            positions = allPositions.toArray(),
            miters = allMiters.toArray(),
            indices = allIndices.toArray(),
        )
    }

}
