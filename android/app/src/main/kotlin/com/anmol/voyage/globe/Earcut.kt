package com.anmol.voyage.globe

/**
 * Kotlin port of mapbox/earcut (v2.2.4) — ear-clipping polygon triangulation
 * with hole support. https://github.com/mapbox/earcut
 *
 * Ported 1:1 from the iOS `Earcut.swift` (itself a port of the same version)
 * so the two stay diffable — same function names, same order, same control
 * flow. The one deliberate difference: Swift tracks every node to break the
 * circular list's strong reference cycles after triangulation; the JVM's
 * garbage collector handles cycles, so that bookkeeping has no Kotlin
 * counterpart.
 *
 * Used to triangulate country polygons in lon/lat space before projecting
 * them onto the globe.
 */
object Earcut {

    /**
     * Triangulates a polygon with optional holes.
     *
     * @param data Flat coordinates `[x0, y0, x1, y1, …]` — outer ring first,
     *   then hole rings.
     * @param holeIndices Start index (in vertices, not doubles) of each hole ring.
     * @return Flat list of vertex indices forming triangles (3 per triangle).
     */
    fun triangulate(data: DoubleArray, holeIndices: IntArray = IntArray(0)): IntArray {
        val hasHoles = holeIndices.isNotEmpty()
        val outerLen = if (hasHoles) holeIndices[0] * 2 else data.size
        var outerNode = linkedList(data, 0, outerLen, clockwise = true)
        val triangles = IndexList()

        val outer = outerNode ?: return triangles.toArray()
        if (outer.next === outer.prev) return triangles.toArray()
        var minX = 0.0
        var minY = 0.0
        var invSize = 0.0

        if (hasHoles) {
            outerNode = eliminateHoles(data, holeIndices, outerNode)
        }

        // If the shape is not too simple, use z-order curve hashing for lookups
        if (data.size > 80 * 2) {
            minX = data[0]; minY = data[1]
            var maxX = minX
            var maxY = minY
            var i = 2
            while (i < outerLen) {
                val x = data[i]
                val y = data[i + 1]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                i += 2
            }
            invSize = maxOf(maxX - minX, maxY - minY)
            invSize = if (invSize != 0.0) 32767 / invSize else 0.0
        }

        earcutLinked(outerNode, triangles, minX, minY, invSize, 0)
        return triangles.toArray()
    }

    /** Growable index list, so the hot path never boxes an `Int`. */
    private class IndexList {
        private var storage = IntArray(1024)
        private var count = 0

        fun add(value: Int) {
            if (count == storage.size) storage = storage.copyOf(storage.size * 2)
            storage[count++] = value
        }

        fun toArray(): IntArray = storage.copyOf(count)
    }

    // Linked list node

    private class Node(
        @JvmField val i: Int,       // vertex index in the input coordinate array
        @JvmField val x: Double,
        @JvmField val y: Double,
    ) {
        @JvmField var prev: Node? = null
        @JvmField var next: Node? = null
        @JvmField var z: Int = 0    // z-order curve value
        @JvmField var prevZ: Node? = null
        @JvmField var nextZ: Node? = null
        @JvmField var steiner = false
    }

    // Create a circular doubly linked list from polygon points in the specified winding order
    private fun linkedList(data: DoubleArray, start: Int, end: Int, clockwise: Boolean): Node? {
        var last: Node? = null

        if (clockwise == (signedArea(data, start, end) > 0)) {
            var i = start
            while (i < end) {
                last = insertNode(i, data[i], data[i + 1], last)
                i += 2
            }
        } else {
            var i = end - 2
            while (i >= start) {
                last = insertNode(i, data[i], data[i + 1], last)
                i -= 2
            }
        }

        val l = last
        if (l != null && equals(l, l.next!!)) {
            removeNode(l)
            last = l.next
        }
        return last
    }

    // Eliminate colinear or duplicate points
    private fun filterPoints(start: Node?, end: Node? = null): Node? {
        var p = start ?: return null
        var stop = end ?: p

        var again: Boolean
        do {
            again = false
            if (!p.steiner && (equals(p, p.next!!) || area(p.prev!!, p, p.next!!) == 0.0)) {
                removeNode(p)
                p = p.prev!!
                stop = p.prev!!
                if (p === p.next) break
                again = true
            } else {
                p = p.next!!
            }
        } while (again || p !== stop)

        return stop
    }

