import XCTest
@testable import voyage

/// The border graph answers as `shared/fixtures/daily/graph.json` says — the
/// vectors `scripts/generate_neighbors.py` writes, which Android
/// (`CountryGraphTest`) and pgTAP (`country_graph_vectors_test.sql`) check too.
final class CountryGraphTests: XCTestCase {

    private struct Vectors: Decodable {
        struct Route: Decodable {
            let from: String
            let to: String
            let between: Int
            let routes: [[String]]
        }

        struct Distance: Decodable {
            let a: String
            let b: String
            let km: Int
        }

        let neighbors: [String: [String]]
        let routes: [Route]
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

    func testNeighboursMatchTheVectors() {
        XCTAssertFalse(Self.vectors.neighbors.isEmpty)
        for (code, neighbors) in Self.vectors.neighbors {
            XCTAssertEqual(cache.neighbors(of: code), neighbors, code)
        }
    }

    func testShortestRoutesMatchTheVectors() {
        XCTAssertFalse(Self.vectors.routes.isEmpty)
        for vector in Self.vectors.routes {
            let routes = cache.shortestRoutes(from: vector.from, to: vector.to)
            XCTAssertEqual(routes, vector.routes, "\(vector.from)-\(vector.to)")
            let between = routes.first.map { max($0.count - 2, 0) } ?? -1
            XCTAssertEqual(between, vector.between, "\(vector.from)-\(vector.to)")
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

    func testUnknownCodesHaveNoNeighboursDistanceOrRoute() {
        XCTAssertEqual(cache.neighbors(of: "XX"), [])
        XCTAssertNil(cache.borderDistanceKm("FR", "XX"))
        XCTAssertEqual(cache.shortestRoutes(from: "FR", to: "XX"), [])
        XCTAssertEqual(cache.shortestRoutes(from: "XX", to: "XX"), [])
        XCTAssertEqual(cache.borderDistanceKm("FR", "FR"), 0)
    }
}
