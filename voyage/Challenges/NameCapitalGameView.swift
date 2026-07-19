import SwiftUI
import UIKit

/// Full-screen "Name the Capital" game. Each country in the region is shown
/// on the prompt card (flag + name) while the globe flies to and outlines it;
/// the player types its capital into a search field. Solved countries stay
/// green, revealed misses stay red — like Click the Country, the game runs on
/// its own in-memory `GlobeState`, so nothing touches the user's travel data.
struct NameCapitalGameView: View {
    let region: ChallengeRegion
    let onDismiss: () -> Void

    @StateObject private var viewModel: NameCapitalGameViewModel
    @StateObject private var gameGlobe: GlobeState
    @State private var searchText = ""
    @State private var feedback: Feedback?
    @State private var feedbackToken = 0
    @State private var showQuitConfirmation = false
    @State private var showRestartConfirmation = false
    @Environment(\.scenePhase) private var scenePhase

    private let isDarkMode: Bool
    /// Every capital in the dataset — the search field's suggestion pool, so
    /// the answer never stands out by being the only nearby suggestion.
    private let capitalSuggestions: [String]
    private static let revealDuration: TimeInterval = 2.6

    init(region: ChallengeRegion, mainState: GlobeState, onDismiss: @escaping () -> Void) {
        self.region = region
        self.onDismiss = onDismiss
        self.isDarkMode = mainState.isDarkMode
        _viewModel = StateObject(wrappedValue: NameCapitalGameViewModel(region: region))

        let globe = GlobeState(inMemory: true)
        globe.globeStyle = mainState.globeStyle
        globe.isDarkMode = mainState.isDarkMode
        globe.isAutoRotating = false
        globe.showsCapitalMarker = false
        _gameGlobe = StateObject(wrappedValue: globe)

        capitalSuggestions = Set(CountryDataCache.shared.countries.compactMap { $0.capital?.name }).sorted()
    }

    private enum Feedback: Equatable {
        case correct(capital: String, points: Int)
        case wrong(guess: String, remainingTries: Int)
        case reveal(country: String, capital: String)
    }

