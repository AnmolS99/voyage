import SwiftUI

// Shared HUD chrome for full-screen region-sweep games (Click the Country,
// Name the Capital): glass pills and buttons, the quit/timer/score top bar,
// the prompt card, feedback banners, and the end-of-sweep result overlay.

/// Liquid glass capsule on iOS 26, translucent card capsule before that.
struct GlassPill<Content: View>: View {
    let isDarkMode: Bool
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26, *) {
            content
                .glassEffect()
        } else {
            content
                .background(Capsule().fill(AppColors.cardBackground(isDarkMode: isDarkMode).opacity(0.9)))
        }
    }
}

/// Circular glass HUD button (quit, restart). `size` overrides the default
/// diameter, e.g. to match the search field height in Name the Capital.
struct SweepHUDButton: View {
    let icon: String
    let isDarkMode: Bool
    var size: CGFloat? = nil
    let action: () -> Void

    var body: some View {
        if #available(iOS 26, *) {
            if let size = size {
                // Exact diameter: glass applied to the sized label directly,
                // since .buttonStyle(.glass) pads beyond the label frame
                Button(action: action) {
                    Image(systemName: icon)
                        .font(.system(size: 17, weight: .medium))
                        .frame(width: size, height: size)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .glassEffect(.regular.interactive(), in: .circle)
            } else {
                Button(action: action) {
                    Image(systemName: icon)
                        .font(.system(size: 17, weight: .medium))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
            }
        } else {
            Button(action: action) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(isDarkMode ? .white : AppColors.closeButtonText)
                    .frame(width: size ?? 36, height: size ?? 36)
                    .background(
                        Circle()
                            .fill(isDarkMode ? AppColors.closeButtonDark : AppColors.closeButtonLight)
                    )
            }
        }
    }
}

/// Top bar: quit button leading, timer + score pill trailing.
struct SweepTopBar: View {
    @ObservedObject var viewModel: RegionSweepGameViewModel
    let isDarkMode: Bool
    var buttonSize: CGFloat? = nil
    let onQuit: () -> Void

    var body: some View {
        HStack(alignment: .center) {
            SweepHUDButton(icon: "xmark", isDarkMode: isDarkMode, size: buttonSize, action: onQuit)

            Spacer()

            GlassPill(isDarkMode: isDarkMode) {
                HStack(spacing: 6) {
                    Image(systemName: "stopwatch.fill")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(AppColors.buttonColor)
                    Text(formatGameTime(viewModel.elapsedTime))
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                    Text("·")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                    Image(systemName: "star.fill")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(AppColors.buttonColor)
                    Text("\(viewModel.score) pts")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                }
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                // Match the HUD buttons' height when one is specified
                .frame(height: buttonSize)
            }
        }
    }
}

/// Prompt card: sweep progress, flag + country name, an optional question
/// line, and the tries-left dots.
struct SweepPromptCard: View {
    @ObservedObject var viewModel: RegionSweepGameViewModel
    let flagProvider: (String) -> String
    var question: String? = nil
    let isDarkMode: Bool

    var body: some View {
        if let target = viewModel.currentTarget {
            VStack(spacing: 8) {
                Text("\(viewModel.solvedCount + 1)/\(viewModel.totalCountries)")
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                    .textCase(.uppercase)

                HStack(spacing: 10) {
                    Text(flagProvider(target))
                        .font(.system(size: 26))
                    Text(target)
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                }

                if let question = question {
                    Text(question)
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                }

                HStack(spacing: 6) {
                    ForEach(0..<RegionSweepGameViewModel.maxPointsPerCountry, id: \.self) { index in
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

/// Prompt card for "Name the Flag": sweep progress, a large flag, a prompt
/// line and the tries-left dots. The country name is deliberately omitted —
/// it's the answer the player is guessing.
struct SweepFlagPromptCard: View {
    @ObservedObject var viewModel: RegionSweepGameViewModel
    let flagProvider: (String) -> String
    let isDarkMode: Bool

    var body: some View {
        if let target = viewModel.currentTarget {
            VStack(spacing: 8) {
                Text("\(viewModel.solvedCount + 1)/\(viewModel.totalCountries)")
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                    .textCase(.uppercase)

                Text(flagProvider(target))
                    .font(.system(size: 72))

                Text("Which country?")
                    .font(.system(size: 14, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))

                HStack(spacing: 6) {
                    ForEach(0..<RegionSweepGameViewModel.maxPointsPerCountry, id: \.self) { index in
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

/// Capsule banner for correct/wrong/reveal feedback.
struct SweepFeedbackBanner: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Capsule().fill(color.opacity(0.95)))
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// End-of-sweep overlay: trophy, new-best badge, score, stat rows, a footer
/// listing what was missed, and Play Again / Done buttons.
struct SweepResultOverlay: View {
    @ObservedObject var viewModel: RegionSweepGameViewModel
    let isDarkMode: Bool
    let firstTryLabel: String
    /// Footer listing what was missed (nil when nothing was).
    let missedSummary: String?
    let onPlayAgain: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Text(viewModel.region.emoji)
                    .font(.system(size: 44))

                Text("Sweep Complete!")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                if viewModel.didEarnTrophy {
                    VStack(spacing: 6) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 40))
                            .foregroundStyle(viewModel.region.trophy.gradient)
                            .shadow(color: viewModel.region.trophy.glowColor.opacity(0.5), radius: 8, y: 2)
                        Text("\(viewModel.region.trophy.displayName) Trophy earned!")
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
                    resultRow(icon: "star.circle.fill", label: firstTryLabel, value: "\(viewModel.perfectCount)")
                    resultRow(icon: "eye.fill", label: "Revealed", value: "\(viewModel.missedCountries.count)")
                }
                .padding(.top, 4)

                if let missedSummary = missedSummary {
                    Text(missedSummary)
                        .font(.system(size: 12, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                        .lineLimit(3)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 10) {
                    Button(action: onPlayAgain) {
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
