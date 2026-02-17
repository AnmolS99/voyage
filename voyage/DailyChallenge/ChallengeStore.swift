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
}
