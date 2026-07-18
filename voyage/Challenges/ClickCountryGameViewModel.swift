import Foundation

/// Drives one "Click the Country" sweep: every country in the region appears
/// once (shuffled), with three graded tries each (3/2/1 points). After the
/// third miss the country is revealed for 0 points. The sweep is timed until
/// the queue is exhausted.
final class ClickCountryGameViewModel: ObservableObject {
    enum Phase {
        case playing
        case revealing   // third miss: the target is being shown on the globe
        case finished
    }

    enum TapOutcome {
        case correct(points: Int)
        case wrong(remainingTries: Int)
        case reveal
        case ignored     // already-solved country, or not currently playing
    }

    static let maxPointsPerCountry = 3

    let region: ChallengeRegion
    let totalCountries: Int
    var maxScore: Int { totalCountries * Self.maxPointsPerCountry }

    @Published private(set) var currentTarget: String?
    @Published private(set) var solvedCount = 0
    @Published private(set) var triesLeft: Int
    @Published private(set) var score = 0
    @Published private(set) var elapsedTime: TimeInterval = 0
    @Published private(set) var phase: Phase = .playing
    @Published private(set) var isNewBest = false
    /// True when this game's flawless sweep earned the region's trophy for the
    /// first time (repeat 100% runs don't re-earn it).
    @Published private(set) var didEarnTrophy = false

    /// Countries found on the first try.
    private(set) var perfectCount = 0
    /// Countries revealed after three misses (0 points), in sweep order.
    private(set) var missedCountries: [String] = []

    private let statsStore: ChallengeStatsStore
    private var queue: [String]
    private var solved: Set<String> = []
    /// Whether this run has been counted as an attempt (set on the first guess).
    private var hasRecordedAttempt = false
    private var accumulatedTime: TimeInterval = 0
    private var segmentStart = Date()
    private var timer: Timer?

    /// `countries` overrides the region's country pool (used by tests).
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared) {
        self.region = region
        self.statsStore = statsStore
        self.queue = countries ?? region.countries.shuffled()
        self.totalCountries = queue.count
        self.currentTarget = queue.first
        self.triesLeft = Self.maxPointsPerCountry
        if queue.isEmpty {
            phase = .finished
        } else {
            startTimer()
        }
    }

    deinit {
        timer?.invalidate()
    }

    func handleTap(on country: String) -> TapOutcome {
        guard phase == .playing, let target = currentTarget else { return .ignored }
        guard !solved.contains(country) else { return .ignored }

        registerAttemptIfNeeded()

        if country == target {
            let points = triesLeft
            score += points
            if points == Self.maxPointsPerCountry {
                perfectCount += 1
            }
            advance(past: target)
            return .correct(points: points)
        }

        triesLeft -= 1
        if triesLeft <= 0 {
            missedCountries.append(target)
            phase = .revealing
            return .reveal
        }
        return .wrong(remainingTries: triesLeft)
    }

    /// Called by the view once the reveal animation has finished.
    func finishReveal() {
        guard phase == .revealing, let target = currentTarget else { return }
        phase = .playing
        advance(past: target)
    }

    /// Restarts the sweep from scratch with a fresh shuffle.
    func restart() {
        queue = region.countries.shuffled()
        solved = []
        missedCountries = []
        perfectCount = 0
        solvedCount = 0
        score = 0
        currentTarget = queue.first
        triesLeft = Self.maxPointsPerCountry
        accumulatedTime = 0
        elapsedTime = 0
        isNewBest = false
        didEarnTrophy = false
        hasRecordedAttempt = false
        phase = queue.isEmpty ? .finished : .playing
        if phase == .playing {
            startTimer()
        }
    }

    /// Pauses the clock (e.g. app went to background).
    func pause() {
        guard timer != nil else { return }
        accumulatedTime += Date().timeIntervalSince(segmentStart)
        stopTimer()
    }

    func resume() {
        guard phase != .finished, timer == nil else { return }
        startTimer()
    }

    /// Counts this run as an attempt on its first guess, so abandoned and
    /// restarted runs still contribute to the (stored, not yet shown)
    /// attempts statistic.
    private func registerAttemptIfNeeded() {
        guard !hasRecordedAttempt else { return }
        hasRecordedAttempt = true
        statsStore.recordAttempt(mode: .clickCountry, region: region)
    }

    private func advance(past target: String) {
        solved.insert(target)
        solvedCount += 1
        queue.removeFirst()
        currentTarget = queue.first
        triesLeft = Self.maxPointsPerCountry
        if currentTarget == nil {
            finishGame()
        }
    }

    private func finishGame() {
        phase = .finished
        elapsedTime = accumulatedTime + Date().timeIntervalSince(segmentStart)
        stopTimer()
        let hadTrophy = statsStore.stats(for: .clickCountry, region: region).isPerfect
        isNewBest = statsStore.recordGame(
            mode: .clickCountry,
            region: region,
            score: score,
            maxScore: maxScore,
            time: elapsedTime
        )
        didEarnTrophy = !hadTrophy && score == maxScore
    }

    private func startTimer() {
        segmentStart = Date()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.elapsedTime = self.accumulatedTime + Date().timeIntervalSince(self.segmentStart)
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
}
