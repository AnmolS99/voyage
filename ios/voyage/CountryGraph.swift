import Foundation

/// Which countries border each other, and how many borders and km apart
/// every pair is: what `scripts/generate_neighbors.py` writes to
/// `neighbors.json`, `border_crossings.json` and `border_distances.json`,
/// which Android reads too (Postgres has the graph alone). The Android port is
/// `CountryGraph` as well; `CountryGraphTests` and its Android twin hold both
/// to `shared/fixtures/daily/graph.json`.
///
/// Reach it through `CountryDataCache.shared` (`neighbors(of:)`,
/// `borderCrossings(_:_:)`, `borderDistanceKm(_:_:)`), never by loading it
/// again.
struct CountryGraph {

    /// A hash of the borders (`neighbors.json`'s `version`), which Postgres's
    /// `country_graph_version()` returns too: send it with a Travel answer so
    /// the server can tell an older app's graph from its own.
    let version: String

    /// Every country's ISO code, sorted.
    let codes: [String]

    private let neighborLists: [String: [String]]
    /// The upper triangles of the crossings and km matrices, row by row: for
    /// `i < j`, the value for `codes[i]` and `codes[j]` (see `value(in:_:_:)`).
    private let crossings: [Int]
    private let distances: [Int]
    private let indexOf: [String: Int]

    init(version: String, codes: [String], neighbors: [String: [String]], crossings: [Int], distances: [Int]) {
        let pairs = codes.count * (codes.count - 1) / 2
        precondition(zip(codes, codes.dropFirst()).allSatisfy { $0 < $1 }, "codes must be sorted and unique")
        precondition(crossings.count == pairs && distances.count == pairs, "one crossing count and distance per pair")
        precondition(Set(neighbors.keys) == Set(codes), "neighbours must list every country")
        self.version = version
        self.codes = codes
        self.neighborLists = neighbors
        self.crossings = crossings
        self.distances = distances
        self.indexOf = Dictionary(uniqueKeysWithValues: codes.enumerated().map { ($1, $0) })
    }

    /// The countries bordering `isoCode`, sorted; empty for an island or an
    /// unknown code.
    func neighbors(of isoCode: String) -> [String] {
        neighborLists[isoCode] ?? []
    }

    /// The fewest borders a route between two countries crosses, in either
    /// order: 0 for the same country, 1 for neighbours, nil when no route
    /// exists or a code is unknown. Android `borderCrossings`.
    ///
    /// This is all Travel needs from routes: the shortest one has
    /// `crossings - 1` countries in between, and `x` is on some shortest route
    /// from `a` to `b` exactly when `crossings(a, x) + crossings(x, b) ==
    /// crossings(a, b)`.
    func borderCrossings(_ a: String, _ b: String) -> Int? {
        value(in: crossings, a, b).flatMap { $0 < 0 ? nil : $0 }
    }

    /// Great-circle km between the closest points of two countries' outlines,
    /// in either order: 0 exactly for neighbours and for the same country, nil
    /// for an unknown code. Android `borderDistanceKm`.
    func borderDistanceKm(_ a: String, _ b: String) -> Int? {
        value(in: distances, a, b)
    }

    /// `triangle`'s value for `a` and `b`: 0 for the same country, nil for an
    /// unknown code.
    private func value(in triangle: [Int], _ a: String, _ b: String) -> Int? {
        guard let i = indexOf[a], let j = indexOf[b] else { return nil }
        if i == j { return 0 }
        let (low, high) = (min(i, j), max(i, j))
        return triangle[low * (2 * codes.count - low - 1) / 2 + (high - low - 1)]
    }
}

extension CountryGraph {

    private struct Neighbors: Decodable {
        let version: String
        let neighbors: [String: [String]]
    }

    /// `border_crossings.json` or `border_distances.json`.
    private struct Triangle: Decodable {
        let codes: [String]
        let rows: [[Int]]

        func flattened(_ name: String) -> [Int] {
            for (i, row) in rows.enumerated() {
                precondition(row.count == codes.count - 1 - i, "row \(i) of \(name).json has \(row.count) entries")
            }
            return rows.flatMap { $0 }
        }
    }

    /// Parses `neighbors.json`, `border_crossings.json` and
    /// `border_distances.json` from `bundle`. They ship with the app, so a
    /// missing or malformed file is a build mistake and crashes rather than
    /// leaving the games an empty graph, as on Android.
    static func load(from bundle: Bundle = .main) -> CountryGraph {
        func contents(of name: String) -> Data {
            guard let url = bundle.url(forResource: name, withExtension: "json"),
                  let data = try? Data(contentsOf: url) else {
                fatalError("\(name).json is missing from the app bundle")
            }
            return data
        }
        do {
            return try decode(
                neighbors: contents(of: "neighbors"),
                borderCrossings: contents(of: "border_crossings"),
                borderDistances: contents(of: "border_distances")
            )
        } catch {
            fatalError("Failed to decode the country graph: \(error)")
        }
    }

    /// The graph in the contents of `neighbors.json`, `border_crossings.json`
    /// and `border_distances.json`. Throws when one is malformed JSON, and traps
    /// when they disagree.
    static func decode(neighbors neighborsData: Data, borderCrossings crossingsData: Data, borderDistances distancesData: Data) throws -> CountryGraph {
        let graph = try JSONDecoder().decode(Neighbors.self, from: neighborsData)
        let crossings = try JSONDecoder().decode(Triangle.self, from: crossingsData)
        let distances = try JSONDecoder().decode(Triangle.self, from: distancesData)
        precondition(crossings.codes == distances.codes, "border_crossings.json and border_distances.json list different countries")
        return CountryGraph(
            version: graph.version,
            codes: distances.codes,
            neighbors: graph.neighbors,
            crossings: crossings.flattened("border_crossings"),
            distances: distances.flattened("border_distances")
        )
    }
}
