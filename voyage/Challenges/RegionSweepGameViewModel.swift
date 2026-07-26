import Foundation

/// Outcome of one player input in a region-sweep game.
enum SweepGuessOutcome {
    case marked      // Click the Country: first tap marked, awaiting confirmation
    case correct
    case reveal      // wrong guess: the answer is shown before moving on
    case ignored     // no-op input (already answered, empty, not playing)
}

/// Shared engine for region-sweep challenge games: every country in the
/// region appears once (shuffled), with a single guess each. A wrong guess
/// reveals the answer and moves on — the result of a sweep is simply how many
/// countries were answered correctly. The sweep is timed until the queue is
/// exhausted, and results are recorded in `ChallengeStatsStore` under the
/// subclass's game mode.
///
/// Subclasses translate their input (globe taps, typed capitals) into a
/// right/wrong judgement and pass it to `resolveGuess(correct:)`; typed-answer
/// games can use `submitGuess(_:)` directly by overriding `currentAnswer`.
class RegionSweepGameViewModel: ObservableObject {
    enum Phase {
        case playing
        case revealing   // wrong guess: the answer is being shown
        case finished
    }

    let mode: ChallengeGameMode
    let region: ChallengeRegion
    let totalCountries: Int

    @Published private(set) var currentTarget: String?
    /// Countries answered correctly — the score of the sweep.
    @Published private(set) var correctCount = 0
    /// Countries whose answer was revealed after a wrong guess, in sweep order.
    @Published private(set) var missedCountries: [String] = []
    @Published private(set) var elapsedTime: TimeInterval = 0
    @Published private(set) var phase: Phase = .playing
    @Published private(set) var isNewBest = false
    /// True when this game's flawless sweep earned the region's trophy for the
    /// first time (repeat 100% runs don't re-earn it).
    @Published private(set) var didEarnTrophy = false

    /// How far through the queue the sweep is.
    var answeredCount: Int { correctCount + missedCountries.count }

    private let statsStore: ChallengeStatsStore
    private var queue: [String]
    private var answered: Set<String> = []
    private var accumulatedTime: TimeInterval = 0
    private var segmentStart = Date()
    private var timer: Timer?

    /// `countries` overrides the region's country pool (used by tests and by
    /// subclasses that filter the pool).
    init(mode: ChallengeGameMode,
         region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared) {
        self.mode = mode
        self.region = region
        self.statsStore = statsStore
        self.queue = countries ?? region.countries.shuffled()
        self.totalCountries = queue.count
        self.currentTarget = queue.first
        if queue.isEmpty {
            phase = .finished
        } else {
            startTimer()
        }
    }

    deinit {
        timer?.invalidate()
    }

    // MARK: - Subclass interface

    /// The answer expected for the current target. Defaults to the country's
    /// own name (Name the Flag); Name the Capital overrides it.
    var currentAnswer: String? { currentTarget }

    /// Scores one typed guess against `currentAnswer`, ignoring casing and
    /// surrounding whitespace.
    func submitGuess(_ guess: String) -> SweepGuessOutcome {
        guard phase == .playing, let answer = currentAnswer else { return .ignored }

        let trimmed = guess.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .ignored }

        return resolveGuess(correct: answer.caseInsensitiveCompare(trimmed) == .orderedSame)
    }

    /// Scores one submitted guess against the current target. Subclasses call
    /// this after translating their input into a right/wrong judgement.
    func resolveGuess(correct: Bool) -> SweepGuessOutcome {
        guard phase == .playing, let target = currentTarget else { return .ignored }

        guard correct else {
            missedCountries.append(target)
            phase = .revealing
            return .reveal
        }

        correctCount += 1
        advance(past: target)
        return .correct
    }

    func isAnswered(_ country: String) -> Bool {
        answered.contains(country)
    }

    /// Subclass hook: called whenever the sweep moves on to a new target
    /// (after an answer, a reveal, or a restart). Clears per-target input state.
    func targetDidChange() {}

    /// The country pool a restarted sweep draws from. Subclasses that filter
    /// the pool (e.g. to countries with capital data) override this.
    func freshQueue() -> [String] {
        region.countries.shuffled()
    }

    // MARK: - Game lifecycle

    /// Called by the view once the reveal animation has finished.
    func finishReveal() {
        guard phase == .revealing, let target = currentTarget else { return }
        phase = .playing
        advance(past: target)
    }

    /// Restarts the sweep from scratch with a fresh shuffle.
    func restart() {
        queue = freshQueue()
        answered = []
        missedCountries = []
        correctCount = 0
        currentTarget = queue.first
        accumulatedTime = 0
        elapsedTime = 0
        isNewBest = false
        didEarnTrophy = false
        phase = queue.isEmpty ? .finished : .playing
        targetDidChange()
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

    // MARK: - Internals

    private func advance(past target: String) {
        answered.insert(target)
        queue.removeFirst()
        currentTarget = queue.first
        targetDidChange()
        if currentTarget == nil {
            finishGame()
        }
    }

    private func finishGame() {
        phase = .finished
        elapsedTime = accumulatedTime + Date().timeIntervalSince(segmentStart)
        stopTimer()
        let hadTrophy = statsStore.stats(for: mode, region: region).isPerfect
        isNewBest = statsStore.recordGame(
            mode: mode,
            region: region,
            correct: correctCount,
            total: totalCountries,
            time: elapsedTime
        )
        didEarnTrophy = !hadTrophy && correctCount == totalCountries
    }

    private func startTimer() {
        timer?.invalidate()
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
