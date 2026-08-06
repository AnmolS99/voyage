import XCTest
@testable import voyage

extension Achievement {
    /// Test factory: builds an Achievement whose derived counts equal the given values.
    /// Achievement derives `current`/`total` from its country lists, so the counts are
    /// expressed as generated country names.
    static func forTesting(name: String = "Test", medal: String = "🏆", current: Int, total: Int) -> Achievement {
        Achievement(
            name: name,
            medal: medal,
            visitedCountries: (0..<current).map { "Visited \($0)" },
            remainingCountries: (0..<(total - current)).map { "Remaining \($0)" }
        )
    }
}

final class AchievementTests: XCTestCase {

    private func makeAchievement(name: String = "Test", medal: String = "🏆", current: Int, total: Int) -> Achievement {
        .forTesting(name: name, medal: medal, current: current, total: total)
    }

    // MARK: - isCompleted Tests

    func testAchievementIsCompletedWhenCurrentEqualsTotal() {
        let achievement = makeAchievement(current: 10, total: 10)
        XCTAssertTrue(achievement.isCompleted, "Achievement with 10/10 should be completed")
    }

    func testAchievementIsNotCompletedWhenCurrentLessThanTotal() {
        let achievement = makeAchievement(current: 5, total: 10)
        XCTAssertFalse(achievement.isCompleted, "Achievement with 5/10 should not be completed")
    }

    func testAchievementIsNotCompletedWithOneRemaining() {
        let achievement = makeAchievement(current: 9, total: 10)
        XCTAssertFalse(achievement.isCompleted, "Achievement with 9/10 should not be completed")
    }

    func testAchievementIsCompletedWithZeroTotal() {
        let achievement = makeAchievement(current: 0, total: 0)
        XCTAssertTrue(achievement.isCompleted, "Achievement with 0/0 should be completed")
    }

    // MARK: - Progress Tests

    func testProgressAtHalf() {
        let achievement = makeAchievement(current: 5, total: 10)
        XCTAssertEqual(achievement.progress, 0.5, accuracy: 0.001, "5/10 should be 0.5 progress")
    }

    func testProgressAtQuarter() {
        let achievement = makeAchievement(current: 3, total: 12)
        XCTAssertEqual(achievement.progress, 0.25, accuracy: 0.001, "3/12 should be 0.25 progress")
    }

    func testProgressAtFull() {
        let achievement = makeAchievement(current: 10, total: 10)
        XCTAssertEqual(achievement.progress, 1.0, accuracy: 0.001, "10/10 should be 1.0 progress")
    }

    func testProgressAtEmpty() {
        let achievement = makeAchievement(current: 0, total: 10)
        XCTAssertEqual(achievement.progress, 0.0, accuracy: 0.001, "0/10 should be 0.0 progress")
    }

    func testProgressWithZeroTotal() {
        let achievement = makeAchievement(current: 0, total: 0)
        XCTAssertEqual(achievement.progress, 0.0, accuracy: 0.001, "0/0 should be 0.0 progress")
    }

    // MARK: - Percentage Tests

    func testPercentageAtHalf() {
        let achievement = makeAchievement(current: 5, total: 10)
        XCTAssertEqual(achievement.percentage, 50, "5/10 should be 50%")
    }

    func testPercentageAtOneThird() {
        let achievement = makeAchievement(current: 1, total: 3)
        XCTAssertEqual(achievement.percentage, 33, "1/3 should be 33%")
    }

    func testPercentageAtFull() {
        let achievement = makeAchievement(current: 10, total: 10)
        XCTAssertEqual(achievement.percentage, 100, "10/10 should be 100%")
    }

    func testPercentageAtEmpty() {
        let achievement = makeAchievement(current: 0, total: 10)
        XCTAssertEqual(achievement.percentage, 0, "0/10 should be 0%")
    }

    // MARK: - Achievement Identity Tests

    func testAchievementIdIsItsName() {
        let achievement = makeAchievement(name: "Explorer of Europe", current: 5, total: 10)
        XCTAssertEqual(achievement.id, "Explorer of Europe", "Achievement id should be its name")
    }

    func testAchievementStoresNameAndMedal() {
        let achievement = makeAchievement(name: "Explorer of Europe", medal: "🏰", current: 5, total: 44)
        XCTAssertEqual(achievement.name, "Explorer of Europe")
        XCTAssertEqual(achievement.medal, "🏰")
        XCTAssertEqual(achievement.current, 5)
        XCTAssertEqual(achievement.total, 44)
    }

    func testAchievementDerivesCountsFromCountryLists() {
        let achievement = Achievement(
            name: "Test",
            medal: "🏆",
            visitedCountries: ["Norway", "France"],
            remainingCountries: ["Japan", "Brazil", "Kenya"]
        )
        XCTAssertEqual(achievement.current, 2)
        XCTAssertEqual(achievement.total, 5)
        XCTAssertFalse(achievement.isCompleted)
    }
}
