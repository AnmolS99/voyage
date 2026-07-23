import SwiftUI

extension String {
    /// Case-insensitive prefix test used by suggestion ranking, so a typed
    /// query like "Os" counts "Oslo" as a prefix match but "Buenos Aires" not.
    func hasCaseInsensitivePrefix(_ prefix: String) -> Bool {
        range(of: prefix, options: [.caseInsensitive, .anchored]) != nil
    }
}

extension Array where Element == String {
    /// Filters to elements containing `query` (case-insensitive), ranking
    /// those that *begin* with `query` above interior matches — so typing
    /// "Os" surfaces "Oslo" before "Buenos Aires". Ties preserve the
    /// receiver's existing order (callers pass alphabetically sorted lists),
    /// keeping the sort stable.
    func rankedMatches(for query: String) -> [String] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return [] }
        return enumerated()
            .filter { $0.element.localizedCaseInsensitiveContains(trimmed) }
            .sorted { lhs, rhs in
                let lPrefix = lhs.element.hasCaseInsensitivePrefix(trimmed)
                let rPrefix = rhs.element.hasCaseInsensitivePrefix(trimmed)
                if lPrefix != rPrefix { return lPrefix }
                return lhs.offset < rhs.offset
            }
            .map(\.element)
    }
}

/// Floating suggestion dropdown: hugs its rows (up to 5 shown), capped at
/// 200pt where it scrolls. Used by `ChallengeSearchField` and floated above
/// the native search field in Name the Capital.
struct ChallengeSuggestionList: View {
    /// Filtered matches; the list shows the first five.
    let suggestions: [String]
    let guessedItems: Set<String>
    let isDarkMode: Bool
    /// Liquid glass styling (iOS 26) for in-game HUDs.
    var usesGlass: Bool = false
    let onSelect: (String) -> Void

    /// Measured height of the rows, so the dropdown hugs them (a ScrollView
    /// would otherwise greedily fill the whole height cap).
    @State private var measuredHeight: CGFloat = 0

    private static let maxHeight: CGFloat = 200

    private var shown: [String] { Array(suggestions.prefix(5)) }

    private func isGuessed(_ item: String) -> Bool {
        guessedItems.contains(where: { $0.caseInsensitiveCompare(item) == .orderedSame })
    }

    var body: some View {
        if usesGlass, #available(iOS 26, *) {
            list
                .glassEffect(in: .rect(cornerRadius: 20))
        } else {
            list
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                        .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 8, y: -4)
                )
        }
    }

    private var list: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(shown, id: \.self) { suggestion in
                    let guessed = isGuessed(suggestion)
                    Button {
                        onSelect(suggestion)
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
                    if suggestion != shown.last {
                        Divider()
                            .padding(.leading, 16)
                    }
                }
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { height in
                measuredHeight = height
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: min(measuredHeight, Self.maxHeight))
    }
}

/// Floating suggestion dropdown for a guess-entry field: ranks `suggestions`
/// against `query` (prefix matches first), greys out already-guessed items, and
/// reports the tapped suggestion. Renders nothing when the query has no matches.
/// Shared by the Daily Challenge and the Challenges game modes so suggestions
/// look and behave identically everywhere.
struct GuessSuggestionDropdown: View {
    let suggestions: [String]
    let query: String
    let guessedItems: Set<String>
    let isDarkMode: Bool
    /// Liquid-glass styling to sit above the native iOS 26 search field.
    var usesGlass: Bool = false
    let onSelect: (String) -> Void

    var body: some View {
        let matches = suggestions.rankedMatches(for: query)
        if !matches.isEmpty {
            ChallengeSuggestionList(
                suggestions: matches,
                guessedItems: guessedItems,
                isDarkMode: isDarkMode,
                usesGlass: usesGlass,
                onSelect: onSelect
            )
        }
    }
}

@available(iOS 26, *)
extension View {
    /// The app's default guess-entry search (iOS 26+): pins the system
    /// `.searchable` field into the bottom toolbar — liquid glass and
    /// thumb-reachable — and clears the text after each submit. Callers float a
    /// `GuessSuggestionDropdown` above it to show suggestions.
    func bottomBarGuessSearch(
        text: Binding<String>,
        prompt: String = "Type your guess...",
        focused: FocusState<Bool>.Binding? = nil,
        onSubmit: @escaping (String) -> Void
    ) -> some View {
        self
            .toolbar { DefaultToolbarItem(kind: .search, placement: .bottomBar) }
            .submittableGuessSearch(text: text, prompt: prompt, focused: focused, onSubmit: onSubmit)
    }

