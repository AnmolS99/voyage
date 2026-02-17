import SwiftUI

struct ChallengeSearchField: View {
    @Binding var searchText: String
    let suggestions: [String]
    let isDarkMode: Bool
    let onSubmit: (String) -> Void

    @State private var showSuggestions = false

    private var filtered: [String] {
        guard !searchText.isEmpty else { return [] }
        return suggestions.filter { $0.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))

                TextField("Type your guess...", text: $searchText)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.words)
                    .onChange(of: searchText) { _ in
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

            if showSuggestions && !filtered.isEmpty {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered.prefix(5), id: \.self) { suggestion in
                            Button {
                                searchText = suggestion
                                showSuggestions = false
                                onSubmit(suggestion)
                            } label: {
                                HStack {
                                    Text(suggestion)
                                        .font(.system(size: 15, design: .rounded))
                                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                            }
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
                        .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 8, y: 4)
                )
                .padding(.top, 4)
            }
        }
    }
}
