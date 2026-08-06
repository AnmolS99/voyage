import XCTest
@testable import voyage

/// Tests the pure merge rules ChallengeStore uses to reconcile daily
/// challenge results between the local and iCloud stores.
final class ChallengeStoreMergeTests: XCTestCase {
    private func result(date: String = "2026-07-18",
                        solved: Bool,
                        completed: Bool,
                        guesses: [String]) -> ChallengeResult {
        ChallengeResult(
            date: date,
            attempts: guesses.count,
            solved: solved,
            completed: completed,
            completedOnChallengeDay: completed,
            guesses: guesses
        )
    }

    func testMergeUnionsDisjointDates() {
        let monday = result(date: "2026-07-13", solved: true, completed: true, guesses: ["France"])
        let tuesday = result(date: "2026-07-14", solved: false, completed: true, guesses: ["A", "B", "C", "D", "E"])

        let merged = ChallengeStore.mergeResults(["2026-07-13": monday], ["2026-07-14": tuesday])

        XCTAssertEqual(merged.count, 2)
        XCTAssertEqual(merged["2026-07-13"], monday)
        XCTAssertEqual(merged["2026-07-14"], tuesday)
    }

    func testCompletedBeatsInProgress() {
        let inProgress = result(solved: false, completed: false, guesses: ["Spain", "Italy"])
        let completed = result(solved: true, completed: true, guesses: ["France"])

        XCTAssertEqual(ChallengeStore.betterResult(inProgress, completed), completed)
        XCTAssertEqual(ChallengeStore.betterResult(completed, inProgress), completed)
    }

    func testSolvedBeatsFailedWhenBothCompleted() {
        let failed = result(solved: false, completed: true, guesses: ["A", "B", "C", "D", "E"])
        let solvedResult = result(solved: true, completed: true, guesses: ["France"])

        XCTAssertEqual(ChallengeStore.betterResult(failed, solvedResult), solvedResult)
        XCTAssertEqual(ChallengeStore.betterResult(solvedResult, failed), solvedResult)
    }

    func testFurtherAlongInProgressWins() {
        let oneGuess = result(solved: false, completed: false, guesses: ["Spain"])
        let threeGuesses = result(solved: false, completed: false, guesses: ["Spain", "Italy", "Portugal"])

        XCTAssertEqual(ChallengeStore.betterResult(oneGuess, threeGuesses), threeGuesses)
        XCTAssertEqual(ChallengeStore.betterResult(threeGuesses, oneGuess), threeGuesses)
    }

    func testTieKeepsFirstResult() {
        let local = result(solved: false, completed: false, guesses: ["Spain"])
        let cloud = result(solved: false, completed: false, guesses: ["Italy"])

        XCTAssertEqual(ChallengeStore.betterResult(local, cloud), local)
    }

    func testMergeAppliesRulesPerDate() {
        let local = [
            "2026-07-13": result(date: "2026-07-13", solved: false, completed: false, guesses: ["Spain"]),
            "2026-07-14": result(date: "2026-07-14", solved: true, completed: true, guesses: ["France"]),
        ]
        let cloud = [
            "2026-07-13": result(date: "2026-07-13", solved: true, completed: true, guesses: ["Norway", "Peru"]),
            "2026-07-15": result(date: "2026-07-15", solved: false, completed: true, guesses: ["A", "B", "C", "D", "E"]),
        ]

        let merged = ChallengeStore.mergeResults(local, cloud)

        XCTAssertEqual(merged.count, 3)
        XCTAssertEqual(merged["2026-07-13"], cloud["2026-07-13"], "Cloud's completed result should beat local in-progress")
        XCTAssertEqual(merged["2026-07-14"], local["2026-07-14"])
        XCTAssertEqual(merged["2026-07-15"], cloud["2026-07-15"])
    }
}