    var body: some View {
        ZStack {
            GlobeBackdrop(isDarkMode: isDarkMode)

            // The globe is a backdrop showing the asked country — taps on it
            // are not part of this game
            GlobeView(globeState: gameGlobe, onCountryTapped: { _ in })
                .ignoresSafeArea()

            VStack(spacing: 12) {
                SweepTopBar(viewModel: viewModel, isDarkMode: isDarkMode) {
                    showQuitConfirmation = true
                }
                SweepPromptCard(
                    viewModel: viewModel,
                    flagProvider: gameGlobe.flagForCountry,
                    question: "What's the capital?",
                    isDarkMode: isDarkMode
                )
                Spacer()
                if let feedback = feedback {
                    feedbackBanner(feedback)
                }
                bottomBar
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 12)

            if viewModel.phase == .finished {
                SweepResultOverlay(
                    viewModel: viewModel,
                    isDarkMode: isDarkMode,
                    firstTryLabel: "First-try answers",
                    missedSummary: missedSummary,
                    onPlayAgain: playAgain,
                    onDismiss: onDismiss
                )
            }
        }
        .onAppear {
            focusOnCurrentTarget(distance: region.cameraTarget.distance)
        }
        .onChange(of: viewModel.currentTarget) { _, _ in
            // Fly to each new question's country at the player's current zoom
            focusOnCurrentTarget(distance: nil)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                viewModel.resume()
            } else {
                viewModel.pause()
            }
        }
        .confirmationDialog("End this sweep?", isPresented: $showQuitConfirmation, titleVisibility: .visible) {
            Button("End Game", role: .destructive) { onDismiss() }
            Button("Keep Playing", role: .cancel) {}
        } message: {
            Text("Progress won't be saved.")
        }
        .confirmationDialog("Restart this sweep?", isPresented: $showRestartConfirmation, titleVisibility: .visible) {
            Button("Restart", role: .destructive) { playAgain() }
            Button("Keep Playing", role: .cancel) {}
        } message: {
            Text("Current progress will be lost.")
        }
        .preferredColorScheme(isDarkMode ? .dark : .light)
    }

    // MARK: - Game interaction

    /// Outlines the current question's country on the globe and flies to it.
    private func focusOnCurrentTarget(distance: Float?) {
        guard let target = viewModel.currentTarget else { return }
        gameGlobe.selectCountry(target, center: nil)
        if let center = CountryHitTester.shared.center(of: target) {
            gameGlobe.flyTo(.init(lat: center.lat, lon: center.lon, distance: distance))
        }
    }

    private func submit(_ guess: String) {
        // Captured before submitting: a correct guess advances the target
        let target = viewModel.currentTarget
        let capital = viewModel.currentCapital

        switch viewModel.submitGuess(guess) {
        case .correct(let points):
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            if let target = target {
                gameGlobe.deselectCountry(resumeAutoRotation: false)
                gameGlobe.setCountryHighlight(AppColors.challengeCorrectUI, for: target)
            }
            if let capital = capital {
                showFeedback(.correct(capital: capital, points: points))
            }

        case .wrong(let remainingTries):
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.wrong(guess: guess, remainingTries: remainingTries))

        case .reveal:
            guard let target = target, let capital = capital else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.reveal(country: target, capital: capital), duration: Self.revealDuration)
            gameGlobe.setCountryHighlight(AppColors.challengeWrongUI, for: target)
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.revealDuration) {
                gameGlobe.deselectCountry(resumeAutoRotation: false)
                viewModel.finishReveal()
            }

        case .marked, .ignored:
            break
        }
    }

    private func showFeedback(_ newFeedback: Feedback, duration: TimeInterval = 1.6) {
        feedbackToken += 1
        let token = feedbackToken
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            feedback = newFeedback
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            if feedbackToken == token {
                withAnimation(.easeOut(duration: 0.25)) {
                    feedback = nil
                }
            }
        }
    }

    private func playAgain() {
        feedbackToken += 1
        feedback = nil
        searchText = ""
        gameGlobe.resetAllData()
        gameGlobe.isAutoRotating = false
        viewModel.restart()
        focusOnCurrentTarget(distance: region.cameraTarget.distance)
    }

    // MARK: - HUD

    /// Search field with restart alongside, both rising with the keyboard.
    private var bottomBar: some View {
        HStack(alignment: .bottom, spacing: 10) {
            ChallengeSearchField(
                searchText: $searchText,
                suggestions: capitalSuggestions,
                guessedItems: Set(viewModel.wrongGuesses),
                isDarkMode: isDarkMode,
                onSubmit: { guess in
                    submit(guess)
                    searchText = ""
                }
            )
            SweepHUDButton(icon: "arrow.counterclockwise", isDarkMode: isDarkMode) {
                showRestartConfirmation = true
            }
        }
    }

    private func feedbackBanner(_ feedback: Feedback) -> some View {
        let text: String
        let color: Color
        switch feedback {
        case .correct(let capital, let points):
            text = "\(capital) +\(points)"
            color = AppColors.challengeCorrect
        case .wrong(let guess, let remainingTries):
            text = "Not \(guess) — \(remainingTries) \(remainingTries == 1 ? "try" : "tries") left"
            color = AppColors.challengeWrong
        case .reveal(let country, let capital):
            text = "The capital of \(country) is \(capital)"
            color = AppColors.challengeWrong
        }

        return SweepFeedbackBanner(text: text, color: color)
    }

    private var missedSummary: String? {
        guard !viewModel.missedCountries.isEmpty else { return nil }
        return viewModel.missedCountries.map { country in
            if let capital = viewModel.capital(of: country) {
                return "\(country) — \(capital)"
            }
            return country
        }.joined(separator: ", ")
    }
}
