import Foundation
import UIKit

struct GeoJSONCountry {
    let name: String
    let polygons: [[[Double]]] // Array of polygons, each polygon is array of [lon, lat] coordinates
    let color: UIColor

    static func landColor() -> UIColor {
        // Green #34BE82
        return UIColor(red: 0.204, green: 0.745, blue: 0.510, alpha: 1.0)
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

        // Get names of countries rendered as points (circles)
        let pointCountryNames = Set(PointCountriesData.getAllNames())

        var countries: [GeoJSONCountry] = []

        for feature in features {
            guard let properties = feature["properties"] as? [String: Any],
                  let name = properties["name"] as? String ?? properties["NAME"] as? String,
                  let geometry = feature["geometry"] as? [String: Any],
                  let type = geometry["type"] as? String,
                  let coordinates = geometry["coordinates"] else {
                continue
            }

            // Skip countries that are rendered as point markers
            if pointCountryNames.contains(name) {
                continue
            }

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
                    color: GeoJSONCountry.landColor()
                )
                countries.append(country)
            }
        }

        return countries
    }
}
