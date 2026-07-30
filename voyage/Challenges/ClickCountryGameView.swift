import SwiftUI
import UIKit

/// Full-screen "Click the Country" game. The target country is shown at the
/// top and the user taps it on the globe. Found countries stay green, revealed
/// misses stay red — the game runs on its own in-memory `GlobeState`, so
/// nothing touches the user's real travel data.
struct ClickCountryGameView: View {
    let region: ChallengeRegion
    let onDismiss: () -> Void

    @StateObject private var viewModel: ClickCountryGameViewModel
    @StateObject private var gameGlobe: GlobeState
    @State private var feedback: Feedback?
    @State private var feedbackToken = 0
    @State private var showQuitConfirmation = false
    @State private var showRestartConfirmation = false
    @Environment(\.scenePhase) private var scenePhase

    private let isDarkMode: Bool
    private static let revealDuration: TimeInterval = 2.2

    init(region: ChallengeRegion, mainState: GlobeState, onDismiss: @escaping () -> Void) {
        self.region = region
        self.onDismiss = onDismiss
        self.isDarkMode = mainState.isDarkMode
        _viewModel = StateObject(wrappedValue: ClickCountryGameViewModel(region: region))

        let globe = GlobeState(inMemory: true)
        globe.globeStyle = mainState.globeStyle
        globe.isDarkMode = mainState.isDarkMode
        globe.isAutoRotating = false
        globe.showsCapitalMarker = false
        _gameGlobe = StateObject(wrappedValue: globe)
    }

    private enum Feedback: Equatable {
        case correct(country: String)
        case reveal(country: String)
    }

    var body: some View {
        ZStack {
            GlobeBackdrop(isDarkMode: isDarkMode)

            GlobeView(globeState: gameGlobe, onCountryTapped: handleTap)
                .ignoresSafeArea()

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
                Spacer()
                if let feedback = feedback {
                    feedbackBanner(feedback)
                } else if viewModel.pendingGuess != nil {
                    confirmHint
                }
                bottomBar
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)

            if viewModel.phase == .finished {
                SweepResultOverlay(
                    viewModel: viewModel,
                    isDarkMode: isDarkMode,
                    missedSummary: viewModel.missedCountries.isEmpty ?
                        nil : viewModel.missedCountries.joined(separator: ", "),
                    onPlayAgain: playAgain,
                    onDismiss: onDismiss
                )
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

    private func handleTap(on country: String) {
        switch viewModel.handleTap(on: country) {
        case .marked:
            // First tap: only outline the country — the camera stays put.
            // Flying the globe to the tap would move the target out from under
            // the finger mid-animation, so the confirming tap could land on a
            // neighbour. The name is deliberately not shown so marking doesn't
            // reveal the answer.
            UISelectionFeedbackGenerator().selectionChanged()
            gameGlobe.selectCountry(country, center: nil)

        case .correct:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            gameGlobe.deselectCountry(resumeAutoRotation: false)
            gameGlobe.setCountryHighlight(AppColors.challengeCorrectUI, for: country)
            showFeedback(.correct(country: country))

        case .reveal:
            guard let target = viewModel.currentTarget else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.reveal(country: target), duration: Self.revealDuration)
            gameGlobe.setCountryHighlight(AppColors.challengeWrongUI, for: target)
            gameGlobe.selectCountry(target, center: CountryHitTester.shared.center(of: target))
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.revealDuration) {
                gameGlobe.deselectCountry(resumeAutoRotation: false)
                viewModel.finishReveal()
            }

        case .ignored:
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
        gameGlobe.resetAllData()
        gameGlobe.isAutoRotating = false
        gameGlobe.flyTo(region.cameraTarget)
        viewModel.restart()
    }

    // MARK: - HUD

    /// Restart sits bottom trailing, within thumb reach when holding the
    /// phone one-handed.
    private var bottomBar: some View {
        HStack {
            Spacer()
            SweepHUDButton(
                icon: "arrow.counterclockwise",
                isDarkMode: isDarkMode,
                size: ChallengeSearchField.fieldHeight
            ) {
                showRestartConfirmation = true
            }
        }
    }

    /// Shown while a country is marked. Intentionally neutral: naming the
    /// marked country would give the answer away before the guess is made.
    private var confirmHint: some View {
        GlassPill(isDarkMode: isDarkMode) {
            HStack(spacing: 6) {
                Image(systemName: "hand.tap.fill")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(AppColors.buttonColor)
                Text("Tap again to confirm your guess")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func feedbackBanner(_ feedback: Feedback) -> some View {
        let text: String
        let color: Color
        switch feedback {
        case .correct(let country):
            text = "Correct — \(country)"
            color = AppColors.challengeCorrect
        case .reveal(let country):
            text = "\(country) \(gameGlobe.flagForCountry(country)) is here"
            color = AppColors.challengeWrong
        }

        return SweepFeedbackBanner(text: text, color: color)
    }
}