    // Main ear-slicing loop which triangulates a polygon (given as a linked list)
    private fun earcutLinked(start: Node?, triangles: IndexList, minX: Double, minY: Double, invSize: Double, pass: Int) {
        var ear = start ?: return

        // Interlink polygon nodes in z-order
        if (pass == 0 && invSize != 0.0) {
            indexCurve(ear, minX, minY, invSize)
        }

        var stop = ear

        while (ear.prev !== ear.next) {
            val prev = ear.prev!!
            val next = ear.next!!

            if (if (invSize != 0.0) isEarHashed(ear, minX, minY, invSize) else isEar(ear)) {
                // Cut off the triangle
                triangles.add(prev.i / 2)
                triangles.add(ear.i / 2)
                triangles.add(next.i / 2)

                removeNode(ear)

                ear = next.next!!
                stop = next.next!!
                continue
            }

            ear = next

            // If we looped through the whole remaining polygon and can't find any more ears
            if (ear === stop) {
                if (pass == 0) {
                    earcutLinked(filterPoints(ear), triangles, minX, minY, invSize, 1)
                } else if (pass == 1) {
                    // Try curing all small self-intersections locally
                    val cured = cureLocalIntersections(filterPoints(ear)!!, triangles)
                    earcutLinked(cured, triangles, minX, minY, invSize, 2)
                } else if (pass == 2) {
                    // As a last resort, try splitting the remaining polygon into two
                    splitEarcut(ear, triangles, minX, minY, invSize)
                }
                break
            }
        }
    }

    // Check whether a polygon node forms a valid ear with adjacent nodes
    private fun isEar(ear: Node): Boolean {
        val a = ear.prev!!
        val b = ear
        val c = ear.next!!

        if (area(a, b, c) >= 0) return false // reflex, can't be an ear

        // Now make sure we don't have other points inside the potential ear
        val ax = a.x; val bx = b.x; val cx = c.x
        val ay = a.y; val by = b.y; val cy = c.y

        // Triangle bbox; min & max are calculated like this for speed
        val x0 = if (ax < bx) (if (ax < cx) ax else cx) else (if (bx < cx) bx else cx)
        val y0 = if (ay < by) (if (ay < cy) ay else cy) else (if (by < cy) by else cy)
        val x1 = if (ax > bx) (if (ax > cx) ax else cx) else (if (bx > cx) bx else cx)
        val y1 = if (ay > by) (if (ay > cy) ay else cy) else (if (by > cy) by else cy)

        var p = c.next!!
        while (p !== a) {
            if (p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 &&
                pointInTriangle(ax, ay, bx, by, cx, cy, p.x, p.y) &&
                area(p.prev!!, p, p.next!!) >= 0
            ) {
                return false
            }
            p = p.next!!
        }
        return true
    }

