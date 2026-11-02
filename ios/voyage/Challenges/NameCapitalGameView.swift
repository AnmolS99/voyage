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
    /// Bumped by each reveal and by whatever ends one, so a reveal's timer
    /// only ends that reveal (not one started after it was dismissed early).
    @State private var revealToken = 0
    @State private var showQuitConfirmation = false
    @State private var showRestartConfirmation = false
    /// Mirrors the search field's keyboard focus (pre-iOS 26 fallback bar);
    /// hides the restart button while typing so the field takes full width.
    @State private var isSearchFocused = false
    /// Drives the native (iOS 26) search field's focus so the game can grab
    /// the keyboard as soon as it opens, and release it when the sweep ends.
    @FocusState private var isFieldFocused: Bool
    @Environment(\.scenePhase) private var scenePhase

    private let isDarkMode: Bool
    /// Every capital in the dataset — the search field's suggestion pool, so
    /// the answer never stands out by being the only nearby suggestion.
    private let capitalSuggestions: CountrySearchIndex<String>
    /// How long the sweep pauses on a revealed capital before moving on.
    private static let revealPause: TimeInterval = 2.6

    init(region: ChallengeRegion, mainState: GlobeState, onDismiss: @escaping () -> Void) {
        self.region = region
        self.onDismiss = onDismiss
        self.isDarkMode = mainState.isDarkMode
        _viewModel = StateObject(wrappedValue: NameCapitalGameViewModel(region: region))

        let globe = GlobeState(inMemory: true)
        globe.globeStyle = mainState.globeStyle
        globe.showsTerrain = mainState.showsTerrain
        globe.isDarkMode = mainState.isDarkMode
        globe.isAutoRotating = false
        globe.showsCapitalMarker = false
        _gameGlobe = StateObject(wrappedValue: globe)

        capitalSuggestions = .plain(Array(Set(CountryDataCache.shared.countries.flatMap(\.capitalNames))))
    }

    private enum Feedback: Equatable {
        case correct(capital: String)
        case reveal(country: String, capitals: [String])
    }

    var body: some View {
        ZStack {
        Group {
            if #available(iOS 26, *) {
                // Native bottom search: the system's liquid-glass field and
                // the restart toolbar button get the exact `.searchable`
                // show/hide choreography used on the Home country list.
                NavigationStack {
                    // Shared guess-entry search: the system field pinned into
                    // the bottom toolbar, with restart as a round button beside
                    // it. The result overlay is layered above this whole stack,
                    // so the field is covered (not removed) when the sweep
                    // finishes.
                    gameContent(includesSearchBar: false)
                        .toolbar(.hidden, for: .navigationBar)
                        .bottomBarGuessSearch(
                            text: $searchText,
                            focused: $isFieldFocused,
                            trailing: {
                                Button {
                                    showRestartConfirmation = true
                                } label: {
                                    Image(systemName: "arrow.counterclockwise")
                                }
                            },
                            // Enter submits the dropdown's top row
                            onSubmit: { submit(capitalSuggestions.guessToSubmit(for: $0)) }
                        )
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
                missedSummary: missedSummary,
                onPlayAgain: playAgain,
                onDismiss: onDismiss
            )
            .ignoresSafeArea(.keyboard)
        }
        }
        .onAppear {
            focusOnCurrentTarget(distance: region.cameraTarget.distance)
            focusSearchField()
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
        .onChange(of: viewModel.phase) { _, newPhase in
            // When the sweep ends, drop keyboard focus so the keyboard and
            // search field don't cover the result overlay's buttons.
            if newPhase == .finished {
                isSearchFocused = false
                isFieldFocused = false
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

            // The globe is a backdrop showing the asked country — taps on it
            // are not part of this game
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
                SweepPromptCard(
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
    /// tapping a capital submits it; already-guessed ones are greyed out.
    private var nativeSearchDropdown: some View {
        GuessSuggestionDropdown(
            suggestions: capitalSuggestions,
            query: searchText,
            isDarkMode: isDarkMode,
            usesGlass: true,
            onSelect: { suggestion in
                submit(suggestion)
                searchText = ""
            }
        )
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
        let capitals = viewModel.currentAnswers
        // The capital the player named, of a country that may have several
        let named = viewModel.acceptedAnswer(for: guess)

        switch viewModel.submitGuess(guess) {
        case .correct:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            if let target = target {
                gameGlobe.deselectCountry(resumeAutoRotation: false)
                gameGlobe.setCountryHighlight(AppColors.challengeCorrectUI, for: target)
            }
            if let named = named {
                showFeedback(.correct(capital: named))
            }

        case .reveal:
            guard let target = target, !capitals.isEmpty else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.reveal(country: target, capitals: capitals), duration: SweepFeedbackBanner.revealDuration)
            gameGlobe.setCountryHighlight(AppColors.challengeWrongUI, for: target)
            scheduleRevealEnd()

        case .marked, .ignored:
            break
        }
    }

    /// Grabs keyboard focus for the native (iOS 26) search field so the player
    /// can start typing right away. A short hop past the first layout pass lets
    /// the search field install before it claims focus.
    private func focusSearchField() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            isFieldFocused = true
        }
    }

    /// Resigns the first responder, dismissing the keyboard (works for both
    /// the native search field and the pre-iOS 26 custom text field).
    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                        to: nil, from: nil, for: nil)
    }

    private func showFeedback(_ newFeedback: Feedback, duration: TimeInterval = SweepFeedbackBanner.correctDuration) {
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

    /// The banner was tapped or swiped away. If the sweep is still pausing on
    /// a revealed answer, it moves on now: the player has read it.
    private func dismissFeedback() {
        feedbackToken += 1
        withAnimation(.easeOut(duration: 0.25)) {
            feedback = nil
        }
        endReveal()
    }

    /// Ends the reveal pause (its timer ran out, or the banner was dismissed)
    /// and moves the sweep on. The banner itself stays until its own timer.
    private func endReveal() {
        revealToken += 1
        guard viewModel.phase == .revealing else { return }
        gameGlobe.deselectCountry(resumeAutoRotation: false)
        viewModel.finishReveal()
        // The last answer gives way to the result overlay
        if viewModel.phase == .finished {
            feedbackToken += 1
            feedback = nil
        }
    }

    /// Ends the reveal that has just started, `revealPause` from now, unless
    /// something ends it sooner.
    private func scheduleRevealEnd() {
        revealToken += 1
        let token = revealToken
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.revealPause) {
            if revealToken == token { endReveal() }
        }
    }

    private func playAgain() {
        feedbackToken += 1
        revealToken += 1
        feedback = nil
        searchText = ""
        gameGlobe.resetAllData()
        gameGlobe.isAutoRotating = false
        viewModel.restart()
        focusOnCurrentTarget(distance: region.cameraTarget.distance)
        focusSearchField()
    }

    // MARK: - HUD

    /// Pre-iOS 26 fallback: the custom search field with the restart button
    /// at its side, hidden while typing so the field takes the full width.
    private var bottomBar: some View {
        HStack(alignment: .bottom, spacing: 10) {
            ChallengeSearchField(
                searchText: $searchText,
                suggestions: capitalSuggestions,
                isDarkMode: isDarkMode,
                usesGlass: true,
                onFocusChange: { focused in
                    isSearchFocused = focused
                },
                autofocus: true,
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
        case .correct(let capital):
            text = "Correct — \(capital)"
            color = AppColors.challengeCorrect
        case .reveal(let country, let capitals):
            text = capitals.count > 1
                ? "The capitals of \(country) are \(ListFormatter.localizedString(byJoining: capitals))"
                : "The capital of \(country) is \(capitals.joined())"
            color = AppColors.challengeWrong
        }

        return SweepFeedbackBanner(text: text, color: color, onDismiss: dismissFeedback)
    }

    private var missedSummary: String? {
        guard !viewModel.missedCountries.isEmpty else { return nil }
        return viewModel.missedCountries.map { country in
            let capitals = viewModel.capitals(of: country)
            return capitals.isEmpty ? country : "\(country) — \(capitals.joined(separator: " / "))"
        }.joined(separator: ", ")
    }
}
