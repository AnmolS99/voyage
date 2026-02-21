import SwiftUI

struct ChallengeResultView: View {
    let challenge: DailyChallenge
    let country: GeoJSONCountry
    let solved: Bool
    let attempts: Int
    let guesses: [String]
    let streak: Int
    let isDarkMode: Bool
    let onDismiss: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Result header
                resultHeader

                // Answer detail card
                answerCard

                // Guesses recap
                if !guesses.isEmpty {
                    guessesRecap
                }

                // Footer action
                footerButton
            }
            .padding(.vertical, 32)
            .padding(.horizontal, 20)
        }
    }

    private var resultHeader: some View {
        VStack(spacing: 32) {
            Text(solved ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.system(size: 0))
                .hidden()
                .overlay(
                    Image(systemName: solved ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 56))
                        .foregroundColor(solved ? .green : .red)
                )

            Text(solved ? "Correct!" : "Better luck next time!")
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

            if solved {
                Text("Solved in \(attempts) attempt\(attempts == 1 ? "" : "s")")
                    .font(.system(size: 16, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
            }
        }
    }

    private var answerCard: some View {
        VStack(spacing: 16) {
            // Country name + flag
            HStack(spacing: 10) {
                if let flagCode = country.flagCode {
                    Text(flagEmojiFromCode(flagCode))
                        .font(.system(size: 32))
                }
                Text(country.name)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
            }

            // Country outline
            if let flagCode = country.flagCode {
                CountrySilhouetteView(flagCode: flagCode, isDarkMode: isDarkMode)
                    .frame(height: 100)
            }

            // Details
            VStack(spacing: 8) {
                if let capital = country.capital {
                    detailRow(icon: "building.columns.fill", label: "Capital", value: capital.name)
                }
                if let continent = country.continent {
                    detailRow(icon: "globe", label: "Continent", value: continent)
                }
                detailRow(
                    icon: "questionmark.circle.fill",
                    label: "Challenge Type",
                    value: challenge.questionType.title
                )
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
    }

    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(AppColors.buttonColor(isDarkMode: isDarkMode))
                .frame(width: 20)
            Text(label)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
            Spacer()
            Text(value)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
        }
    }

    private var guessesRecap: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your Guesses")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))

            ForEach(Array(guesses.enumerated()), id: \.offset) { index, guess in
                HStack(spacing: 10) {
                    Text("\(index + 1).")
                        .font(.system(size: 14, weight: .medium, design: .monospaced))
                        .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                        .frame(width: 24, alignment: .trailing)
                    Text(guess)
                        .font(.system(size: 15, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    Spacer()
                    Image(systemName: isCorrect(guess) ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundColor(isCorrect(guess) ? .green : .red)
                        .font(.system(size: 14))
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
        )
    }

    private var footerButton: some View {
        VStack(spacing: 12) {
            ShareLink(item: shareText) {
                Label("Share Result", systemImage: "square.and.arrow.up")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(AppColors.buttonColor(isDarkMode: isDarkMode))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(AppColors.buttonColor(isDarkMode: isDarkMode), lineWidth: 2)
                    )
            }

            Button(action: onDismiss) {
                Text("Back to Calendar")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(AppColors.buttonColor(isDarkMode: isDarkMode))
                    )
            }
        }
    }

    private var shareText: String {
        let parseFormatter = DateFormatter()
        parseFormatter.dateFormat = "yyyy-MM-dd"
        let shareFormatter = DateFormatter()
        shareFormatter.dateFormat = "dd.MM.yy"

        let dateStr: String
        if let parsedDate = parseFormatter.date(from: challenge.date) {
            dateStr = shareFormatter.string(from: parsedDate)
        } else {
            dateStr = challenge.date
        }

        let dots = guesses.enumerated().map { index, _ in
            (index == guesses.count - 1 && solved) ? "🟢" : "🔴"
        }.joined()

        var text = "#voyage 🌍 \(challenge.questionType.title) (\(dateStr)) \(attempts)/5\n\(dots)\n"

        if streak > 0 {
            text += "\nMy streak: \(streak) 🔥\n"
        }

        text += "\nGet voyage 🌍 for free: https://apps.apple.com/no/app/voyage-track-your-journey/id6758411779?l=nb"

        return text
    }

    private func isCorrect(_ guess: String) -> Bool {
        switch challenge.questionType {
        case .capital:
            return country.capital?.name.caseInsensitiveCompare(guess) == .orderedSame
        case .country, .flag:
            return country.name.caseInsensitiveCompare(guess) == .orderedSame
        }
    }
}
