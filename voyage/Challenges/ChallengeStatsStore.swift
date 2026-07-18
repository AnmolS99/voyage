import Foundation

/// Lifetime statistics for one game mode in one region.
struct ChallengeGameStats: Codable {
    /// Number of *completed* games: runs that went through every country in
    /// the region and reached the result screen. Best score/time only ever
    /// come from completed games. Shown in the UI as "Played".
    var gamesPlayed = 0
    /// Number of *attempts*: runs in which the user made at least one guess
    /// (a tap resolved as correct or wrong). Counted at the first guess, so
    /// restarting or quitting mid-run still counts; opening a game and
    /// leaving without guessing does not. Every completed game is also an
    /// attempt, so `attempts >= gamesPlayed`. Stored to measure effort more
    /// accurately in the future; deliberately not shown in the UI yet.
    var attempts = 0
    var bestScore: Int?
    /// Max possible points when the best score was set (the country set can
    /// change between app versions, so best results compare as fractions).
    var bestScoreMax: Int?
    var bestScoreTime: TimeInterval?
    var lastPlayed: Date?

    init() {}

    /// Decodes with per-field defaults so stats saved by older app versions
    /// (missing newer fields like `attempts`) load instead of being dropped.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        gamesPlayed = try container.decodeIfPresent(Int.self, forKey: .gamesPlayed) ?? 0
        attempts = try container.decodeIfPresent(Int.self, forKey: .attempts) ?? 0
        bestScore = try container.decodeIfPresent(Int.self, forKey: .bestScore)
        bestScoreMax = try container.decodeIfPresent(Int.self, forKey: .bestScoreMax)
        bestScoreTime = try container.decodeIfPresent(TimeInterval.self, forKey: .bestScoreTime)
        lastPlayed = try container.decodeIfPresent(Date.self, forKey: .lastPlayed)
    }

    var bestFraction: Double? {
        guard let bestScore = bestScore, let bestScoreMax = bestScoreMax, bestScoreMax > 0 else { return nil }
        return Double(bestScore) / Double(bestScoreMax)
    }

    var bestPercentage: Int? {
        bestFraction.map { Int(($0 * 100).rounded()) }
    }

    /// True when the best result is a flawless sweep (full points).
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

    /// Records the start of an attempt (the first guess of a run). See
    /// `ChallengeGameStats.attempts` for what counts as an attempt.
    func recordAttempt(mode: ChallengeGameMode, region: ChallengeRegion) {
        var stats = stats(for: mode, region: region)
        stats.attempts += 1
        allStats[key(mode, region)] = stats
        save()
    }

    /// Records a completed game and returns true if it set a new best result
    /// (higher score fraction, or the same fraction in less time).
    @discardableResult
    func recordGame(mode: ChallengeGameMode, region: ChallengeRegion,
                    score: Int, maxScore: Int, time: TimeInterval) -> Bool {
        guard maxScore > 0 else { return false }
        var stats = stats(for: mode, region: region)
        stats.gamesPlayed += 1
        stats.lastPlayed = Date()

        let fraction = Double(score) / Double(maxScore)
        let isNewBest: Bool
        if let bestFraction = stats.bestFraction, let bestTime = stats.bestScoreTime {
            isNewBest = fraction > bestFraction || (fraction == bestFraction && time < bestTime)
        } else {
            isNewBest = true
        }
        if isNewBest {
            stats.bestScore = score
            stats.bestScoreMax = maxScore
            stats.bestScoreTime = time
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
