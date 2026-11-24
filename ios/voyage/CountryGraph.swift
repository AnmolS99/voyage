import Foundation

/// Which countries border each other, and how far apart every pair of
/// outlines is: the graph `scripts/generate_neighbors.py` writes to
/// `neighbors.json` and `border_distances.json`, which Android reads too
/// (Postgres has the graph alone). The Android port is `CountryGraph` as well; `CountryGraphTests`
/// and its Android twin hold both to `shared/fixtures/daily/graph.json`.
///
/// Reach it through `CountryDataCache.shared` (`neighbors(of:)`,
/// `borderDistanceKm(_:_:)`, `shortestRoutes(from:to:)`), never by loading it
/// again.
struct CountryGraph {

    /// Every country's ISO code, sorted.
    let codes: [String]

    private let neighborLists: [String: [String]]
    /// The upper triangle of the km matrix, row by row: for `i < j`, the km
    /// between `codes[i]` and `codes[j]` sits at `triangleIndex(i, j)`.
    private let distances: [Int]
    private let indexOf: [String: Int]

    init(codes: [String], neighbors: [String: [String]], distances: [Int]) {
        precondition(zip(codes, codes.dropFirst()).allSatisfy { $0 < $1 }, "codes must be sorted and unique")
        precondition(distances.count == codes.count * (codes.count - 1) / 2, "one distance per pair")
        precondition(Set(neighbors.keys) == Set(codes), "neighbours must list every country")
        self.codes = codes
        self.neighborLists = neighbors
        self.distances = distances
        self.indexOf = Dictionary(uniqueKeysWithValues: codes.enumerated().map { ($1, $0) })
    }

    /// The countries bordering `isoCode`, sorted; empty for an island or an
    /// unknown code.
    func neighbors(of isoCode: String) -> [String] {
        neighborLists[isoCode] ?? []
    }

    /// Great-circle km between the closest points of two countries' outlines,
    /// in either order: 0 exactly for neighbours and for the same country, nil
    /// for an unknown code. Android `borderDistanceKm`.
    func borderDistanceKm(_ a: String, _ b: String) -> Int? {
        guard let i = indexOf[a], let j = indexOf[b] else { return nil }
        if i == j { return 0 }
        return distances[triangleIndex(min(i, j), max(i, j))]
    }

    /// Every shortest route from `start` to `goal` over the border graph, both
    /// ends included, sorted. `[start]` alone when they are the same country;
    /// empty when no route exists or a code is unknown.
    ///
    /// Breadth-first from `start`, then back from `goal` through each
    /// country's neighbours one step closer. The generator's `shortest_routes`,
    /// Android's `shortestRoutes` and Postgres's `shortest_routes` do the same.
    func shortestRoutes(from start: String, to goal: String) -> [[String]] {
        guard indexOf[start] != nil, indexOf[goal] != nil else { return [] }
        if start == goal { return [[start]] }

        var depth = [start: 0]
        var queue = [start]
        var head = 0
        while head < queue.count {
            let current = queue[head]
            head += 1
            if current == goal { break }
            let next = depth[current]! + 1
            for neighbor in neighbors(of: current) where depth[neighbor] == nil {
                depth[neighbor] = next
                queue.append(neighbor)
            }
        }
        guard depth[goal] != nil else { return [] }

        func routes(to code: String) -> [[String]] {
            if code == start { return [[start]] }
            let closer = depth[code]! - 1
            return neighbors(of: code)
                .filter { depth[$0] == closer }
                .flatMap { previous in routes(to: previous).map { $0 + [code] } }
        }

        return routes(to: goal).sorted { $0.lexicographicallyPrecedes($1) }
    }

    private func triangleIndex(_ i: Int, _ j: Int) -> Int {
        i * (2 * codes.count - i - 1) / 2 + (j - i - 1)
    }
}

extension CountryGraph {

    private struct Distances: Decodable {
        let codes: [String]
        let rows: [[Int]]
    }

    /// Parses `neighbors.json` and `border_distances.json` from `bundle`. Both
    /// ship with the app, so a missing or malformed file is a build mistake and
    /// crashes rather than leaving the games an empty graph, as on Android.
    static func load(from bundle: Bundle = .main) -> CountryGraph {
        func contents(of name: String) -> Data {
            guard let url = bundle.url(forResource: name, withExtension: "json"),
                  let data = try? Data(contentsOf: url) else {
                fatalError("\(name).json is missing from the app bundle")
            }
            return data
        }
        do {
            return try decode(neighbors: contents(of: "neighbors"), borderDistances: contents(of: "border_distances"))
        } catch {
            fatalError("Failed to decode the country graph: \(error)")
        }
    }

    /// The graph in the contents of `neighbors.json` and `border_distances.json`.
    /// Throws when either is malformed JSON, and traps when they disagree.
    static func decode(neighbors neighborsData: Data, borderDistances distancesData: Data) throws -> CountryGraph {
        let neighbors = try JSONDecoder().decode([String: [String]].self, from: neighborsData)
        let distances = try JSONDecoder().decode(Distances.self, from: distancesData)
        let count = distances.codes.count
        for (i, row) in distances.rows.enumerated() {
            precondition(row.count == count - 1 - i, "row \(i) of border_distances.json has \(row.count) entries")
        }
        return CountryGraph(codes: distances.codes, neighbors: neighbors, distances: distances.rows.flatMap { $0 })
    }
}
