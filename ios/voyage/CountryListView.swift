import SwiftUI

/// Which countries the country list shows: every one, the visited ones or the
/// wishlisted ones — Android `CountryListFilter`, same cases and rules.
enum CountryListFilter: String, CaseIterable, Identifiable {
    case all, visited, wishlist

    var id: String { rawValue }

    /// The countries this filter lists right now; nil for `.all`, which lists every one.
    func members(visited: Set<String>, wishlist: Set<String>) -> Set<String>? {
        switch self {
        case .all: nil
        case .visited: visited
        case .wishlist: wishlist
        }
    }

    /// The empty search's explanation, naming the list it searched.
    func noMatchesDescription(_ query: String) -> String {
        switch self {
        case .all: "No countries match “\(query)”."
        case .visited: "No visited countries match “\(query)”."
        case .wishlist: "No wishlisted countries match “\(query)”."
        }
    }

    /// Whether the list shows `name`. `kept` is what the filter listed when it
    /// was chosen: a country un-visited (or un-wished) from the filtered list
    /// stays in it, unmarked, so a mis-tap is one tap to undo rather than a row
    /// that vanishes. It leaves when another filter is chosen or the list closes.
    func includes(_ name: String, visited: Set<String>, wishlist: Set<String>, kept: Set<String> = []) -> Bool {
        guard let members = members(visited: visited, wishlist: wishlist) else { return true }
        return members.contains(name) || kept.contains(name)
    }
}

struct CountryListView: View {
    let globeState: GlobeState
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    @State private var filter: CountryListFilter
    /// See `CountryListFilter.includes`.
    @State private var kept: Set<String>
    /// Matching and ordering, by name and by alias ("uk", "england", "burma").
    /// Built once: the country list never changes.
    private static let index = CountrySearchIndex<GeoJSONCountry>.countries(CountryDataCache.shared.countries)

    init(globeState: GlobeState, filter: CountryListFilter = .all) {
        self.globeState = globeState
        _filter = State(initialValue: filter)
        _kept = State(initialValue: filter.members(
            visited: globeState.visitedCountries, wishlist: globeState.wishlistCountries
        ) ?? [])
    }

    private var results: [SearchMatch<GeoJSONCountry>] {
        let visited = globeState.visitedCountries
        let wishlist = globeState.wishlistCountries
        return Self.index.search(searchText).filter {
            filter.includes($0.item.name, visited: visited, wishlist: wishlist, kept: kept)
        }
    }

    var body: some View {
        NavigationStack {
            let shown = results
            List(shown, id: \.item.name) { match in
                CountryRow(
                    country: match.item,
                    matchedAlias: match.matchedAlias,
                    globeState: globeState,
                    onSelect: { navigateToCountry(match.item) }
                )
            }
            .overlay {
                if shown.isEmpty { emptyState }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                CountryFilterBar(
                    selection: filter,
                    visitedCount: globeState.visitedCountries.count,
                    wishlistCount: globeState.wishlistCountries.count,
                    isDarkMode: globeState.isDarkMode,
                    onSelect: choose
                )
                .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            }
            .searchable(text: $searchText, prompt: "Search countries")
            .scrollContentBackground(.hidden)
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .navigationTitle("Countries")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .sensoryFeedback(.selection, trigger: filter)
        }
        .preferredColorScheme(globeState.colorScheme)
    }

    private func choose(_ newFilter: CountryListFilter) {
        withAnimation(.snappy(duration: 0.25)) {
            filter = newFilter
            kept = newFilter.members(
                visited: globeState.visitedCountries, wishlist: globeState.wishlistCountries
            ) ?? []
        }
    }

    /// Nothing listed: a search that found nothing, or a filter with nothing in
    /// it yet. Either way a filtered list offers the way back to every country.
    @ViewBuilder
    private var emptyState: some View {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        if !query.isEmpty {
            ContentUnavailableView {
                Label("No Matches", systemImage: "magnifyingglass")
            } description: {
                Text(filter.noMatchesDescription(query))
            } actions: {
                if filter != .all {
                    Button("Search All Countries") { choose(.all) }
                }
            }
        } else if filter == .visited {
            ContentUnavailableView {
                Label("No Visited Countries Yet", systemImage: "checkmark.circle")
            } description: {
                Text("Tap \(Image(systemName: "plus.circle")) beside a country you've been to.")
            } actions: {
                Button("Show All Countries") { choose(.all) }
            }
        } else if filter == .wishlist {
            ContentUnavailableView {
                Label("Your Wishlist Is Empty", systemImage: "heart")
            } description: {
                Text("Tap \(Image(systemName: "heart")) beside a country you'd like to see.")
            } actions: {
                Button("Show All Countries") { choose(.all) }
            }
        }
    }

    private func navigateToCountry(_ country: GeoJSONCountry) {
        let center = getCountryCenter(country)
        globeState.selectCountry(country.name, center: center)
        dismiss()
    }

