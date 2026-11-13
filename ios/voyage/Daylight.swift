import Foundation

/// Whether the sun is down, for `ThemeMode.daylight`. Android's `Daylight` is a
/// port of this, pinned to the same cases by `DaylightTests` and `DaylightTest`.
///
/// The app asks for no location, so "here" is the reference city of the device's
/// time zone (`timezone_locations.json`, shared with Android) — right to within
/// the zone's width, and it follows the user when they travel.
enum Daylight {
    /// Latitude and longitude of each time zone's city, keyed by zone identifier.
    static let locations: [String: [Double]] = {
        guard let url = Bundle.main.url(forResource: "timezone_locations", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let locations = try? JSONDecoder().decode([String: [Double]].self, from: data) else {
            return [:]
        }
        return locations
    }()

    /// A zone with no city (`UTC`, `GMT+2`) falls back to 07:00–19:00 on its clock.
    static func isNight(at now: Date = Date(), in timeZone: TimeZone = .current) -> Bool {
        guard let location = locations[timeZone.identifier], location.count == 2 else {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = timeZone
            return !(7...18).contains(calendar.component(.hour, from: now))
        }
        return isNight(at: now, latitude: location[0], longitude: location[1])
    }

    /// Whether the sun is below the horizon, from the Astronomical Almanac's
    /// low-precision formulas (good to a minute or two of sunrise). Asking for
    /// the sun's height rather than for sunrise and sunset times keeps polar
    /// night and the midnight sun from being special cases.
    static func isNight(at now: Date, latitude: Double, longitude: Double) -> Bool {
        func radians(_ degrees: Double) -> Double { degrees * .pi / 180 }
        let days = now.timeIntervalSince1970 / 86_400 - 10_957.5 // since noon on 1 January 2000
        let anomaly = radians(357.528 + 0.9856003 * days)
        let sunLongitude = radians(280.460 + 0.9856474 * days + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly))
        let tilt = radians(23.439)
        let declination = asin(sin(tilt) * sin(sunLongitude))
        let rightAscension = atan2(cos(tilt) * sin(sunLongitude), cos(sunLongitude))
        let hourAngle = radians(280.46061837 + 360.98564736629 * days + longitude) - rightAscension
        let lat = radians(latitude)
        let height = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        return height < sin(radians(sunriseHeight))
    }

    /// The sun's centre at sunrise: its radius plus refraction below the horizon.
    private static let sunriseHeight = -0.833
}
