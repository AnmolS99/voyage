import Foundation

/// Swift port of mapbox/earcut (v2.2.4) — ear-clipping polygon triangulation with hole support.
/// https://github.com/mapbox/earcut
///
/// Used to triangulate country polygons in lon/lat space before projecting onto the globe.
final class Earcut {

    /// Triangulates a polygon with optional holes.
    /// - Parameters:
    ///   - data: Flat coordinates [x0, y0, x1, y1, ...] — outer ring first, then hole rings.
    ///   - holeIndices: Start index (in vertices, not doubles) of each hole ring.
    /// - Returns: Flat list of vertex indices forming triangles (3 per triangle).
    static func triangulate(data: [Double], holeIndices: [Int]) -> [Int] {
        let instance = Earcut()
        defer { instance.releaseNodes() }
        return instance.run(data: data, holeIndices: holeIndices)
    }

    // The circular doubly-linked node list holds strong reference cycles by design;
    // every created node is tracked so the cycles can be broken after triangulation.
    private var allNodes: [Node] = []

    private func releaseNodes() {
        for node in allNodes {
            node.prev = nil
            node.next = nil
            node.prevZ = nil
            node.nextZ = nil
        }
        allNodes.removeAll()
    }

    private func run(data: [Double], holeIndices: [Int]) -> [Int] {
        let hasHoles = !holeIndices.isEmpty
        let outerLen = hasHoles ? holeIndices[0] * 2 : data.count
        var outerNode = linkedList(data, 0, outerLen, clockwise: true)
        var triangles: [Int] = []

        guard let outer = outerNode, outer.next !== outer.prev else { return triangles }
        var minX = 0.0, minY = 0.0, invSize = 0.0

        if hasHoles {
            outerNode = eliminateHoles(data, holeIndices, outerNode)
        }

        // If the shape is not too simple, use z-order curve hashing for lookups
        if data.count > 80 * 2 {
            minX = data[0]; minY = data[1]
            var maxX = minX, maxY = minY
            var i = 2
            while i < outerLen {
                let x = data[i], y = data[i + 1]
                if x < minX { minX = x }
                if y < minY { minY = y }
                if x > maxX { maxX = x }
                if y > maxY { maxY = y }
                i += 2
            }
            invSize = max(maxX - minX, maxY - minY)
            invSize = invSize != 0 ? 32767 / invSize : 0
        }

        earcutLinked(outerNode, &triangles, minX, minY, invSize, 0)
        return triangles
    }

    // MARK: - Linked list node

    private final class Node {
        let i: Int          // vertex index in the input coordinate array
        let x: Double
        let y: Double
        var prev: Node!
        var next: Node!
        var z: Int32 = 0    // z-order curve value
        var prevZ: Node?
        var nextZ: Node?
        var steiner = false

        init(_ i: Int, _ x: Double, _ y: Double) {
            self.i = i
            self.x = x
            self.y = y
        }
    }

    // Create a circular doubly linked list from polygon points in the specified winding order
    private func linkedList(_ data: [Double], _ start: Int, _ end: Int, clockwise: Bool) -> Node? {
        var last: Node?

        if clockwise == (signedArea(data, start, end) > 0) {
            var i = start
            while i < end {
                last = insertNode(i, data[i], data[i + 1], last)
                i += 2
            }
        } else {
            var i = end - 2
            while i >= start {
                last = insertNode(i, data[i], data[i + 1], last)
                i -= 2
            }
        }

        if let l = last, equals(l, l.next) {
            removeNode(l)
            last = l.next
        }
        return last
    }

    // Eliminate colinear or duplicate points
    private func filterPoints(_ start: Node?, _ end: Node? = nil) -> Node? {
        guard var p = start else { return nil }
        var end = end ?? p

        var again = false
        repeat {
            again = false
            if !p.steiner && (equals(p, p.next) || area(p.prev, p, p.next) == 0) {
                removeNode(p)
                p = p.prev
                end = p.prev
                if p === p.next { break }
                again = true
            } else {
                p = p.next
            }
        } while again || p !== end

        return end
    }

