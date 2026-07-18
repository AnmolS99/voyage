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
        _gameGlobe = StateObject(wrappedValue: globe)
    }

    private enum Feedback: Equatable {
        case correct(country: String, points: Int)
        case wrong(tapped: String, remainingTries: Int)
        case reveal(country: String)
    }

    var body: some View {
        ZStack {
            GlobeBackdrop(isDarkMode: isDarkMode)

            GlobeView(globeState: gameGlobe, onCountryTapped: handleTap)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                topBar
                promptCard
                Spacer()
                if let feedback = feedback {
                    feedbackBanner(feedback)
                }
                statsBar
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)

            if viewModel.phase == .finished {
                resultOverlay
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
        case .correct(let points):
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            gameGlobe.setCountryHighlight(AppColors.challengeCorrectUI, for: country)
            showFeedback(.correct(country: country, points: points))

        case .wrong(let remainingTries):
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            showFeedback(.wrong(tapped: country, remainingTries: remainingTries))

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

    private var topBar: some View {
        HStack {
            if #available(iOS 26, *) {
                // Liquid glass buttons, matching the Home tab header
                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 0) {
                        hudButton(icon: "xmark") {
                            showQuitConfirmation = true
                        }
                        hudButton(icon: "arrow.counterclockwise") {
                            showRestartConfirmation = true
                        }
                    }
                }
                .tint(nil)
            } else {
                HStack(spacing: 8) {
                    hudButton(icon: "xmark") {
                        showQuitConfirmation = true
                    }
                    hudButton(icon: "arrow.counterclockwise") {
                        showRestartConfirmation = true
                    }
                }
            }

            Spacer()

            glassPill {
                HStack(spacing: 6) {
                    Image(systemName: "stopwatch.fill")
                        .font(.system(size: 12, weight: .medium))
                    Text(formatGameTime(viewModel.elapsedTime))
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                }
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
            }
        }
    }

    @ViewBuilder
    private func hudButton(icon: String, action: @escaping () -> Void) -> some View {
        if #available(iOS 26, *) {
            Button(action: action) {
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .medium))
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
        } else {
            Button(action: action) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(isDarkMode ? .white : AppColors.closeButtonText)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle()
                            .fill(isDarkMode ? AppColors.closeButtonDark : AppColors.closeButtonLight)
                    )
            }
        }
    }

    /// Liquid glass capsule on iOS 26, translucent card capsule before that.
    @ViewBuilder
    private func glassPill<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if #available(iOS 26, *) {
            content()
                .glassEffect()
        } else {
            content()
                .background(Capsule().fill(AppColors.cardBackground(isDarkMode: isDarkMode).opacity(0.9)))
        }
    }

    private var promptCard: some View {
        Group {
            if let target = viewModel.currentTarget {
                VStack(spacing: 8) {
                    Text("Find")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                        .textCase(.uppercase)

                    HStack(spacing: 10) {
                        Text(gameGlobe.flagForCountry(target))
                            .font(.system(size: 26))
                        Text(target)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    }

                    HStack(spacing: 6) {
                        ForEach(0..<ClickCountryGameViewModel.maxPointsPerCountry, id: \.self) { index in
                            Circle()
                                .fill(index < viewModel.triesLeft ?
                                      AppColors.buttonColor :
                                      AppColors.track(isDarkMode: isDarkMode))
                                .frame(width: 8, height: 8)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                        .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
                )
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: target)
            }
        }
    }

    private var statsBar: some View {
        HStack {
            statPill(icon: "checkmark.circle.fill", text: "\(viewModel.solvedCount)/\(viewModel.totalCountries)")
            Spacer()
            statPill(icon: "star.fill", text: "\(viewModel.score) pts")
        }
    }

    private func statPill(icon: String, text: String) -> some View {
        glassPill {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(AppColors.buttonColor)
                Text(text)
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
        }
    }

    private func feedbackBanner(_ feedback: Feedback) -> some View {
        let text: String
        let color: Color
        switch feedback {
        case .correct(let country, let points):
            text = "\(country) +\(points)"
            color = AppColors.challengeCorrect
        case .wrong(let tapped, let remainingTries):
            text = "That's \(tapped) — \(remainingTries) \(remainingTries == 1 ? "try" : "tries") left"
            color = AppColors.challengeWrong
        case .reveal(let country):
            text = "\(country) \(gameGlobe.flagForCountry(country)) is here"
            color = AppColors.challengeWrong
        }

        return Text(text)
            .font(.system(size: 14, weight: .semibold, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Capsule().fill(color.opacity(0.95)))
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    // MARK: - Result overlay

    private var resultOverlay: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Text(region.emoji)
                    .font(.system(size: 44))

                Text("Sweep Complete!")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                if viewModel.didEarnTrophy {
                    VStack(spacing: 6) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 40))
                            .foregroundStyle(region.trophy.gradient)
                            .shadow(color: region.trophy.glowColor.opacity(0.5), radius: 8, y: 2)
                        Text("\(region.trophy.displayName) Trophy earned!")
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    }
                }

                if viewModel.isNewBest {
                    Text("🏆 New Best!")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(AppColors.buttonVisited))
                }

                VStack(spacing: 4) {
                    Text("\(viewModel.score) / \(viewModel.maxScore)")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundColor(AppColors.buttonColor)
                    Text("\(scorePercentage)% · \(formatGameTime(viewModel.elapsedTime))")
                        .font(.system(size: 15, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                }

                VStack(spacing: 8) {
                    resultRow(icon: "star.circle.fill", label: "First-try finds", value: "\(viewModel.perfectCount)")
                    resultRow(icon: "eye.fill", label: "Revealed", value: "\(viewModel.missedCountries.count)")
                }
                .padding(.top, 4)

                if !viewModel.missedCountries.isEmpty {
                    Text(viewModel.missedCountries.joined(separator: ", "))
                        .font(.system(size: 12, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                        .lineLimit(3)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 10) {
                    Button(action: playAgain) {
                        Text("Play Again")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(AppColors.buttonColor))
                    }

                    Button(action: onDismiss) {
                        Text("Done")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundColor(AppColors.buttonColor)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(AppColors.buttonColor, lineWidth: 2)
                            )
                    }
                }
                .padding(.top, 8)
            }
            .padding(24)
            .background(
                RoundedRectangle(cornerRadius: 24)
                    .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                    .shadow(color: .black.opacity(0.3), radius: 20, y: 8)
            )
            .padding(.horizontal, 24)

            if viewModel.isNewBest {
                ConfettiView()
            }
        }
    }

    private func resultRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(AppColors.buttonColor)
                .frame(width: 20)
            Text(label)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
            Spacer()
            Text(value)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
        }
        .frame(maxWidth: 240)
    }

    private var scorePercentage: Int {
        guard viewModel.maxScore > 0 else { return 0 }
        return Int((Double(viewModel.score) / Double(viewModel.maxScore) * 100).rounded())
    }

}
