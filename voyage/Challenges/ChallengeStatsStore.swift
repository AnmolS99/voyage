import Foundation

/// Lifetime statistics for one game mode in one region.
struct ChallengeGameStats: Codable {
    /// Number of *completed* games: runs that went through every country in
    /// the region and reached the result screen. Best result/time only ever
    /// come from completed games. Shown in the UI as "Played".
    var gamesPlayed = 0
    /// Most countries ever answered correctly in one completed sweep.
    var bestCorrect: Int?
    /// Countries in the sweep the best result was set on (the country set can
    /// change between app versions, so best results compare as fractions).
    var bestTotal: Int?
    var bestTime: TimeInterval?
    var lastPlayed: Date?

    var bestFraction: Double? {
        guard let bestCorrect = bestCorrect, let bestTotal = bestTotal, bestTotal > 0 else { return nil }
        return Double(bestCorrect) / Double(bestTotal)
    }

    var bestPercentage: Int? {
        bestFraction.map { Int(($0 * 100).rounded()) }
    }

    /// True when the best result is a flawless sweep (every country correct).
    var isPerfect: Bool {
        bestFraction == 1.0
    }
}

/// Persists challenge game statistics locally (UserDefaults), keyed by mode + region.
final class ChallengeStatsStore: ObservableObject {
    static let shared = ChallengeStatsStore()

    private let userDefaults: UserDefaults
    private let storageKey = "challengeGameStats"

    @Published private var allStats: [String: ChallengeGameStats]

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        if let data = userDefaults.data(forKey: storageKey),
           let stats = try? JSONDecoder().decode([String: ChallengeGameStats].self, from: data) {
            allStats = stats
        } else {
            allStats = [:]
        }
    }

    func stats(for mode: ChallengeGameMode, region: ChallengeRegion) -> ChallengeGameStats {
        allStats[key(mode, region)] ?? ChallengeGameStats()
    }

    func totalGamesPlayed(for mode: ChallengeGameMode) -> Int {
        ChallengeRegion.allCases.reduce(0) { $0 + stats(for: mode, region: $1).gamesPlayed }
    }

    /// Number of trophies of the given tier earned across all game modes
    /// (one per flawless region sweep per mode).
    func trophyCount(_ trophy: ChallengeTrophy) -> Int {
        ChallengeGameMode.allCases.reduce(0) { total, mode in
            total + ChallengeRegion.allCases.filter {
                $0.trophy == trophy && stats(for: mode, region: $0).isPerfect
            }.count
        }
    }

    /// Records a completed game and returns true if it set a new best result
    /// (more countries correct as a fraction, or the same fraction in less time).
    @discardableResult
    func recordGame(mode: ChallengeGameMode, region: ChallengeRegion,
                    correct: Int, total: Int, time: TimeInterval) -> Bool {
        guard total > 0 else { return false }
        var stats = stats(for: mode, region: region)
        stats.gamesPlayed += 1
        stats.lastPlayed = Date()

        let fraction = Double(correct) / Double(total)
        let isNewBest: Bool
        if let bestFraction = stats.bestFraction, let bestTime = stats.bestTime {
            isNewBest = fraction > bestFraction || (fraction == bestFraction && time < bestTime)
        } else {
            isNewBest = true
        }
        if isNewBest {
            stats.bestCorrect = correct
            stats.bestTotal = total
            stats.bestTime = time
        }

        allStats[key(mode, region)] = stats
        save()
        return isNewBest
    }

    /// Clears all challenge statistics (Settings → Reset All Data).
    func resetAll() {
        allStats = [:]
        userDefaults.removeObject(forKey: storageKey)
    }

    private func key(_ mode: ChallengeGameMode, _ region: ChallengeRegion) -> String {
        "\(mode.rawValue)|\(region.rawValue)"
    }

    private func save() {
        if let data = try? JSONEncoder().encode(allStats) {
            userDefaults.set(data, forKey: storageKey)
        }
    }
}
