import SwiftUI
import UIKit

/// Full-screen "Name the Flag" game. Each country in the region is shown as
/// its flag on the prompt card; the player types the country's name into a
/// search field. Unlike Name the Capital, the globe stays a neutral backdrop
/// during play — outlining the asked country would give the answer away — and
/// only flies to a country when its answer is revealed after three misses.
/// Correctly named countries stay green, revealed misses stay red. The game
/// runs on its own in-memory `GlobeState`, so nothing touches the user's
/// travel data.
struct NameFlagGameView: View {
    let region: ChallengeRegion
    let onDismiss: () -> Void

    @StateObject private var viewModel: NameFlagGameViewModel
    @StateObject private var gameGlobe: GlobeState
    @State private var searchText = ""
    @State private var feedback: Feedback?
    @State private var feedbackToken = 0
    @State private var showQuitConfirmation = false
    @State private var showRestartConfirmation = false
    /// Mirrors the search field's keyboard focus (pre-iOS 26 fallback bar);
    /// hides the restart button while typing so the field takes full width.
    @State private var isSearchFocused = false
    @Environment(\.scenePhase) private var scenePhase

    private let isDarkMode: Bool
    /// Every country name in the dataset — the search field's suggestion pool,
    /// so the answer never stands out by being the only nearby suggestion.
    private let countrySuggestions: [String]
    private static let revealDuration: TimeInterval = 2.6

    init(region: ChallengeRegion, mainState: GlobeState, onDismiss: @escaping () -> Void) {
        self.region = region
        self.onDismiss = onDismiss
        self.isDarkMode = mainState.isDarkMode
        _viewModel = StateObject(wrappedValue: NameFlagGameViewModel(region: region))

        let globe = GlobeState(inMemory: true)
        globe.globeStyle = mainState.globeStyle
        globe.isDarkMode = mainState.isDarkMode
        globe.isAutoRotating = false
        globe.showsCapitalMarker = false
        _gameGlobe = StateObject(wrappedValue: globe)

        countrySuggestions = CountryDataCache.shared.countries.map { $0.name }.sorted()
    }

    private enum Feedback: Equatable {
        case correct(country: String, points: Int)
        case wrong(guess: String, remainingTries: Int)
        case reveal(country: String)
    }