    /// Same as `bottomBarGuessSearch`, plus a `trailing` control pinned beside
    /// the field in the bottom toolbar (e.g. a restart button). Both items share
    /// one `.toolbar` block so their left-to-right order is preserved.
    func bottomBarGuessSearch<Trailing: View>(
        text: Binding<String>,
        prompt: String = "Type your guess...",
        focused: FocusState<Bool>.Binding? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing,
        onSubmit: @escaping (String) -> Void
    ) -> some View {
        self
            .toolbar {
                DefaultToolbarItem(kind: .search, placement: .bottomBar)
                ToolbarItem(placement: .bottomBar) { trailing() }
            }
            .submittableGuessSearch(text: text, prompt: prompt, focused: focused, onSubmit: onSubmit)
    }

    private func submittableGuessSearch(
        text: Binding<String>,
        prompt: String,
        focused: FocusState<Bool>.Binding? = nil,
        onSubmit: @escaping (String) -> Void
    ) -> some View {
        let field = self
            .searchable(text: text, prompt: prompt)
            .onSubmit(of: .search) {
                onSubmit(text.wrappedValue)
                text.wrappedValue = ""
            }
        // `.searchFocused` needs a concrete binding, so branch on whether a
        // host opted into programmatic focus (e.g. the daily challenge
        // auto-focusing the field when it opens).
        return Group {
            if let focused {
                field.searchFocused(focused)
            } else {
                field
            }
        }
    }
}

struct ChallengeSearchField: View {
    @Binding var searchText: String
    let suggestions: [String]
    let guessedItems: Set<String>
    let isDarkMode: Bool
    /// Liquid glass styling (iOS 26) for in-game HUDs; the default card look
    /// is kept for the daily challenge.
    var usesGlass: Bool = false
    /// Reports keyboard focus changes so a host can adapt its layout (e.g.
    /// hide an adjacent button while typing).
    var onFocusChange: ((Bool) -> Void)? = nil
    /// When true, the field grabs keyboard focus as soon as it appears so the
    /// user can start typing immediately (used by the daily challenge).
    var autofocus: Bool = false
    let onSubmit: (String) -> Void

    @State private var showSuggestions = false
    @FocusState private var isFieldFocused: Bool

    /// Fixed height of the glass field row, so in-game HUD buttons can match.
    static let fieldHeight: CGFloat = 46

    private var filtered: [String] {
        suggestions.rankedMatches(for: searchText)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Suggestions open upward, above the text field
            if showSuggestions && !filtered.isEmpty {
                ChallengeSuggestionList(
                    suggestions: filtered,
                    guessedItems: guessedItems,
                    isDarkMode: isDarkMode,
                    usesGlass: usesGlass,
                    onSelect: { suggestion in
                        searchText = suggestion
                        showSuggestions = false
                        onSubmit(suggestion)
                    }
                )
                .padding(.bottom, 4)
            }

            // The field itself is a capsule under glass — the native iOS 26
            // search-field shape — and the classic rounded card otherwise
            if usesGlass, #available(iOS 26, *) {
                fieldRow
                    .glassEffect()
            } else {
                fieldRow
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                            .shadow(color: .black.opacity(isDarkMode ? 0.3 : 0.08), radius: 8, y: 2)
                    )
            }
        }
        .onAppear {
            guard autofocus else { return }
            // A short hop past the first layout pass lets the field install
            // before it claims focus, which the keyboard reliably follows.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                isFieldFocused = true
            }
        }
    }

    private var fieldRow: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))

            TextField("Type your guess...", text: $searchText)
                .textFieldStyle(.plain)
                .font(.system(size: 16, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                .autocorrectionDisabled()
                .textInputAutocapitalization(.words)
                .focused($isFieldFocused)
                .onChange(of: searchText) {
                    showSuggestions = !searchText.isEmpty
                }
                .onChange(of: isFieldFocused) { _, focused in
                    onFocusChange?(focused)
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
        // Glass: fixed height shared with the HUD buttons beside it.
        // Card (daily challenge): original padding-derived height.
        .frame(height: usesGlass ? Self.fieldHeight : nil)
        .padding(.vertical, usesGlass ? 0 : 12)
    }
}
