import Foundation

/// Shared country hit-testing for the globe and map views.
/// Precomputes per-polygon bounding boxes so lookups stay fast with high-detail
/// boundary data (a world-wide point-in-polygon scan touches ~170k points).
final class CountryHitTester {
    static let shared = CountryHitTester()

    private struct PolygonEntry {
        let countryName: String
        let ring: [[Double]]
        let minLon: Double, maxLon: Double, minLat: Double, maxLat: Double

        func boundingBoxContains(lon: Double, lat: Double) -> Bool {
            lon >= minLon && lon <= maxLon && lat >= minLat && lat <= maxLat
        }
    }

    private let polygonEntries: [PolygonEntry]
    private let holeEntries: [PolygonEntry]
    private let pointCountries: [(name: String, lat: Double, lon: Double)]
    private let countriesByName: [String: GeoJSONCountry]

    private init() {
        var polygonEntries: [PolygonEntry] = []
        var holeEntries: [PolygonEntry] = []
        var pointCountries: [(name: String, lat: Double, lon: Double)] = []
        var countriesByName: [String: GeoJSONCountry] = [:]

        func makeEntry(_ name: String, _ ring: [[Double]]) -> PolygonEntry? {
            var minLon = Double.infinity, maxLon = -Double.infinity
            var minLat = Double.infinity, maxLat = -Double.infinity
            for coord in ring where coord.count >= 2 {
                minLon = min(minLon, coord[0])
                maxLon = max(maxLon, coord[0])
                minLat = min(minLat, coord[1])
                maxLat = max(maxLat, coord[1])
            }
            guard minLon <= maxLon else { return nil }
            return PolygonEntry(countryName: name, ring: ring,
                                minLon: minLon, maxLon: maxLon, minLat: minLat, maxLat: maxLat)
        }

        for country in CountryDataCache.shared.countries {
            countriesByName[country.name] = country
            if country.isPointCountry {
                if let coord = country.pointCoordinate {
                    pointCountries.append((name: country.name, lat: coord.lat, lon: coord.lon))
                }
                continue
            }
            for polygon in country.polygons {
                if let entry = makeEntry(country.name, polygon) { polygonEntries.append(entry) }
            }
            for hole in country.holes {
                if let entry = makeEntry(country.name, hole) { holeEntries.append(entry) }
            }
        }

        self.polygonEntries = polygonEntries
        self.holeEntries = holeEntries
        self.pointCountries = pointCountries
        self.countriesByName = countriesByName
    }

    /// Finds the country at a lat/lon, checking point countries first, then exact polygon
    /// containment, then an expanding-radius search so small countries stay tappable.
    func findCountry(lat: Double, lon: Double) -> String? {
        let pointHitRadius: Double = 0.8
        for point in pointCountries {
            let distance = ((lat - point.lat) * (lat - point.lat) + (lon - point.lon) * (lon - point.lon)).squareRoot()
            if distance < pointHitRadius {
                return point.name
            }
        }

        if let name = findCountryExact(lat: lat, lon: lon) {
            return name
        }

        // Search in expanding radius for small countries
        let searchRadii: [Double] = [0.5, 1.0, 2.0, 3.0]
        let pointsPerRadius = 8

        for radius in searchRadii {
            for i in 0..<pointsPerRadius {
                let angle = Double(i) * (2.0 * .pi / Double(pointsPerRadius))
                if let name = findCountryExact(lat: lat + radius * sin(angle), lon: lon + radius * cos(angle)) {
                    return name
                }
            }
        }

        return nil
    }

    /// Country whose polygon strictly contains the point (enclave holes excluded,
    /// so e.g. a tap inside Lesotho never resolves to South Africa).
    func findCountryExact(lat: Double, lon: Double) -> String? {
        for entry in polygonEntries where entry.boundingBoxContains(lon: lon, lat: lat) {
            guard PolygonTriangulator.isPointInPolygon(lon: lon, lat: lat, polygon: entry.ring) else { continue }
            let inHole = holeEntries.contains { hole in
                hole.countryName == entry.countryName &&
                hole.boundingBoxContains(lon: lon, lat: lat) &&
                PolygonTriangulator.isPointInPolygon(lon: lon, lat: lat, polygon: hole.ring)
            }
            if !inHole {
                return entry.countryName
            }
        }
        return nil
    }

    /// Geographic center of a country (average of boundary points, antimeridian-aware).
    func center(of name: String) -> (lat: Double, lon: Double)? {
        guard let country = countriesByName[name] else { return nil }

        // Point countries have their center stored directly
        if country.isPointCountry, let coord = country.pointCoordinate {
            return coord
        }

        var lats: [Double] = []
        var lons: [Double] = []
        for polygon in country.polygons {
            for coord in polygon where coord.count >= 2 {
                lons.append(coord[0])
                lats.append(coord[1])
            }
        }
        guard !lats.isEmpty else { return nil }

        // Handle antimeridian-crossing countries (e.g. Fiji): if the longitude
        // range exceeds 180°, shift negative longitudes by +360° before averaging.
        let minLon = lons.min()!
        let maxLon = lons.max()!
        var avgLon: Double
        if maxLon - minLon > 180 {
            let adjusted = lons.map { $0 < 0 ? $0 + 360 : $0 }
            avgLon = adjusted.reduce(0, +) / Double(adjusted.count)
            if avgLon > 180 { avgLon -= 360 }
        } else {
            avgLon = lons.reduce(0, +) / Double(lons.count)
        }

        return (lat: lats.reduce(0, +) / Double(lats.count), lon: avgLon)
    }
}