    var body: some View {
        ZStack {
        Group {
            if #available(iOS 26, *) {
                // Native bottom search: the system's liquid-glass field and
                // the restart toolbar button get the exact `.searchable`
                // show/hide choreography used on the Home country list.
                NavigationStack {
                    gameContent(includesSearchBar: false)
                        .toolbar(.hidden, for: .navigationBar)
                        .toolbar {
                            // Pin the system search field into the bottom
                            // toolbar (it defaults to the hidden nav bar),
                            // with restart as a round button beside it. The
                            // result overlay is layered above this whole stack,
                            // so the field is covered (not removed) when the
                            // sweep finishes.
                            DefaultToolbarItem(kind: .search, placement: .bottomBar)
                            ToolbarItem(placement: .bottomBar) {
                                Button {
                                    showRestartConfirmation = true
                                } label: {
                                    Image(systemName: "arrow.counterclockwise")
                                }
                            }
                        }
                        .searchable(text: $searchText, prompt: "Type your guess...")
                        .onSubmit(of: .search) {
                            submit(searchText)
                            searchText = ""
                        }
                }
            } else {
                gameContent(includesSearchBar: true)
            }
        }

        // Result overlay layered ABOVE the navigation chrome. On iOS 26 the
        // search field lives inside the NavigationStack's bottom toolbar and
        // .searchable keeps it there even with no toolbar item, so an overlay
        // nested inside gameContent would sit under it. Placed here it covers
        // the field (its dim also swallows taps, so the keyboard can't be
        // brought back) and ignores the keyboard so the card never shifts.
        if viewModel.phase == .finished {
            SweepResultOverlay(
                viewModel: viewModel,
                isDarkMode: isDarkMode,
                firstTryLabel: "First-try answers",
                missedSummary: missedSummary,
                onPlayAgain: playAgain,
                onDismiss: onDismiss
            )
            .ignoresSafeArea(.keyboard)
        }
        }
        .onAppear {
            gameGlobe.flyTo(region.cameraTarget)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                viewModel.resume()
            } else {
                viewModel.pause()
            }
        }
        .onChange(of: viewModel.phase) { _, newPhase in
            // When the sweep ends, drop keyboard focus so the keyboard and
            // search field don't cover the result overlay's buttons.
            if newPhase == .finished {
                isSearchFocused = false
                dismissKeyboard()
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

    // MARK: - Screen content

    /// The game screen: globe, top HUD, prompt card and feedback banner.
    /// `includesSearchBar` adds the pre-iOS 26 custom search bar; on iOS 26
    /// the native `.searchable` field takes its place.
    private func gameContent(includesSearchBar: Bool) -> some View {
        ZStack {
            GlobeBackdrop(isDarkMode: isDarkMode)

            // The globe is a neutral backdrop framing the region — the asked
            // country is deliberately not outlined (that's the answer), and
            // taps on it are not part of this game
            GlobeView(globeState: gameGlobe, onCountryTapped: { _ in })
                .ignoresSafeArea()

            // Top HUD pinned to the top, ignoring the keyboard so a growing
            // suggestion list below never pushes it up
            VStack(spacing: 12) {
                SweepTopBar(
                    viewModel: viewModel,
                    isDarkMode: isDarkMode,
                    buttonSize: ChallengeSearchField.fieldHeight
                ) {
                    showQuitConfirmation = true
                }
                SweepFlagPromptCard(
                    viewModel: viewModel,
                    flagProvider: gameGlobe.flagForCountry,
                    isDarkMode: isDarkMode
                )
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .ignoresSafeArea(.keyboard)

            // Bottom layer: feedback + suggestions/search, floating up from
            // the bottom over the globe (independent of the top HUD)
            VStack(spacing: 12) {
                Spacer(minLength: 0)
                if let feedback = feedback {
                    feedbackBanner(feedback)
                }
                // Hide the search UI once the sweep is over, so it never sits
                // over the result overlay
                if viewModel.phase != .finished {
                    if includesSearchBar {
                        bottomBar
                    } else {
                        // Native path: compact dropdown floating above the
                        // system search field instead of full-screen suggestions
                        nativeSearchDropdown
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
        }
    }

    /// Compact dropdown floating above the native search field (iOS 26):
    /// tapping a country submits it; already-guessed ones are greyed out.
    @ViewBuilder
    private var nativeSearchDropdown: some View {
        let matches = searchText.isEmpty ? [] : countrySuggestions
            .filter { $0.localizedCaseInsensitiveContains(searchText) }
        if !matches.isEmpty {
            ChallengeSuggestionList(
                suggestions: matches,
                guessedItems: Set(viewModel.wrongGuesses),
                isDarkMode: isDarkMode,
                usesGlass: true,
                onSelect: { suggestion in
                    submit(suggestion)
                    searchText = ""
                }
            )
        }
    }

    // MARK: - Game interaction

    private func submit(_ guess: String) {
        // Captured before submitting: a correct guess advances the target
        let target = viewModel.currentTarget

        switch viewModel.submitGuess(guess) {
        case .correct(let points):
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            if let target = target {
                // Naming it right reveals where it is — colour it in on the globe
                gameGlobe.setCountryHighlight(AppColors.challengeCorrectUI, for: target)
                showFeedback(.correct(country: target, points: points))
            }

        case .wrong(let remainingTries):
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.wrong(guess: guess, remainingTries: remainingTries))

        case .reveal:
            guard let target = target else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.reveal(country: target), duration: Self.revealDuration)
            // Fly to and outline the missed country so the player sees where it was
            gameGlobe.setCountryHighlight(AppColors.challengeWrongUI, for: target)
            gameGlobe.selectCountry(target, center: CountryHitTester.shared.center(of: target))
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.revealDuration) {
                gameGlobe.deselectCountry(resumeAutoRotation: false)
                gameGlobe.flyTo(region.cameraTarget)
                viewModel.finishReveal()
            }

        case .marked, .ignored:
            break
        }
    }

    /// Resigns the first responder, dismissing the keyboard (works for both
    /// the native search field and the pre-iOS 26 custom text field).
    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                        to: nil, from: nil, for: nil)
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
        gameGlobe.flyTo(region.cameraTarget)
        viewModel.restart()
    }

    // MARK: - HUD

    /// Pre-iOS 26 fallback: the custom search field with the restart button
    /// at its side, hidden while typing so the field takes the full width.
    private var bottomBar: some View {
        HStack(alignment: .bottom, spacing: 10) {
            ChallengeSearchField(
                searchText: $searchText,
                suggestions: countrySuggestions,
                guessedItems: Set(viewModel.wrongGuesses),
                isDarkMode: isDarkMode,
                usesGlass: true,
                onFocusChange: { focused in
                    isSearchFocused = focused
                },
                onSubmit: { guess in
                    submit(guess)
                    searchText = ""
                }
            )

            if !isSearchFocused {
                SweepHUDButton(
                    icon: "arrow.counterclockwise",
                    isDarkMode: isDarkMode,
                    size: ChallengeSearchField.fieldHeight
                ) {
                    showRestartConfirmation = true
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.bouncy(duration: 0.4), value: isSearchFocused)
    }

    private func feedbackBanner(_ feedback: Feedback) -> some View {
        let text: String
        let color: Color
        switch feedback {
        case .correct(let country, let points):
            text = "\(country) +\(points)"
            color = AppColors.challengeCorrect
        case .wrong(let guess, let remainingTries):
            text = "Not \(guess) — \(remainingTries) \(remainingTries == 1 ? "try" : "tries") left"
            color = AppColors.challengeWrong
        case .reveal(let country):
            text = "This flag is \(country) \(gameGlobe.flagForCountry(country))"
            color = AppColors.challengeWrong
        }

        return SweepFeedbackBanner(text: text, color: color)
    }

    private var missedSummary: String? {
        guard !viewModel.missedCountries.isEmpty else { return nil }
        return viewModel.missedCountries.joined(separator: ", ")
    }
}
