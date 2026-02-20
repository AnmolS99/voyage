import Foundation

final class ChallengeStore {
    static let shared = ChallengeStore()

    private let userDefaults = UserDefaults.standard
    private let storageKey = "dailyChallengeResults"

    private init() {}

    func saveResult(_ result: ChallengeResult) {
        var all = allResults()
        all[result.date] = result
        if let data = try? JSONEncoder().encode(all) {
            userDefaults.set(data, forKey: storageKey)
        }
    }

    func getResult(for date: String) -> ChallengeResult? {
        allResults()[date]
    }

    func allResults() -> [String: ChallengeResult] {
        guard let data = userDefaults.data(forKey: storageKey),
              let results = try? JSONDecoder().decode([String: ChallengeResult].self, from: data) else {
            return [:]
        }
        return results
    }

    var currentStreak: Int {
        let results = allResults()
        let calendar = Calendar.current
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        var streak = 0
        var checkDate = Date()

        let todayString = dateFormatter.string(from: checkDate)
        if let todayResult = results[todayString], todayResult.completed {
            if todayResult.solved && todayResult.completedOnChallengeDay {
                streak = 1
            } else {
                return 0
            }
            checkDate = calendar.date(byAdding: .day, value: -1, to: checkDate)!
        } else {
            checkDate = calendar.date(byAdding: .day, value: -1, to: checkDate)!
        }

        while true {
            let dateString = dateFormatter.string(from: checkDate)
            guard let result = results[dateString], result.completed, result.solved, result.completedOnChallengeDay else { break }
            streak += 1
            checkDate = calendar.date(byAdding: .day, value: -1, to: checkDate)!
        }

        return streak
    }
}