    private fun isEarHashed(ear: Node, minX: Double, minY: Double, invSize: Double): Boolean {
        val a = ear.prev!!
        val b = ear
        val c = ear.next!!

        if (area(a, b, c) >= 0) return false

        val ax = a.x; val bx = b.x; val cx = c.x
        val ay = a.y; val by = b.y; val cy = c.y

        val x0 = if (ax < bx) (if (ax < cx) ax else cx) else (if (bx < cx) bx else cx)
        val y0 = if (ay < by) (if (ay < cy) ay else cy) else (if (by < cy) by else cy)
        val x1 = if (ax > bx) (if (ax > cx) ax else cx) else (if (bx > cx) bx else cx)
        val y1 = if (ay > by) (if (ay > cy) ay else cy) else (if (by > cy) by else cy)

        // Z-order range for the current triangle bbox
        val minZ = zOrder(x0, y0, minX, minY, invSize)
        val maxZ = zOrder(x1, y1, minX, minY, invSize)

        var p = ear.prevZ
        var n = ear.nextZ

        // Look for points inside the triangle in both directions
        while (p != null && p.z >= minZ && n != null && n.z <= maxZ) {
            if (p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 && p !== a && p !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, p.x, p.y) && area(p.prev!!, p, p.next!!) >= 0
            ) {
                return false
            }
            p = p.prevZ

            if (n.x >= x0 && n.x <= x1 && n.y >= y0 && n.y <= y1 && n !== a && n !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, n.x, n.y) && area(n.prev!!, n, n.next!!) >= 0
            ) {
                return false
            }
            n = n.nextZ
        }

        // Look for remaining points in decreasing z-order
        while (p != null && p.z >= minZ) {
            if (p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 && p !== a && p !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, p.x, p.y) && area(p.prev!!, p, p.next!!) >= 0
            ) {
                return false
            }
            p = p.prevZ
        }

        // Look for remaining points in increasing z-order
        while (n != null && n.z <= maxZ) {
            if (n.x >= x0 && n.x <= x1 && n.y >= y0 && n.y <= y1 && n !== a && n !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, n.x, n.y) && area(n.prev!!, n, n.next!!) >= 0
            ) {
                return false
            }
            n = n.nextZ
        }

        return true
    }

    // Go through all polygon nodes and cure small local self-intersections
    private fun cureLocalIntersections(start: Node, triangles: IndexList): Node? {
        var stop = start
        var p = start
        do {
            val a = p.prev!!
            val b = p.next!!.next!!

            if (!equals(a, b) && intersects(a, p, p.next!!, b) && locallyInside(a, b) && locallyInside(b, a)) {
                triangles.add(a.i / 2)
                triangles.add(p.i / 2)
                triangles.add(b.i / 2)

                // Remove two nodes involved
                removeNode(p)
                removeNode(p.next!!)

                p = b
                stop = b
            }
            p = p.next!!
        } while (p !== stop)

        return filterPoints(p)
    }

    // Try splitting polygon into two and triangulate them independently
    private fun splitEarcut(start: Node, triangles: IndexList, minX: Double, minY: Double, invSize: Double) {
        // Look for a valid diagonal that divides the polygon into two
        var a = start
        do {
            var b = a.next!!.next!!
            while (b !== a.prev) {
                if (a.i != b.i && isValidDiagonal(a, b)) {
                    // Split the polygon in two by the diagonal
                    var c = splitPolygon(a, b)

                    // Filter colinear points around the cuts
                    val aFiltered = filterPoints(a, a.next)
                    c = filterPoints(c, c.next)!!

                    // Run earcut on each half
                    earcutLinked(aFiltered, triangles, minX, minY, invSize, 0)
                    earcutLinked(c, triangles, minX, minY, invSize, 0)
                    return
                }
                b = b.next!!
            }
            a = a.next!!
        } while (a !== start)
    }

    // Link every hole into the outer loop, producing a single-ring polygon without holes
    private fun eliminateHoles(data: DoubleArray, holeIndices: IntArray, outerNode: Node?): Node? {
        var outer = outerNode
        val queue = ArrayList<Node>()

        for (i in holeIndices.indices) {
            val start = holeIndices[i] * 2
            val end = if (i < holeIndices.size - 1) holeIndices[i + 1] * 2 else data.size
            val list = linkedList(data, start, end, clockwise = false)
            if (list != null) {
                if (list === list.next) list.steiner = true
                queue.add(getLeftmost(list))
            }
        }

        queue.sortBy { it.x }

        // Process holes from left to right
        for (hole in queue) {
            outer = eliminateHole(hole, outer)
        }

        return outer
    }

    // Find a bridge between vertices that connects hole with an outer ring and link it
    private fun eliminateHole(hole: Node, outerNode: Node?): Node? {
        if (outerNode == null) return null
        val bridge = findHoleBridge(hole, outerNode) ?: return outerNode

        val bridgeReverse = splitPolygon(bridge, hole)

        // Filter collinear points around the cuts
        filterPoints(bridgeReverse, bridgeReverse.next)
        return filterPoints(bridge, bridge.next)
    }

    // David Eberly's algorithm for finding a bridge between hole and outer polygon
    private fun findHoleBridge(hole: Node, outerNode: Node): Node? {
        var p = outerNode
        val hx = hole.x
        val hy = hole.y
        var qx = Double.NEGATIVE_INFINITY
        var m: Node? = null

        // Find a segment intersected by a ray from the hole's leftmost point to the left;
        // segment's endpoint with lesser x will be a potential connection point
        do {
            val next = p.next!!
            if (hy <= p.y && hy >= next.y && next.y != p.y) {
                val x = p.x + (hy - p.y) * (next.x - p.x) / (next.y - p.y)
                if (x <= hx && x > qx) {
                    qx = x
                    if (x == hx) {
                        if (hy == p.y) return p
                        if (hy == next.y) return next
                    }
                    m = if (p.x < next.x) p else next
                }
            }
            p = next
        } while (p !== outerNode)

        var mNode = m ?: return null

        if (hx == qx) return mNode // hole touches outer segment; pick leftmost endpoint

        // Look for points inside the triangle of hole point, segment intersection and endpoint;
        // if there are no points found, we have a valid connection;
        // otherwise choose the point of the minimum angle with the ray as connection point
        val stop = mNode
        val mx = mNode.x
        val my = mNode.y
        var tanMin = Double.POSITIVE_INFINITY

        p = mNode

        do {
            if (hx >= p.x && p.x >= mx && hx != p.x &&
                pointInTriangle(if (hy < my) hx else qx, hy, mx, my, if (hy < my) qx else hx, hy, p.x, p.y)
            ) {
                val tan = kotlin.math.abs(hy - p.y) / (hx - p.x)

                if (locallyInside(p, hole) &&
                    (tan < tanMin || (tan == tanMin && (p.x > mNode.x || (p.x == mNode.x && sectorContainsSector(mNode, p)))))
                ) {
                    mNode = p
                    tanMin = tan
                }
            }
            p = p.next!!
        } while (p !== stop)

        return mNode
    }

    // Whether sector in vertex m contains sector in vertex p in the same coordinates
    private fun sectorContainsSector(m: Node, p: Node): Boolean {
        return area(m.prev!!, m, p.prev!!) < 0 && area(p.next!!, m, m.next!!) < 0
    }

    // Interlink polygon nodes in z-order
    private fun indexCurve(start: Node, minX: Double, minY: Double, invSize: Double) {
        var p = start
        do {
            if (p.z == 0) p.z = zOrder(p.x, p.y, minX, minY, invSize)
            p.prevZ = p.prev
            p.nextZ = p.next
            p = p.next!!
        } while (p !== start)

        p.prevZ?.nextZ = null
        p.prevZ = null

        sortLinked(p)
    }

    // Simon Tatham's linked list merge sort algorithm
    private fun sortLinked(start: Node): Node {
        var list: Node? = start
        var inSize = 1
        var numMerges: Int

        do {
            var p = list
            list = null
            var tail: Node? = null
            numMerges = 0

            while (p != null) {
                numMerges += 1
                var q: Node? = p
                var pSize = 0
                for (unused in 0 until inSize) {
                    pSize += 1
                    q = q?.nextZ
                    if (q == null) break
                }
                var qSize = inSize

                while (pSize > 0 || (qSize > 0 && q != null)) {
                    val e: Node
                    if (pSize != 0 && (qSize == 0 || q == null || p!!.z <= q.z)) {
                        e = p!!
                        p = p.nextZ
                        pSize -= 1
                    } else {
                        e = q!!
                        q = q.nextZ
                        qSize -= 1
                    }

                    if (tail != null) tail.nextZ = e
                    else list = e

                    e.prevZ = tail
                    tail = e
                }

                p = q
            }

            tail?.nextZ = null
            inSize *= 2
        } while (numMerges > 1)

        return list!!
    }

    // Z-order of a point given coords and inverse of the longer side of data bbox
    private fun zOrder(xd: Double, yd: Double, minX: Double, minY: Double, invSize: Double): Int {
        // Coords are transformed into non-negative 15-bit integer range
        var x = ((xd - minX) * invSize).toInt()
        var y = ((yd - minY) * invSize).toInt()

        x = (x or (x shl 8)) and 0x00FF00FF
        x = (x or (x shl 4)) and 0x0F0F0F0F
        x = (x or (x shl 2)) and 0x33333333
        x = (x or (x shl 1)) and 0x55555555

        y = (y or (y shl 8)) and 0x00FF00FF
        y = (y or (y shl 4)) and 0x0F0F0F0F
        y = (y or (y shl 2)) and 0x33333333
        y = (y or (y shl 1)) and 0x55555555

        return x or (y shl 1)
    }

    // Find the leftmost node of a polygon ring
    private fun getLeftmost(start: Node): Node {
        var p = start
        var leftmost = start
        do {
            if (p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y)) {
                leftmost = p
            }
            p = p.next!!
        } while (p !== start)
        return leftmost
    }

    private fun pointInTriangle(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, px: Double, py: Double): Boolean {
        return (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
            (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
            (bx - px) * (cy - py) >= (cx - px) * (by - py)
    }

    // Check if a diagonal between two polygon nodes is valid (lies in polygon interior)
    private fun isValidDiagonal(a: Node, b: Node): Boolean {
        return a.next!!.i != b.i && a.prev!!.i != b.i && !intersectsPolygon(a, b) && // doesn't intersect other edges
            (locallyInside(a, b) && locallyInside(b, a) && middleInside(a, b) && // locally visible
                (area(a.prev!!, a, b.prev!!) != 0.0 || area(a, b.prev!!, b) != 0.0) || // does not create opposite-facing sectors
                equals(a, b) && area(a.prev!!, a, a.next!!) > 0 && area(b.prev!!, b, b.next!!) > 0) // special zero-length case
    }

    // Signed area of a triangle
    private fun area(p: Node, q: Node, r: Node): Double {
        return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
    }

    // Check if two points are equal
    private fun equals(p1: Node, p2: Node): Boolean {
        return p1.x == p2.x && p1.y == p2.y
    }

    // Check if two segments intersect
    private fun intersects(p1: Node, q1: Node, p2: Node, q2: Node): Boolean {
        val o1 = sign(area(p1, q1, p2))
        val o2 = sign(area(p1, q1, q2))
        val o3 = sign(area(p2, q2, p1))
        val o4 = sign(area(p2, q2, q1))

        if (o1 != o2 && o3 != o4) return true // general case

        if (o1 == 0 && onSegment(p1, p2, q1)) return true // p1, q1 and p2 are collinear and p2 lies on p1q1
        if (o2 == 0 && onSegment(p1, q2, q1)) return true // p1, q1 and q2 are collinear and q2 lies on p1q1
        if (o3 == 0 && onSegment(p2, p1, q2)) return true // p2, q2 and p1 are collinear and p1 lies on p2q2
        if (o4 == 0 && onSegment(p2, q1, q2)) return true // p2, q2 and q1 are collinear and q1 lies on p2q2

        return false
    }

    // For collinear points p, q, r: check if point q lies on segment pr
    private fun onSegment(p: Node, q: Node, r: Node): Boolean {
        return q.x <= maxOf(p.x, r.x) && q.x >= minOf(p.x, r.x) && q.y <= maxOf(p.y, r.y) && q.y >= minOf(p.y, r.y)
    }

    private fun sign(num: Double): Int {
        return if (num > 0) 1 else if (num < 0) -1 else 0
    }

    // Check if a polygon diagonal intersects any polygon segments
    private fun intersectsPolygon(a: Node, b: Node): Boolean {
        var p = a
        do {
            if (p.i != a.i && p.next!!.i != a.i && p.i != b.i && p.next!!.i != b.i &&
                intersects(p, p.next!!, a, b)
            ) {
                return true
            }
            p = p.next!!
        } while (p !== a)
        return false
    }

    // Check if a polygon diagonal is locally inside the polygon
    private fun locallyInside(a: Node, b: Node): Boolean {
        return if (area(a.prev!!, a, a.next!!) < 0) {
            area(a, b, a.next!!) >= 0 && area(a, a.prev!!, b) >= 0
        } else {
            area(a, b, a.prev!!) < 0 || area(a, a.next!!, b) < 0
        }
    }

    // Check if the middle point of a polygon diagonal is inside the polygon
    private fun middleInside(a: Node, b: Node): Boolean {
        var p = a
        var inside = false
        val px = (a.x + b.x) / 2
        val py = (a.y + b.y) / 2
        do {
            val next = p.next!!
            if (((p.y > py) != (next.y > py)) && next.y != p.y &&
                (px < (next.x - p.x) * (py - p.y) / (next.y - p.y) + p.x)
            ) {
                inside = !inside
            }
            p = next
        } while (p !== a)
        return inside
    }

    // Link two polygon vertices with a bridge; if the vertices belong to the same ring, it splits
    // polygon into two; if one belongs to the outer ring and another to a hole, it merges it into a
    // single ring
    private fun splitPolygon(a: Node, b: Node): Node {
        val a2 = Node(a.i, a.x, a.y)
        val b2 = Node(b.i, b.x, b.y)
        val an = a.next!!
        val bp = b.prev!!

        a.next = b
        b.prev = a

        a2.next = an
        an.prev = a2

        b2.next = a2
        a2.prev = b2

        bp.next = b2
        b2.prev = bp

        return b2
    }

    // Create a node and optionally link it with previous one (in a circular doubly linked list)
    private fun insertNode(i: Int, x: Double, y: Double, last: Node?): Node {
        val p = Node(i, x, y)

        if (last != null) {
            p.next = last.next
            p.prev = last
            last.next!!.prev = p
            last.next = p
        } else {
            p.prev = p
            p.next = p
        }
        return p
    }

    private fun removeNode(p: Node) {
        p.next!!.prev = p.prev
        p.prev!!.next = p.next

        p.prevZ?.nextZ = p.nextZ
        p.nextZ?.prevZ = p.prevZ
    }

    private fun signedArea(data: DoubleArray, start: Int, end: Int): Double {
        var sum = 0.0
        var i = start
        var j = end - 2
        while (i < end) {
            sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
            j = i
            i += 2
        }
        return sum
    }
}
