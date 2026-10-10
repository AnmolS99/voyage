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
    private var focuses: [String: CountryFocus] = [:]
    private let focusLock = NSLock()

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

    /// What selecting `name` frames on the globe — see `CountryFocus`. Worked out
    /// on first use and kept, since the enclosing cap walks every boundary point
    /// of the country a hundred times.
    func focus(of name: String) -> CountryFocus? {
        focusLock.lock()
        defer { focusLock.unlock() }
        if let cached = focuses[name] { return cached }
        guard let country = countriesByName[name], let focus = CountryFocus(country) else { return nil }
        focuses[name] = focus
        return focus
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

/// What selecting a country frames: the smallest circle on the sphere — a cap —
/// around its main landmass, as a center and an angular radius in degrees.
///
/// The globe turns the radius into a camera distance for the view it is in
/// (`GlobeState.distanceToFit`), so Luxembourg fills the screen and Russia fits
/// inside it instead of every country landing at one fixed zoom.
///
/// Android's `CountryFocus` is a port of this, down to the iteration count:
/// `CountryFocusTests` here and Android's `CountryHitTesterTest` pin the same values.
struct CountryFocus {
    let lat: Double
    let lon: Double
    let radiusDegrees: Double

    /// Rings smaller than this fraction of the country's largest ring are left
    /// out of the frame: the US frames the contiguous states and Alaska, not
    /// Hawaii; Portugal its mainland, not the Azores.
    static let mainLandmassFraction = 0.1

    /// Steps of the Bădoiu–Clarkson enclosing-ball iteration.
    static let iterations = 100

    init(lat: Double, lon: Double, radiusDegrees: Double) {
        self.lat = lat
        self.lon = lon
        self.radiusDegrees = radiusDegrees
    }

    init?(_ country: GeoJSONCountry) {
        if country.isPointCountry {
            guard let coord = country.pointCoordinate else { return nil }
            self = CountryFocus(lat: coord.lat, lon: coord.lon, radiusDegrees: 0)
            return
        }
        let rings = country.polygons.map { $0.filter { $0.count >= 2 } }.filter { !$0.isEmpty }
        guard !rings.isEmpty else { return nil }
        let areas = rings.map { Self.approximateArea($0) }
        let largest = areas.max()!
        let main = zip(rings, areas).filter { $0.1 >= Self.mainLandmassFraction * largest }.map { $0.0 }
        self = Self.enclosingCap(main)
    }

    /// Shoelace area in degrees², scaled by the cosine of the ring's mean
    /// latitude. Only ever compared between rings of one country, so the rough
    /// correction for converging meridians is all it needs.
    private static func approximateArea(_ ring: [[Double]]) -> Double {
        var twice = 0.0
        var latSum = 0.0
        for i in 0..<ring.count {
            let j = (i + 1) % ring.count
            twice += ring[i][0] * ring[j][1] - ring[j][0] * ring[i][1]
            latSum += ring[i][1]
        }
        return abs(twice) / 2 * cos(latSum / Double(ring.count) * .pi / 180)
    }

    /// The smallest enclosing ball of the points as unit vectors, found with
    /// Bădoiu–Clarkson (step toward the farthest point by 1/(k+1)) and pushed back
    /// onto the sphere. Working in 3D makes it antimeridian-safe for free, and the
    /// cap's center is not dragged toward detailed coastlines the way a mean of
    /// boundary points is.
    private static func enclosingCap(_ rings: [[[Double]]]) -> CountryFocus {
        var points: [SIMD3<Double>] = []
        for ring in rings {
            for coord in ring {
                let lat = coord[1] * .pi / 180
                let lon = coord[0] * .pi / 180
                points.append(SIMD3(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat)))
            }
        }

        var center = points.reduce(SIMD3<Double>(), +) / Double(points.count)
        for k in 1...iterations {
            var far = points[0]
            var farthest = -1.0
            for point in points {
                let d = point - center
                let distance = d.x * d.x + d.y * d.y + d.z * d.z
                if distance > farthest {
                    farthest = distance
                    far = point
                }
            }
            center += (far - center) * (1.0 / Double(k + 1))
        }

        let length = (center.x * center.x + center.y * center.y + center.z * center.z).squareRoot()
        center /= length
        var smallestDot = 1.0
        for point in points {
            smallestDot = min(smallestDot, point.x * center.x + point.y * center.y + point.z * center.z)
        }

        return CountryFocus(
            lat: asin(max(-1, min(1, center.z))) * 180 / .pi,
            lon: atan2(center.y, center.x) * 180 / .pi,
            radiusDegrees: acos(max(-1, smallestDot)) * 180 / .pi
        )
    }
}
