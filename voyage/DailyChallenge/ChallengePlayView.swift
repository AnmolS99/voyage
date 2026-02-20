import SwiftUI

struct ChallengePlayView: View {
    let date: Date
    let isDarkMode: Bool
    @StateObject private var viewModel = DailyChallengeViewModel()
    @State private var searchText = ""
    @State private var showConfetti = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.pageBackground(isDarkMode: isDarkMode)
                    .ignoresSafeArea()

                switch viewModel.viewState {
                case .loading:
                    ProgressView("Loading challenge...")
                        .font(.system(size: 16, design: .rounded))
                        .foregroundColor(AppColors.textSecondary(isDarkMode: isDarkMode))

                case .error(let message):
                    errorView(message)

                case .playing:
                    playingView

                case .completed:
                    if let challenge = viewModel.challenge, let country = viewModel.answerCountry {
                        ChallengeResultView(
                            challenge: challenge,
                            country: country,
                            solved: viewModel.solved,
                            attempts: viewModel.attemptsUsed,
                            guesses: viewModel.guesses,
                            isDarkMode: isDarkMode,
                            onDismiss: { dismiss() }
                        )
                    }
                }
            }
            .overlay { if showConfetti { ConfettiView() } }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                    }
                }
            }
        }
        .task {
            await viewModel.loadChallenge(for: date)
        }
        .onChange(of: viewModel.viewState) { oldState, newState in
            if case .completed = newState, case .playing = oldState, viewModel.solved {
                showConfetti = true
                Task {
                    try? await Task.sleep(for: .seconds(3))
                    showConfetti = false
                }
            }
        }
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundColor(.orange)
            Text(message)
                .font(.system(size: 16, design: .rounded))
                .foregroundColor(AppColors.textSecondary(isDarkMode: isDarkMode))
                .multilineTextAlignment(.center)
            Button("Try Again") {
                Task { await viewModel.loadChallenge(for: date) }
            }
            .font(.system(size: 16, weight: .semibold, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppColors.buttonColor(isDarkMode: isDarkMode))
            )
        }
        .padding(32)
    }

    @ViewBuilder
    private var playingView: some View {
        if let challenge = viewModel.challenge {
            ScrollView {
                VStack(spacing: 24) {
                    // Question type header
                    questionHeader(challenge.questionType)

                    // Clue card
                    clueCard(challenge)

                    // Attempt indicators
                    attemptIndicators

                    // Previous guesses
                    if !viewModel.guesses.isEmpty {
                        guessList
                    }

                    // Search field
                    ChallengeSearchField(
                        searchText: $searchText,
                        suggestions: viewModel.suggestions,
                        guessedItems: Set(viewModel.guesses),
                        isDarkMode: isDarkMode,
                        onSubmit: { guess in
                            viewModel.submitGuess(guess)
                            searchText = ""
                        }
                    )
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 24)
            }
        }
    }

    private func questionHeader(_ type: QuestionType) -> some View {
        VStack(spacing: 6) {
            Image(systemName: type.icon)
                .font(.system(size: 24))
                .foregroundColor(AppColors.buttonColor(isDarkMode: isDarkMode))
            Text(type.title)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
            Text(type.subtitle)
                .font(.system(size: 14, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
        }
    }

    private func clueCard(_ challenge: DailyChallenge) -> some View {
        Group {
            switch challenge.questionType {
            case .country:
                CountrySilhouetteView(flagCode: challenge.answer, isDarkMode: isDarkMode)
                    .frame(height: 220)
                    .padding(.horizontal, 40)

            case .capital:
                capitalClue(challenge)

            case .flag:
                flagClue(challenge)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }

    private func capitalClue(_ challenge: DailyChallenge) -> some View {
        VStack(spacing: 12) {
            if let country = viewModel.answerCountry {
                Text(flagEmojiFromCode(challenge.answer))
                    .font(.system(size: 60))
                Text(country.name)
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                Text("What is the capital?")
                    .font(.system(size: 16, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
            }
        }
    }

    private func flagClue(_ challenge: DailyChallenge) -> some View {
        VStack(spacing: 12) {
            Text(flagEmojiFromCode(challenge.answer))
                .font(.system(size: 100))
            Text("Which country?")
                .font(.system(size: 16, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
        }
    }

    private var attemptIndicators: some View {
        HStack(spacing: 8) {
            ForEach(0..<5, id: \.self) { index in
                Circle()
                    .fill(attemptColor(for: index))
                    .frame(width: 16, height: 16)
            }
        }
    }

    private func attemptColor(for index: Int) -> Color {
        if index >= viewModel.attemptsUsed {
            return AppColors.track(isDarkMode: isDarkMode)
        }
        if index == viewModel.attemptsUsed - 1 && viewModel.solved {
            return .green
        }
        return .red
    }

    private var guessList: some View {
        VStack(spacing: 8) {
            ForEach(Array(viewModel.guesses.enumerated()), id: \.offset) { index, guess in
                HStack(spacing: 12) {
                    Image(systemName: isCorrectGuess(guess) ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundColor(isCorrectGuess(guess) ? .green : .red)
                    Text(guess)
                        .font(.system(size: 15, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    Spacer()
                    Text("Attempt \(index + 1)")
                        .font(.system(size: 12, design: .rounded))
                        .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                )
            }
        }
        .padding(.horizontal, 20)
    }

    private func isCorrectGuess(_ guess: String) -> Bool {
        guard let country = viewModel.answerCountry else { return false }
        switch viewModel.challenge?.questionType {
        case .capital:
            return country.capital?.name.caseInsensitiveCompare(guess) == .orderedSame
        case .country, .flag:
            return country.name.caseInsensitiveCompare(guess) == .orderedSame
        case nil:
            return false
        }
    }
}