    private func getCountryCenter(_ country: GeoJSONCountry) -> (lat: Double, lon: Double)? {
        if country.isPointCountry, let coord = country.pointCoordinate {
            return coord
        }

        var totalLat = 0.0
        var totalLon = 0.0
        var count = 0

        for polygon in country.polygons {
            for coord in polygon {
                totalLon += coord.lon
                totalLat += coord.lat
                count += 1
            }
        }

        guard count > 0 else { return nil }
        return (lat: totalLat / Double(count), lon: totalLon / Double(count))
    }
}

/// All · Visited · Wishlist, pinned under the title. Each chip carries its
/// count and, as on the rows, its status's icon and color: the visited check
/// in green, the wishlist heart in purple. The chosen one fills with that color.
private struct CountryFilterBar: View {
    let selection: CountryListFilter
    let visitedCount: Int
    let wishlistCount: Int
    let isDarkMode: Bool
    let onSelect: (CountryListFilter) -> Void

    var body: some View {
        HStack(spacing: 8) {
            chip(.all, title: "All", symbol: nil, count: nil, tint: AppColors.buttonColor)
            chip(.visited, title: "Visited", symbol: "checkmark.circle.fill", count: visitedCount,
                 tint: AppColors.buttonVisited)
            chip(.wishlist, title: "Wishlist", symbol: "heart.fill", count: wishlistCount,
                 tint: AppColors.wishlist)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, 4)
        .padding(.bottom, 8)
    }

    private func chip(_ filter: CountryListFilter, title: String, symbol: String?, count: Int?, tint: Color) -> some View {
        let isSelected = selection == filter
        return Button { onSelect(filter) } label: {
            HStack(spacing: 5) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(isSelected ? Color.white : tint)
                }
                Text(title)
                    .font(.voyageText(14, weight: .semibold))
                if let count {
                    Text("\(count)")
                        .font(.voyageText(14, weight: .medium))
                        .monospacedDigit()
                        .opacity(isSelected ? 0.85 : 0.55)
                        .contentTransition(.numericText(value: Double(count)))
                }
            }
            .foregroundStyle(isSelected ? Color.white : AppColors.textPrimary(isDarkMode: isDarkMode))
            .padding(.horizontal, 13)
            .frame(height: 34)
            .background(Capsule().fill(isSelected ? tint : Color.primary.opacity(0.08)))
            .contentShape(Capsule())
            .animation(.snappy(duration: 0.2), value: count)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(count.map { "\(title), \($0)" } ?? title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

struct CountryRow: View {
    let country: GeoJSONCountry
    /// The alias the search found this country by, shown under its name so
    /// the match is clear. Nil when the name itself matched.
    var matchedAlias: CountryAlias? = nil
    let globeState: GlobeState
    let onSelect: () -> Void

    @State private var showingExplore = false

    private var isVisited: Bool { globeState.isVisited(country.name) }
    private var isWishlist: Bool { globeState.isInWishlist(country.name) }

    var body: some View {
        HStack(spacing: 12) {
            Text(globeState.flagForCountry(country.name))
                .font(.system(size: 24))

            VStack(alignment: .leading, spacing: 2) {
                Text(country.name)
                    .font(.voyageText(16, weight: .medium))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))
                if let matchedAlias {
                    Text(matchedAlias.label)
                        .font(.voyageText(13))
                        .foregroundColor(AppColors.textMuted(isDarkMode: globeState.isDarkMode))
                }
            }

            Spacer()

            HStack(spacing: 4) {
                Button(action: toggleVisited) {
                    Image(systemName: isVisited ? "checkmark.circle.fill" : "plus.circle")
                        .font(.system(size: 20))
                        .foregroundColor(isVisited ? AppColors.buttonVisited : AppColors.buttonColor)
                }
                .buttonStyle(.plain)

                Button(action: toggleWishlist) {
                    Image(systemName: isWishlist ? "heart.fill" : "heart")
                        .font(.system(size: 20))
                        .foregroundColor(isWishlist ? AppColors.wishlist : AppColors.buttonColor)
                }
                .buttonStyle(.plain)

                Button { showingExplore = true } label: {
                    Image(systemName: "binoculars.fill")
                        .font(.system(size: 20))
                        .foregroundColor(AppColors.buttonColor)
                }
                .buttonStyle(.plain)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onSelect() }
        .sheet(isPresented: $showingExplore) {
            CountryExploreView(globeState: globeState, countryName: country.name)
        }
    }

    private func toggleVisited() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            if isVisited {
                globeState.removeVisit(country.name)
            } else {
                globeState.addVisit(country.name)
            }
        }
    }

    private func toggleWishlist() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            if isWishlist {
                globeState.removeFromWishlist(country.name)
            } else {
                globeState.addToWishlist(country.name)
            }
        }
    }
}
