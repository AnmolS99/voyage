import Foundation
import UIKit

struct GeoJSONCountry {
    let name: String
    let polygons: [[[Double]]] // Array of polygons, each polygon is array of [lon, lat] coordinates
    let color: UIColor

    static func randomLandColor() -> UIColor {
        let colors: [UIColor] = [
            UIColor(red: 0.30, green: 0.60, blue: 0.35, alpha: 1.0),
            UIColor(red: 0.35, green: 0.55, blue: 0.30, alpha: 1.0),
            UIColor(red: 0.40, green: 0.65, blue: 0.40, alpha: 1.0),
            UIColor(red: 0.45, green: 0.58, blue: 0.38, alpha: 1.0),
            UIColor(red: 0.38, green: 0.52, blue: 0.32, alpha: 1.0),
        ]
        return colors.randomElement()!
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
                    color: GeoJSONCountry.randomLandColor()
                )
                countries.append(country)
            }
        }

        return countries
    }
}
