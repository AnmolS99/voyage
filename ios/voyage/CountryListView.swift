import SwiftUI

struct CountryListView: View {
    @ObservedObject var globeState: GlobeState
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    /// Matching and ordering, by name and by alias ("uk", "england", "burma").
    /// Built once: the country list never changes.
    private static let index = CountrySearchIndex<GeoJSONCountry>.countries(CountryDataCache.shared.countries)

    private var results: [SearchMatch<GeoJSONCountry>] {
        Self.index.search(searchText)
    }

    var body: some View {
        NavigationStack {
            List(results, id: \.item.name) { match in
                CountryRow(
                    country: match.item,
                    matchedAlias: match.matchedAlias,
                    globeState: globeState,
                    onSelect: { navigateToCountry(match.item) }
                )
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
        }
        .preferredColorScheme(globeState.themeMode.colorScheme)
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
                if coord.count >= 2 {
                    totalLon += coord[0]
                    totalLat += coord[1]
                    count += 1
                }
            }
        }

        guard count > 0 else { return nil }
        return (lat: totalLat / Double(count), lon: totalLon / Double(count))
    }
}

struct CountryRow: View {
    let country: GeoJSONCountry
    /// The alias the search found this country by, shown under its name so
    /// the match is clear. Nil when the name itself matched.
    var matchedAlias: CountryAlias? = nil
    @ObservedObject var globeState: GlobeState
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