    // Main ear-slicing loop which triangulates a polygon (given as a linked list)
    private func earcutLinked(_ ear: Node?, _ triangles: inout [Int], _ minX: Double, _ minY: Double, _ invSize: Double, _ pass: Int) {
        guard var ear = ear else { return }

        // Interlink polygon nodes in z-order
        if pass == 0 && invSize != 0 {
            indexCurve(ear, minX, minY, invSize)
        }

        var stop = ear

        while ear.prev !== ear.next {
            let prev = ear.prev!
            let next = ear.next!

            if invSize != 0 ? isEarHashed(ear, minX, minY, invSize) : isEar(ear) {
                // Cut off the triangle
                triangles.append(prev.i / 2)
                triangles.append(ear.i / 2)
                triangles.append(next.i / 2)

                removeNode(ear)

                ear = next.next
                stop = next.next
                continue
            }

            ear = next

            // If we looped through the whole remaining polygon and can't find any more ears
            if ear === stop {
                if pass == 0 {
                    earcutLinked(filterPoints(ear), &triangles, minX, minY, invSize, 1)
                } else if pass == 1 {
                    // Try curing all small self-intersections locally
                    let cured = cureLocalIntersections(filterPoints(ear)!, &triangles)
                    earcutLinked(cured, &triangles, minX, minY, invSize, 2)
                } else if pass == 2 {
                    // As a last resort, try splitting the remaining polygon into two
                    splitEarcut(ear, &triangles, minX, minY, invSize)
                }
                break
            }
        }
    }

    // Check whether a polygon node forms a valid ear with adjacent nodes
    private func isEar(_ ear: Node) -> Bool {
        let a = ear.prev!, b = ear, c = ear.next!

        if area(a, b, c) >= 0 { return false } // reflex, can't be an ear

        // Now make sure we don't have other points inside the potential ear
        let ax = a.x, bx = b.x, cx = c.x, ay = a.y, by = b.y, cy = c.y

        // Triangle bbox; min & max are calculated like this for speed
        let x0 = ax < bx ? (ax < cx ? ax : cx) : (bx < cx ? bx : cx)
        let y0 = ay < by ? (ay < cy ? ay : cy) : (by < cy ? by : cy)
        let x1 = ax > bx ? (ax > cx ? ax : cx) : (bx > cx ? bx : cx)
        let y1 = ay > by ? (ay > cy ? ay : cy) : (by > cy ? by : cy)

        var p = c.next!
        while p !== a {
            if p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 &&
                pointInTriangle(ax, ay, bx, by, cx, cy, p.x, p.y) &&
                area(p.prev, p, p.next) >= 0 {
                return false
            }
            p = p.next
        }
        return true
    }

