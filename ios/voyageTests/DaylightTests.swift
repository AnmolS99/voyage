import XCTest
@testable import voyage

/// The Daylight theme's sun. Android's `DaylightTest` pins the same cases.
final class DaylightTests: XCTestCase {
    private func isNight(_ utc: String, _ zone: String) -> Bool {
        let now = ISO8601DateFormatter().date(from: utc)!
        return Daylight.isNight(at: now, in: TimeZone(identifier: zone)!)
    }

    func testNightFallsAtSunsetInTheTimeZonesCity() {
        // Oslo's sun sets at 15:12 local time on the winter solstice (14:12 UTC)
        // and at 22:44 on the summer one (20:44 UTC).
        XCTAssertFalse(isNight("2026-12-21T13:30:00Z", "Europe/Oslo"))
        XCTAssertTrue(isNight("2026-12-21T15:00:00Z", "Europe/Oslo"))
        XCTAssertFalse(isNight("2026-06-21T12:00:00Z", "Europe/Oslo"))
        XCTAssertTrue(isNight("2026-06-21T23:30:00Z", "Europe/Oslo"))
    }

    func testTheSameMomentIsDayInOneZoneAndNightInAnother() {
        XCTAssertFalse(isNight("2026-03-20T03:00:00Z", "Asia/Kolkata"))
        XCTAssertTrue(isNight("2026-03-20T03:00:00Z", "America/Los_Angeles"))
    }

    // Where re-selecting Home turns the globe to
    func testAZonesCityIsWhereTheUserIsTakenToBe() throws {
        let oslo = try XCTUnwrap(Daylight.location(of: TimeZone(identifier: "Europe/Oslo")!))
        XCTAssertEqual(oslo.latitude, 59.9, accuracy: 0.1)
        XCTAssertEqual(oslo.longitude, 10.75, accuracy: 0.1)
        XCTAssertNil(Daylight.location(of: TimeZone(identifier: "UTC")!))
    }

    func testPolarNightAndTheMidnightSunLastAllDay() {
        XCTAssertTrue(isNight("2026-12-21T11:00:00Z", "Arctic/Longyearbyen"))
        XCTAssertFalse(isNight("2026-06-21T23:00:00Z", "Arctic/Longyearbyen"))
    }

    func testSunriseAndSunsetAreTheMinutesTheThemeFlips() {
        let formatter = ISO8601DateFormatter()
        let noon = formatter.date(from: "2026-12-21T12:00:00Z")!

        let oslo = Daylight.sunTimes(on: noon, in: TimeZone(identifier: "Europe/Oslo")!)
        XCTAssertEqual(oslo.sunrise, formatter.date(from: "2026-12-21T08:19:00Z")) // 09:19 in Oslo
        XCTAssertEqual(oslo.sunset, formatter.date(from: "2026-12-21T14:12:00Z")) // 15:12 in Oslo

        let polarNight = Daylight.sunTimes(on: noon, in: TimeZone(identifier: "Arctic/Longyearbyen")!)
        XCTAssertNil(polarNight.sunrise)
        XCTAssertNil(polarNight.sunset)

        let noCity = Daylight.sunTimes(on: formatter.date(from: "2026-01-01T12:00:00Z")!, in: TimeZone(identifier: "UTC")!)
        XCTAssertEqual(noCity.sunrise, formatter.date(from: "2026-01-01T07:00:00Z"))
        XCTAssertEqual(noCity.sunset, formatter.date(from: "2026-01-01T19:00:00Z"))
    }

    func testAZonesCityIsNamedByItsIdentifier() {
        XCTAssertEqual(Daylight.cityName(of: TimeZone(identifier: "Europe/Oslo")!), "Oslo")
        XCTAssertEqual(Daylight.cityName(of: TimeZone(identifier: "America/New_York")!), "New York")
        XCTAssertNil(Daylight.cityName(of: TimeZone(identifier: "UTC")!))
    }

    func testAZonesOlderNameFindsTheSameCity() {
        XCTAssertFalse(Daylight.locations.isEmpty, "timezone_locations.json is bundled")
        XCTAssertEqual(Daylight.locations["Asia/Kolkata"], Daylight.locations["Asia/Calcutta"])
        XCTAssertTrue(Daylight.locations.values.allSatisfy { $0.count == 2 })
    }

    func testAZoneWithNoCityIsDarkFrom19To7OnItsClock() {
        XCTAssertFalse(isNight("2026-01-01T07:00:00Z", "UTC"))
        XCTAssertFalse(isNight("2026-01-01T18:59:00Z", "UTC"))
        XCTAssertTrue(isNight("2026-01-01T19:00:00Z", "UTC"))
        XCTAssertTrue(isNight("2026-01-01T06:59:00Z", "UTC"))
        let sixUTC = ISO8601DateFormatter().date(from: "2026-01-01T06:00:00Z")!
        XCTAssertFalse(Daylight.isNight(at: sixUTC, in: TimeZone(secondsFromGMT: 7200)!))
    }
}
