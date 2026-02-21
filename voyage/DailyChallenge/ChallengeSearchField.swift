import SwiftUI

struct ChallengeSearchField: View {
    @Binding var searchText: String
    let suggestions: [String]
    let guessedItems: Set<String>
    let isDarkMode: Bool
    let onSubmit: (String) -> Void

    @State private var showSuggestions = false

    private var filtered: [String] {
        guard !searchText.isEmpty else { return [] }
        return suggestions.filter { $0.localizedCaseInsensitiveContains(searchText) }
    }

    private func isGuessed(_ item: String) -> Bool {
        guessedItems.contains(where: { $0.caseInsensitiveCompare(item) == .orderedSame })
    }

    var body: some View {
        VStack(spacing: 0) {
            // Suggestions open upward, above the text field
            if showSuggestions && !filtered.isEmpty {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered.prefix(5), id: \.self) { suggestion in
                            let guessed = isGuessed(suggestion)
                            Button {
                                searchText = suggestion
                                showSuggestions = false
                                onSubmit(suggestion)
                            } label: {
                                HStack {
                                    Text(suggestion)
                                        .font(.system(size: 15, design: .rounded))
                                        .foregroundColor(guessed
                                            ? AppColors.textMuted(isDarkMode: isDarkMode)
                                            : AppColors.textPrimary(isDarkMode: isDarkMode))
                                    Spacer()
                                    if guessed {
                                        Text("Guessed")
                                            .font(.system(size: 12, design: .rounded))
                                            .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                                    }
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 14)
                            }
                            .disabled(guessed)
                            if suggestion != filtered.prefix(5).last {
                                Divider()
                                    .padding(.leading, 16)
                            }
                        }
                    }
                }
                .frame(maxHeight: 200)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                        .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 8, y: -4)
                )
                .padding(.bottom, 4)
            }

            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))

                TextField("Type your guess...", text: $searchText)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.words)
                    .onChange(of: searchText) {
                        showSuggestions = !searchText.isEmpty
                    }

                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                        showSuggestions = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                    .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 8, y: 2)
            )
        }
    }
}
