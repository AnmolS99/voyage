import XCTest
@testable import voyage

/// The border graph answers as `shared/fixtures/daily/graph.json` says — the
/// vectors `scripts/generate_neighbors.py` writes, which Android
/// (`CountryGraphTest`) and pgTAP (`country_graph_vectors_test.sql`) check too.
final class CountryGraphTests: XCTestCase {

    private struct Vectors: Decodable {
        struct Crossing: Decodable {
            let a: String
            let b: String
            let crossings: Int?
        }

        struct Distance: Decodable {
            let a: String
            let b: String
            let km: Int
        }

        let version: String
        let neighbors: [String: [String]]
        let crossings: [Crossing]
        let distances: [Distance]
    }

    private static let vectors: Vectors = {
        guard let url = Bundle(for: CountryGraphTests.self).url(forResource: "graph", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let vectors = try? JSONDecoder().decode(Vectors.self, from: data) else {
            fatalError("graph.json is missing from the test bundle")
        }
        return vectors
    }()

    private let cache = CountryDataCache.shared

    func testGraphVersionMatchesTheVectors() {
        XCTAssertEqual(cache.countryGraph.version, Self.vectors.version)
    }

    func testNeighboursMatchTheVectors() {
        XCTAssertFalse(Self.vectors.neighbors.isEmpty)
        for (code, neighbors) in Self.vectors.neighbors {
            XCTAssertEqual(cache.neighbors(of: code), neighbors, code)
        }
    }

    func testCrossingsMatchTheVectorsInEitherOrder() {
        XCTAssertFalse(Self.vectors.crossings.isEmpty)
        for vector in Self.vectors.crossings {
            XCTAssertEqual(cache.borderCrossings(vector.a, vector.b), vector.crossings, "\(vector.a)-\(vector.b)")
            XCTAssertEqual(cache.borderCrossings(vector.b, vector.a), vector.crossings, "\(vector.b)-\(vector.a)")
        }
    }

    /// Every pair, not just the vectors: one crossing means neighbours, and
    /// each step of a shortest route brings a neighbour one crossing closer.
    func testCrossingsAreShortestRoutesOverTheNeighbours() {
        let graph = cache.countryGraph
        for a in graph.codes {
            for b in graph.codes where b != a {
                let crossings = graph.borderCrossings(a, b)
                XCTAssertEqual(crossings == 1, graph.neighbors(of: a).contains(b), "\(a)-\(b)")
                let closest = graph.neighbors(of: a).compactMap { graph.borderCrossings($0, b) }.min()
                XCTAssertEqual(crossings, closest.map { $0 + 1 }, "\(a)-\(b)")
            }
        }
    }

    func testDistancesMatchTheVectorsInEitherOrder() {
        XCTAssertFalse(Self.vectors.distances.isEmpty)
        for vector in Self.vectors.distances {
            XCTAssertEqual(cache.borderDistanceKm(vector.a, vector.b), vector.km, "\(vector.a)-\(vector.b)")
            XCTAssertEqual(cache.borderDistanceKm(vector.b, vector.a), vector.km, "\(vector.b)-\(vector.a)")
        }
    }

    func testGraphIsSymmetricAndZeroKmMeansNeighbours() {
        let graph = cache.countryGraph
        for a in graph.codes {
            let neighbors = Set(graph.neighbors(of: a))
            XCTAssertFalse(neighbors.contains(a), a)
            for b in neighbors {
                XCTAssertTrue(graph.neighbors(of: b).contains(a), "\(a)-\(b)")
            }
            for b in graph.codes where b != a {
                XCTAssertEqual(graph.borderDistanceKm(a, b) == 0, neighbors.contains(b), "\(a)-\(b)")
            }
        }
    }

    func testEveryCountryHasAPlaceInTheGraph() {
        let countries = cache.countries.compactMap(\.flagCode).sorted()
        XCTAssertEqual(cache.countryGraph.codes, countries)
    }

    func testUnknownCodesHaveNoNeighboursDistanceOrCrossings() {
        XCTAssertEqual(cache.neighbors(of: "XX"), [])
        XCTAssertNil(cache.borderDistanceKm("FR", "XX"))
        XCTAssertNil(cache.borderCrossings("FR", "XX"))
        XCTAssertNil(cache.borderCrossings("XX", "XX"))
        XCTAssertEqual(cache.borderDistanceKm("FR", "FR"), 0)
        XCTAssertEqual(cache.borderCrossings("FR", "FR"), 0)
    }
}
