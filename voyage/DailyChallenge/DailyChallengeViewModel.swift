import Foundation

@MainActor
final class DailyChallengeViewModel: ObservableObject {

    enum ViewState: Equatable {
        case loading
        case error(String)
        case playing
        case completed
    }

    @Published var viewState: ViewState = .loading
    @Published var challenge: DailyChallenge?
    @Published var guesses: [String] = []
    @Published var attemptsUsed: Int = 0
    @Published var solved: Bool = false

    private let store = ChallengeStore.shared
    private let maxAttempts = 5

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    var answerCountry: GeoJSONCountry? {
        guard let isoCode = challenge?.answer else { return nil }
        return CountryDataCache.shared.countries.first { $0.flagCode == isoCode }
    }

    var remainingAttempts: Int {
        maxAttempts - attemptsUsed
    }

    func loadChallenge(for date: Date) async {
        let dateString = Self.dateFormatter.string(from: date)

        // Check for existing result (completed or in-progress)
        if let existing = store.getResult(for: dateString) {
            restoreResult(existing)
            return
        }

        viewState = .loading
        do {
            let fetched = try await SupabaseClient.fetchChallenge(for: date)
            challenge = fetched
            viewState = .playing
        } catch let error as DailyChallengeError {
            viewState = .error(error.localizedDescription)
        } catch {
            viewState = .error("Failed to load challenge")
        }
    }

    func submitGuess(_ guess: String) {
        guard viewState == .playing,
              attemptsUsed < maxAttempts,
              let country = answerCountry else { return }

        if guesses.contains(where: { $0.caseInsensitiveCompare(guess) == .orderedSame }) { return }

        attemptsUsed += 1
        guesses.append(guess)

        let isCorrect: Bool
        switch challenge?.questionType {
        case .capital:
            isCorrect = country.capital?.name.caseInsensitiveCompare(guess) == .orderedSame
        case .country, .flag:
            isCorrect = country.name.caseInsensitiveCompare(guess) == .orderedSame
        case nil:
            return
        }

        if isCorrect {
            solved = true
            viewState = .completed
            saveResult(completed: true)
        } else if attemptsUsed >= maxAttempts {
            viewState = .completed
            saveResult(completed: true)
        } else {
            saveResult(completed: false)
        }
    }

    var suggestions: [String] {
        guard let type = challenge?.questionType else { return [] }
        let countries = CountryDataCache.shared.countries
        switch type {
        case .country, .flag:
            return countries.compactMap { $0.name }.sorted()
        case .capital:
            return countries.compactMap { $0.capital?.name }.sorted()
        }
    }

    private func saveResult(completed: Bool) {
        guard let dateString = challenge?.date else { return }
        let today = Self.dateFormatter.string(from: Date())
        let result = ChallengeResult(
            date: dateString,
            attempts: attemptsUsed,
            solved: solved,
            completed: completed,
            completedOnChallengeDay: completed && dateString == today,
            guesses: guesses
        )
        store.saveResult(result)
    }

    private func restoreResult(_ result: ChallengeResult) {
        guesses = result.guesses
        attemptsUsed = result.attempts
        solved = result.solved

        // Still need the challenge data for display
        Task {
            let date = Self.dateFormatter.date(from: result.date) ?? Date()
            do {
                let fetched = try await SupabaseClient.fetchChallenge(for: date)
                challenge = fetched
            } catch {}
            viewState = result.completed ? .completed : .playing
        }
    }
}
