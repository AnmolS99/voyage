import Foundation
import UIKit

struct PointCountry {
    let name: String
    let lat: Double
    let lon: Double
    let flagCode: String
    let continent: String
}

class PointCountriesData {

    static let countries: [PointCountry] = [
        // European microstates
        PointCountry(name: "Andorra", lat: 42.5063, lon: 1.5218, flagCode: "AD", continent: "Europe"),
        PointCountry(name: "Cyprus", lat: 34.65, lon: 32.95, flagCode: "CY", continent: "Asia"),
        PointCountry(name: "Liechtenstein", lat: 47.1660, lon: 9.5554, flagCode: "LI", continent: "Europe"),
        PointCountry(name: "Luxembourg", lat: 49.8153, lon: 6.1296, flagCode: "LU", continent: "Europe"),
        PointCountry(name: "Malta", lat: 35.9375, lon: 14.3754, flagCode: "MT", continent: "Europe"),
        PointCountry(name: "Monaco", lat: 43.7384, lon: 7.4246, flagCode: "MC", continent: "Europe"),
        PointCountry(name: "Northern Cyprus", lat: 35.35, lon: 33.55, flagCode: "CY", continent: "Asia"),
        PointCountry(name: "San Marino", lat: 43.9424, lon: 12.4578, flagCode: "SM", continent: "Europe"),
        PointCountry(name: "Vatican City", lat: 41.9029, lon: 12.4534, flagCode: "VA", continent: "Europe"),

        // Caribbean
        PointCountry(name: "Antigua and Barbuda", lat: 17.0608, lon: -61.7964, flagCode: "AG", continent: "North America"),
        PointCountry(name: "Barbados", lat: 13.1939, lon: -59.5432, flagCode: "BB", continent: "North America"),
        PointCountry(name: "Dominica", lat: 15.4150, lon: -61.3710, flagCode: "DM", continent: "North America"),
        PointCountry(name: "Grenada", lat: 12.1165, lon: -61.6790, flagCode: "GD", continent: "North America"),
        PointCountry(name: "Saint Kitts and Nevis", lat: 17.3578, lon: -62.7830, flagCode: "KN", continent: "North America"),
        PointCountry(name: "Saint Lucia", lat: 13.9094, lon: -60.9789, flagCode: "LC", continent: "North America"),
        PointCountry(name: "Saint Vincent and the Grenadines", lat: 13.2528, lon: -61.1971, flagCode: "VC", continent: "North America"),

        // Pacific
        PointCountry(name: "Kiribati", lat: 1.8709, lon: 173.0176, flagCode: "KI", continent: "Oceania"),
        PointCountry(name: "Marshall Islands", lat: 7.1315, lon: 171.1845, flagCode: "MH", continent: "Oceania"),
        PointCountry(name: "Micronesia", lat: 6.9248, lon: 158.1610, flagCode: "FM", continent: "Oceania"),
        PointCountry(name: "Nauru", lat: -0.5228, lon: 166.9315, flagCode: "NR", continent: "Oceania"),
        PointCountry(name: "Palau", lat: 7.5150, lon: 134.5825, flagCode: "PW", continent: "Oceania"),
        PointCountry(name: "Samoa", lat: -13.7590, lon: -172.1046, flagCode: "WS", continent: "Oceania"),
        PointCountry(name: "Tonga", lat: -21.1790, lon: -175.1982, flagCode: "TO", continent: "Oceania"),
        PointCountry(name: "Tuvalu", lat: -7.1095, lon: 179.1940, flagCode: "TV", continent: "Oceania"),

        // Indian Ocean / Atlantic / Asia
        PointCountry(name: "Bahrain", lat: 26.0667, lon: 50.5577, flagCode: "BH", continent: "Asia"),
        PointCountry(name: "Cape Verde", lat: 14.9330, lon: -23.5133, flagCode: "CV", continent: "Africa"),
        PointCountry(name: "Comoros", lat: -11.6455, lon: 43.3333, flagCode: "KM", continent: "Africa"),
        PointCountry(name: "Maldives", lat: 3.2028, lon: 73.2207, flagCode: "MV", continent: "Asia"),
        PointCountry(name: "Mauritius", lat: -20.3484, lon: 57.5522, flagCode: "MU", continent: "Africa"),
        PointCountry(name: "Sao Tome and Principe", lat: 0.1864, lon: 6.6131, flagCode: "ST", continent: "Africa"),
        PointCountry(name: "Seychelles", lat: -4.6796, lon: 55.4920, flagCode: "SC", continent: "Africa"),
        PointCountry(name: "Singapore", lat: 1.3521, lon: 103.8198, flagCode: "SG", continent: "Asia"),
    ]

    static func getCountry(named name: String) -> PointCountry? {
        return countries.first { $0.name == name }
    }

    static func getAllNames() -> [String] {
        return countries.map { $0.name }
    }
}
