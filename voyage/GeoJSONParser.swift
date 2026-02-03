import Foundation
import UIKit

struct GeoJSONCountry {
    let name: String
    let polygons: [[[Double]]] // Array of polygons, each polygon is array of [lon, lat] coordinates (empty for point countries)
    let color: UIColor
    let continent: String?
    let isPointCountry: Bool
    let pointCoordinate: (lat: Double, lon: Double)? // For point countries (small islands, microstates)
    let flagCode: String?
    let capital: (name: String, lat: Double, lon: Double)?

    static func landColor() -> UIColor {
        return AppColors.landUI
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
                  let name = properties["name"] as? String ?? properties["NAME"] as? String,
                  let geometry = feature["geometry"] as? [String: Any],
                  let type = geometry["type"] as? String,
                  let coordinates = geometry["coordinates"] else {
                continue
            }

            let continent = properties["continent"] as? String
            let flagCode = properties["flagCode"] as? String
            let renderAs = properties["renderAs"] as? String
            let isPointCountry = renderAs == "point"

            // Parse capital data
            var capital: (name: String, lat: Double, lon: Double)? = nil
            if let capitalName = properties["capital"] as? String,
               let capitalLat = properties["capitalLat"] as? Double,
               let capitalLon = properties["capitalLon"] as? Double {
                capital = (name: capitalName, lat: capitalLat, lon: capitalLon)
            }

            if type == "Point" {
                // Point country (small islands, microstates)
                if let coords = coordinates as? [Double], coords.count >= 2 {
                    let lon = coords[0]
                    let lat = coords[1]
                    let country = GeoJSONCountry(
                        name: name,
                        polygons: [],
                        color: GeoJSONCountry.landColor(),
                        continent: continent,
                        isPointCountry: true,
                        pointCoordinate: (lat: lat, lon: lon),
                        flagCode: flagCode,
                        capital: capital
                    )
                    countries.append(country)
                }
            } else {
                // Polygon or MultiPolygon country
                var polygons: [[[Double]]] = []

                if type == "Polygon" {
                    if let coords = coordinates as? [[[Double]]] {
                        // Take only the outer ring (first element)
                        if let outerRing = coords.first {
                            polygons.append(outerRing)
                        }
                    }
                } else if type == "MultiPolygon" {
                    if let multiCoords = coordinates as? [[[[Double]]]] {
                        for polygon in multiCoords {
                            if let outerRing = polygon.first {
                                polygons.append(outerRing)
                            }
                        }
                    }
                }

                if !polygons.isEmpty {
                    let country = GeoJSONCountry(
                        name: name,
                        polygons: polygons,
                        color: GeoJSONCountry.landColor(),
                        continent: continent,
                        isPointCountry: isPointCountry,
                        pointCoordinate: nil,
                        flagCode: flagCode,
                        capital: capital
                    )
                    countries.append(country)
                }
            }
        }

        return countries
    }
}
