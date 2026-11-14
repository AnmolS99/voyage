import Foundation

/// A capital city and its location.
typealias Capital = (name: String, lat: Double, lon: Double)

struct GeoJSONCountry {
    let name: String
    let polygons: [Ring] // Outer rings (empty for point countries)
    let holes: [Ring]    // Inner rings (holes) — e.g., the Lesotho enclave inside South Africa
    let continent: String?
    let isPointCountry: Bool
    let pointCoordinate: (lat: Double, lon: Double)? // For point countries (small islands, microstates)
    let flagCode: String?
    /// The capital the country is best known by (La Paz for Bolivia).
    let capital: Capital?
    /// The country's other official capitals (Sucre for Bolivia), in the
    /// order `world.geojson` lists them. Empty for most countries.
    let otherCapitals: [Capital]

    /// Every capital, the main one first. Each is a right answer in Name the
    /// Capital and marked on the globe and map.
    var capitals: [Capital] {
        (capital.map { [$0] } ?? []) + otherCapitals
    }

    var capitalNames: [String] {
        capitals.map(\.name)
    }

    /// "La Paz, Sucre" — the capitals on one line, or nil when there are none.
    var capitalsLine: String? {
        capitals.isEmpty ? nil : capitalNames.joined(separator: ", ")
    }

    /// The capital of this country `name` names, ignoring case, or nil.
    func capital(named name: String) -> Capital? {
        capitals.first { $0.name.caseInsensitiveCompare(name) == .orderedSame }
    }
}

class GeoJSONParser {

    static func loadCountries() -> [GeoJSONCountry] {
        guard let url = Bundle.main.url(forResource: "world", withExtension: "geojson"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = json["features"] as? [[String: Any]] else {
            print("Failed to load GeoJSON")
            return []
        }

        var countries: [GeoJSONCountry] = []

        for feature in features {
            guard let properties = feature["properties"] as? [String: Any],
                  let name = properties["name"] as? String,
                  let geometry = feature["geometry"] as? [String: Any],
                  let type = geometry["type"] as? String,
                  let coordinates = geometry["coordinates"] else {
                continue
            }

            let continent = properties["continent"] as? String
            let flagCode = feature["id"] as? String
            let renderAs = properties["renderAs"] as? String
            let isPointCountry = renderAs == "point"

            // Parse capital data
            var capital: Capital? = nil
            if let capitalName = properties["capital"] as? String,
               let capitalLat = properties["capitalLat"] as? Double,
               let capitalLon = properties["capitalLon"] as? Double {
                capital = (name: capitalName, lat: capitalLat, lon: capitalLon)
            }
            let otherCapitals: [Capital] = (properties["otherCapitals"] as? [[String: Any]] ?? []).compactMap { other in
                guard let name = other["name"] as? String,
                      let lat = other["lat"] as? Double,
                      let lon = other["lon"] as? Double else { return nil }
                return (name: name, lat: lat, lon: lon)
            }

            if type == "Point" {
                // Point country (small islands, microstates)
                if let coords = coordinates as? [Double], coords.count >= 2 {
                    let lon = coords[0]
                    let lat = coords[1]
                    let country = GeoJSONCountry(
                        name: name,
                        polygons: [],
                        holes: [],
                        continent: continent,
                        isPointCountry: true,
                        pointCoordinate: (lat: lat, lon: lon),
                        flagCode: flagCode,
                        capital: capital,
                        otherCapitals: otherCapitals
                    )
                    countries.append(country)
                }
            } else {
                // Polygon or MultiPolygon country
                var polygons: [Ring] = []
                var holes: [Ring] = []

                if type == "Polygon" {
                    if let coords = coordinates as? [[[Double]]] {
                        // First ring is the outer boundary; subsequent rings are holes (e.g., enclaves)
                        if let outerRing = coords.first {
                            polygons.append(Ring(geoJSON: outerRing))
                        }
                        holes.append(contentsOf: coords.dropFirst().map { Ring(geoJSON: $0) })
                    }
                } else if type == "MultiPolygon" {
                    if let multiCoords = coordinates as? [[[[Double]]]] {
                        for polygon in multiCoords {
                            if let outerRing = polygon.first {
                                polygons.append(Ring(geoJSON: outerRing))
                            }
                            holes.append(contentsOf: polygon.dropFirst().map { Ring(geoJSON: $0) })
                        }
                    }
                }

                if !polygons.isEmpty {
                    let country = GeoJSONCountry(
                        name: name,
                        polygons: polygons,
                        holes: holes,
                        continent: continent,
                        isPointCountry: isPointCountry,
                        pointCoordinate: nil,
                        flagCode: flagCode,
                        capital: capital,
                        otherCapitals: otherCapitals
                    )
                    countries.append(country)
                }
            }
        }

        return countries
    }
}
