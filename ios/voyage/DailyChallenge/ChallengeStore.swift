import Foundation

/// Persists daily challenge results to both UserDefaults (local) and the
/// iCloud key-value store, keyed by date — the same dual-store pattern as
/// `GlobeState` — so results (and therefore streaks) follow the user across
/// devices.
///
/// On load and whenever iCloud reports an external change, the two
/// dictionaries are merged per date and the merge is saved back to both
/// stores. Merge rules for the same date:
/// 1. A date only one device has played is simply kept.
/// 2. A completed result beats an in-progress one.
/// 3. Between two completed results, a solved one beats a failed one.
/// 4. Otherwise the result that is further along (more guesses) wins.
final class ChallengeStore: ObservableObject {
    static let shared = ChallengeStore()

    private let userDefaults = UserDefaults.standard
    private let iCloudStore = NSUbiquitousKeyValueStore.default
    private let storageKey = "dailyChallengeResults"

    @Published private(set) var results: [String: ChallengeResult] = [:]

    private init() {
        loadAndMergeStores()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(iCloudDidChange),
            name: NSUbiquitousKeyValueStore.didChangeExternallyNotification,
            object: iCloudStore
        )

        iCloudStore.synchronize()
    }

    func saveResult(_ result: ChallengeResult) {
        results[result.date] = result
        persist()
    }

    func getResult(for date: String) -> ChallengeResult? {
        results[date]
    }

    func allResults() -> [String: ChallengeResult] {
        results
    }

    var currentStreak: Int {
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

    // MARK: - Merging

    /// Merges two result dictionaries per date (see the type doc for rules).
    static func mergeResults(_ base: [String: ChallengeResult],
                             _ other: [String: ChallengeResult]) -> [String: ChallengeResult] {
        var merged = base
        for (date, result) in other {
            if let existing = merged[date] {
                merged[date] = betterResult(existing, result)
            } else {
                merged[date] = result
            }
        }
        return merged
    }

    /// Picks the more authoritative of two results for the same date:
    /// completed beats in-progress, solved beats failed, then the result
    /// that is further along (more guesses) wins; ties keep the first.
    static func betterResult(_ first: ChallengeResult, _ second: ChallengeResult) -> ChallengeResult {
        if first.completed != second.completed {
            return first.completed ? first : second
        }
        if first.completed && first.solved != second.solved {
            return first.solved ? first : second
        }
        return second.guesses.count > first.guesses.count ? second : first
    }

    // MARK: - Persistence

    private func loadAndMergeStores() {
        let local = decodeResults(userDefaults.data(forKey: storageKey))
        let cloud = decodeResults(iCloudStore.data(forKey: storageKey))
        results = Self.mergeResults(local, cloud)

        // Save the merge back so both stores converge
        if results != local || results != cloud {
            persist()
        }
    }

    private func decodeResults(_ data: Data?) -> [String: ChallengeResult] {
        guard let data = data,
              let decoded = try? JSONDecoder().decode([String: ChallengeResult].self, from: data) else {
            return [:]
        }
        return decoded
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(results) else { return }
        userDefaults.set(data, forKey: storageKey)
        iCloudStore.set(data, forKey: storageKey)
        iCloudStore.synchronize()
    }

    @objc private func iCloudDidChange(_ notification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.loadAndMergeStores()
        }
    }
}
