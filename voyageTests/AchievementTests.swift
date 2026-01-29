import XCTest
@testable import voyage

final class AchievementTests: XCTestCase {

    // MARK: - isCompleted Tests

    func testAchievementIsCompletedWhenCurrentEqualsTotal() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 10, total: 10)
        XCTAssertTrue(achievement.isCompleted, "Achievement with 10/10 should be completed")
    }

    func testAchievementIsNotCompletedWhenCurrentLessThanTotal() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 5, total: 10)
        XCTAssertFalse(achievement.isCompleted, "Achievement with 5/10 should not be completed")
    }

    func testAchievementIsCompletedWhenCurrentExceedsTotal() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 15, total: 10)
        XCTAssertTrue(achievement.isCompleted, "Achievement with 15/10 should be completed")
    }

    func testAchievementIsCompletedWithZeroTotal() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 0, total: 0)
        XCTAssertTrue(achievement.isCompleted, "Achievement with 0/0 should be completed")
    }

    // MARK: - Progress Tests

    func testProgressAtHalf() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 5, total: 10)
        XCTAssertEqual(achievement.progress, 0.5, accuracy: 0.001, "5/10 should be 0.5 progress")
    }

    func testProgressAtQuarter() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 3, total: 12)
        XCTAssertEqual(achievement.progress, 0.25, accuracy: 0.001, "3/12 should be 0.25 progress")
    }

    func testProgressAtFull() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 10, total: 10)
        XCTAssertEqual(achievement.progress, 1.0, accuracy: 0.001, "10/10 should be 1.0 progress")
    }

    func testProgressAtEmpty() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 0, total: 10)
        XCTAssertEqual(achievement.progress, 0.0, accuracy: 0.001, "0/10 should be 0.0 progress")
    }

    func testProgressWithZeroTotal() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 0, total: 0)
        XCTAssertEqual(achievement.progress, 0.0, accuracy: 0.001, "0/0 should be 0.0 progress")
    }

    // MARK: - Percentage Tests

    func testPercentageAtHalf() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 5, total: 10)
        XCTAssertEqual(achievement.percentage, 50, "5/10 should be 50%")
    }

    func testPercentageAtOneThird() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 1, total: 3)
        XCTAssertEqual(achievement.percentage, 33, "1/3 should be 33%")
    }

    func testPercentageAtFull() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 10, total: 10)
        XCTAssertEqual(achievement.percentage, 100, "10/10 should be 100%")
    }

    func testPercentageAtEmpty() {
        let achievement = Achievement(name: "Test", medal: "🏆", current: 0, total: 10)
        XCTAssertEqual(achievement.percentage, 0, "0/10 should be 0%")
    }

    // MARK: - Achievement Identity Tests

    func testAchievementHasUniqueId() {
        let achievement1 = Achievement(name: "Test", medal: "🏆", current: 5, total: 10)
        let achievement2 = Achievement(name: "Test", medal: "🏆", current: 5, total: 10)
        XCTAssertNotEqual(achievement1.id, achievement2.id, "Each achievement should have a unique ID")
    }

    func testAchievementStoresNameAndMedal() {
        let achievement = Achievement(name: "Explorer of Europe", medal: "🏰", current: 5, total: 44)
        XCTAssertEqual(achievement.name, "Explorer of Europe")
        XCTAssertEqual(achievement.medal, "🏰")
        XCTAssertEqual(achievement.current, 5)
        XCTAssertEqual(achievement.total, 44)
    }
}