    private func isEarHashed(_ ear: Node, _ minX: Double, _ minY: Double, _ invSize: Double) -> Bool {
        let a = ear.prev!, b = ear, c = ear.next!

        if area(a, b, c) >= 0 { return false }

        let ax = a.x, bx = b.x, cx = c.x, ay = a.y, by = b.y, cy = c.y

        let x0 = ax < bx ? (ax < cx ? ax : cx) : (bx < cx ? bx : cx)
        let y0 = ay < by ? (ay < cy ? ay : cy) : (by < cy ? by : cy)
        let x1 = ax > bx ? (ax > cx ? ax : cx) : (bx > cx ? bx : cx)
        let y1 = ay > by ? (ay > cy ? ay : cy) : (by > cy ? by : cy)

        // Z-order range for the current triangle bbox
        let minZ = zOrder(x0, y0, minX, minY, invSize)
        let maxZ = zOrder(x1, y1, minX, minY, invSize)

        var p = ear.prevZ
        var n = ear.nextZ

        // Look for points inside the triangle in both directions
        while let pp = p, pp.z >= minZ, let nn = n, nn.z <= maxZ {
            if pp.x >= x0 && pp.x <= x1 && pp.y >= y0 && pp.y <= y1 && pp !== a && pp !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, pp.x, pp.y) && area(pp.prev, pp, pp.next) >= 0 {
                return false
            }
            p = pp.prevZ

            if nn.x >= x0 && nn.x <= x1 && nn.y >= y0 && nn.y <= y1 && nn !== a && nn !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, nn.x, nn.y) && area(nn.prev, nn, nn.next) >= 0 {
                return false
            }
            n = nn.nextZ
        }

        // Look for remaining points in decreasing z-order
        while let pp = p, pp.z >= minZ {
            if pp.x >= x0 && pp.x <= x1 && pp.y >= y0 && pp.y <= y1 && pp !== a && pp !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, pp.x, pp.y) && area(pp.prev, pp, pp.next) >= 0 {
                return false
            }
            p = pp.prevZ
        }

        // Look for remaining points in increasing z-order
        while let nn = n, nn.z <= maxZ {
            if nn.x >= x0 && nn.x <= x1 && nn.y >= y0 && nn.y <= y1 && nn !== a && nn !== c &&
                pointInTriangle(ax, ay, bx, by, cx, cy, nn.x, nn.y) && area(nn.prev, nn, nn.next) >= 0 {
                return false
            }
            n = nn.nextZ
        }

        return true
    }

    // Go through all polygon nodes and cure small local self-intersections
    private func cureLocalIntersections(_ start: Node, _ triangles: inout [Int]) -> Node? {
        var start = start
        var p = start
        repeat {
            let a = p.prev!, b = p.next!.next!

            if !equals(a, b) && intersects(a, p, p.next, b) && locallyInside(a, b) && locallyInside(b, a) {
                triangles.append(a.i / 2)
                triangles.append(p.i / 2)
                triangles.append(b.i / 2)

                // Remove two nodes involved
                removeNode(p)
                removeNode(p.next)

                p = b
                start = b
            }
            p = p.next
        } while p !== start

        return filterPoints(p)
    }

    // Try splitting polygon into two and triangulate them independently
    private func splitEarcut(_ start: Node, _ triangles: inout [Int], _ minX: Double, _ minY: Double, _ invSize: Double) {
        // Look for a valid diagonal that divides the polygon into two
        var a = start
        repeat {
            var b = a.next!.next!
            while b !== a.prev {
                if a.i != b.i && isValidDiagonal(a, b) {
                    // Split the polygon in two by the diagonal
                    var c = splitPolygon(a, b)

                    // Filter colinear points around the cuts
                    let aFiltered = filterPoints(a, a.next)
                    c = filterPoints(c, c.next)!

                    // Run earcut on each half
                    earcutLinked(aFiltered, &triangles, minX, minY, invSize, 0)
                    earcutLinked(c, &triangles, minX, minY, invSize, 0)
                    return
                }
                b = b.next
            }
            a = a.next
        } while a !== start
    }

    // Link every hole into the outer loop, producing a single-ring polygon without holes
    private func eliminateHoles(_ data: [Double], _ holeIndices: [Int], _ outerNode: Node?) -> Node? {
        var outerNode = outerNode
        var queue: [Node] = []

        for i in 0..<holeIndices.count {
            let start = holeIndices[i] * 2
            let end = i < holeIndices.count - 1 ? holeIndices[i + 1] * 2 : data.count
            if let list = linkedList(data, start, end, clockwise: false) {
                if list === list.next { list.steiner = true }
                queue.append(getLeftmost(list))
            }
        }

        queue.sort { $0.x < $1.x }

        // Process holes from left to right
        for hole in queue {
            outerNode = eliminateHole(hole, outerNode)
        }

        return outerNode
    }

    // Find a bridge between vertices that connects hole with an outer ring and link it
    private func eliminateHole(_ hole: Node, _ outerNode: Node?) -> Node? {
        guard let outerNode = outerNode else { return nil }
        guard let bridge = findHoleBridge(hole, outerNode) else { return outerNode }

        let bridgeReverse = splitPolygon(bridge, hole)

        // Filter collinear points around the cuts
        _ = filterPoints(bridgeReverse, bridgeReverse.next)
        return filterPoints(bridge, bridge.next)
    }

    // David Eberly's algorithm for finding a bridge between hole and outer polygon
    private func findHoleBridge(_ hole: Node, _ outerNode: Node) -> Node? {
        var p = outerNode
        let hx = hole.x
        let hy = hole.y
        var qx = -Double.infinity
        var m: Node?

        // Find a segment intersected by a ray from the hole's leftmost point to the left;
        // segment's endpoint with lesser x will be a potential connection point
        repeat {
            if hy <= p.y && hy >= p.next.y && p.next.y != p.y {
                let x = p.x + (hy - p.y) * (p.next.x - p.x) / (p.next.y - p.y)
                if x <= hx && x > qx {
                    qx = x
                    if x == hx {
                        if hy == p.y { return p }
                        if hy == p.next.y { return p.next }
                    }
                    m = p.x < p.next.x ? p : p.next
                }
            }
            p = p.next
        } while p !== outerNode

        guard var mNode = m else { return nil }

        if hx == qx { return mNode } // hole touches outer segment; pick leftmost endpoint

        // Look for points inside the triangle of hole point, segment intersection and endpoint;
        // if there are no points found, we have a valid connection;
        // otherwise choose the point of the minimum angle with the ray as connection point
        let stop = mNode
        let mx = mNode.x
        let my = mNode.y
        var tanMin = Double.infinity

        p = mNode

        repeat {
            if hx >= p.x && p.x >= mx && hx != p.x &&
                pointInTriangle(hy < my ? hx : qx, hy, mx, my, hy < my ? qx : hx, hy, p.x, p.y) {

                let tan = abs(hy - p.y) / (hx - p.x)

                if locallyInside(p, hole) &&
                    (tan < tanMin || (tan == tanMin && (p.x > mNode.x || (p.x == mNode.x && sectorContainsSector(mNode, p))))) {
                    mNode = p
                    tanMin = tan
                }
            }
            p = p.next
        } while p !== stop

        return mNode
    }

    // Whether sector in vertex m contains sector in vertex p in the same coordinates
    private func sectorContainsSector(_ m: Node, _ p: Node) -> Bool {
        return area(m.prev, m, p.prev) < 0 && area(p.next, m, m.next) < 0
    }

    // Interlink polygon nodes in z-order
    private func indexCurve(_ start: Node, _ minX: Double, _ minY: Double, _ invSize: Double) {
        var p = start
        repeat {
            if p.z == 0 { p.z = zOrder(p.x, p.y, minX, minY, invSize) }
            p.prevZ = p.prev
            p.nextZ = p.next
            p = p.next
        } while p !== start

        p.prevZ?.nextZ = nil
        p.prevZ = nil

        _ = sortLinked(p)
    }

    // Simon Tatham's linked list merge sort algorithm
    private func sortLinked(_ list: Node) -> Node {
        var list: Node? = list
        var inSize = 1
        var numMerges = 0

        repeat {
            var p = list
            list = nil
            var tail: Node?
            numMerges = 0

            while p != nil {
                numMerges += 1
                var q = p
                var pSize = 0
                for _ in 0..<inSize {
                    pSize += 1
                    q = q?.nextZ
                    if q == nil { break }
                }
                var qSize = inSize

                while pSize > 0 || (qSize > 0 && q != nil) {
                    var e: Node
                    if pSize != 0 && (qSize == 0 || q == nil || p!.z <= q!.z) {
                        e = p!
                        p = p!.nextZ
                        pSize -= 1
                    } else {
                        e = q!
                        q = q!.nextZ
                        qSize -= 1
                    }

                    if tail != nil { tail!.nextZ = e }
                    else { list = e }

                    e.prevZ = tail
                    tail = e
                }

                p = q
            }

            tail?.nextZ = nil
            inSize *= 2
        } while numMerges > 1

        return list!
    }

    // Z-order of a point given coords and inverse of the longer side of data bbox
    private func zOrder(_ x: Double, _ y: Double, _ minX: Double, _ minY: Double, _ invSize: Double) -> Int32 {
        // Coords are transformed into non-negative 15-bit integer range
        var x = Int32((x - minX) * invSize)
        var y = Int32((y - minY) * invSize)

        x = (x | (x << 8)) & 0x00FF00FF
        x = (x | (x << 4)) & 0x0F0F0F0F
        x = (x | (x << 2)) & 0x33333333
        x = (x | (x << 1)) & 0x55555555

        y = (y | (y << 8)) & 0x00FF00FF
        y = (y | (y << 4)) & 0x0F0F0F0F
        y = (y | (y << 2)) & 0x33333333
        y = (y | (y << 1)) & 0x55555555

        return x | (y << 1)
    }

    // Find the leftmost node of a polygon ring
    private func getLeftmost(_ start: Node) -> Node {
        var p = start
        var leftmost = start
        repeat {
            if p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y) {
                leftmost = p
            }
            p = p.next
        } while p !== start
        return leftmost
    }

    private func pointInTriangle(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ cx: Double, _ cy: Double, _ px: Double, _ py: Double) -> Bool {
        return (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
               (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
               (bx - px) * (cy - py) >= (cx - px) * (by - py)
    }

    // Check if a diagonal between two polygon nodes is valid (lies in polygon interior)
    private func isValidDiagonal(_ a: Node, _ b: Node) -> Bool {
        return a.next.i != b.i && a.prev.i != b.i && !intersectsPolygon(a, b) && // doesn't intersect other edges
            (locallyInside(a, b) && locallyInside(b, a) && middleInside(a, b) && // locally visible
             (area(a.prev, a, b.prev) != 0 || area(a, b.prev, b) != 0) || // does not create opposite-facing sectors
             equals(a, b) && area(a.prev, a, a.next) > 0 && area(b.prev, b, b.next) > 0) // special zero-length case
    }

    // Signed area of a triangle
    private func area(_ p: Node, _ q: Node, _ r: Node) -> Double {
        return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
    }

    // Check if two points are equal
    private func equals(_ p1: Node, _ p2: Node) -> Bool {
        return p1.x == p2.x && p1.y == p2.y
    }

    // Check if two segments intersect
    private func intersects(_ p1: Node, _ q1: Node, _ p2: Node, _ q2: Node) -> Bool {
        let o1 = sign(area(p1, q1, p2))
        let o2 = sign(area(p1, q1, q2))
        let o3 = sign(area(p2, q2, p1))
        let o4 = sign(area(p2, q2, q1))

        if o1 != o2 && o3 != o4 { return true } // general case

        if o1 == 0 && onSegment(p1, p2, q1) { return true } // p1, q1 and p2 are collinear and p2 lies on p1q1
        if o2 == 0 && onSegment(p1, q2, q1) { return true } // p1, q1 and q2 are collinear and q2 lies on p1q1
        if o3 == 0 && onSegment(p2, p1, q2) { return true } // p2, q2 and p1 are collinear and p1 lies on p2q2
        if o4 == 0 && onSegment(p2, q1, q2) { return true } // p2, q2 and q1 are collinear and q1 lies on p2q2

        return false
    }

    // For collinear points p, q, r: check if point q lies on segment pr
    private func onSegment(_ p: Node, _ q: Node, _ r: Node) -> Bool {
        return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
    }

    private func sign(_ num: Double) -> Int {
        return num > 0 ? 1 : num < 0 ? -1 : 0
    }

    // Check if a polygon diagonal intersects any polygon segments
    private func intersectsPolygon(_ a: Node, _ b: Node) -> Bool {
        var p = a
        repeat {
            if p.i != a.i && p.next.i != a.i && p.i != b.i && p.next.i != b.i &&
                intersects(p, p.next, a, b) {
                return true
            }
            p = p.next
        } while p !== a
        return false
    }

    // Check if a polygon diagonal is locally inside the polygon
    private func locallyInside(_ a: Node, _ b: Node) -> Bool {
        return area(a.prev, a, a.next) < 0 ?
            area(a, b, a.next) >= 0 && area(a, a.prev, b) >= 0 :
            area(a, b, a.prev) < 0 || area(a, a.next, b) < 0
    }

    // Check if the middle point of a polygon diagonal is inside the polygon
    private func middleInside(_ a: Node, _ b: Node) -> Bool {
        var p = a
        var inside = false
        let px = (a.x + b.x) / 2
        let py = (a.y + b.y) / 2
        repeat {
            if ((p.y > py) != (p.next.y > py)) && p.next.y != p.y &&
                (px < (p.next.x - p.x) * (py - p.y) / (p.next.y - p.y) + p.x) {
                inside = !inside
            }
            p = p.next
        } while p !== a
        return inside
    }

    // Link two polygon vertices with a bridge; if the vertices belong to the same ring, it splits
    // polygon into two; if one belongs to the outer ring and another to a hole, it merges it into a
    // single ring
    private func splitPolygon(_ a: Node, _ b: Node) -> Node {
        let a2 = Node(a.i, a.x, a.y)
        let b2 = Node(b.i, b.x, b.y)
        allNodes.append(a2)
        allNodes.append(b2)
        let an = a.next!
        let bp = b.prev!

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
    private func insertNode(_ i: Int, _ x: Double, _ y: Double, _ last: Node?) -> Node {
        let p = Node(i, x, y)
        allNodes.append(p)

        if let last = last {
            p.next = last.next
            p.prev = last
            last.next.prev = p
            last.next = p
        } else {
            p.prev = p
            p.next = p
        }
        return p
    }

    private func removeNode(_ p: Node) {
        p.next.prev = p.prev
        p.prev.next = p.next

        p.prevZ?.nextZ = p.nextZ
        p.nextZ?.prevZ = p.prevZ
    }

    private func signedArea(_ data: [Double], _ start: Int, _ end: Int) -> Double {
        var sum = 0.0
        var i = start
        var j = end - 2
        while i < end {
            sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
            j = i
            i += 2
        }
        return sum
    }
}
