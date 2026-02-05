import SwiftUI

struct CountryListView: View {
    @ObservedObject var globeState: GlobeState
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    private var filteredCountries: [GeoJSONCountry] {
        let countries = CountryDataCache.shared.countries.sorted { $0.name < $1.name }
        if searchText.isEmpty {
            return countries
        }
        return countries.filter {
            $0.name.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationStack {
            List(filteredCountries, id: \.name) { country in
                CountryRow(
                    country: country,
                    globeState: globeState,
                    onSelect: { navigateToCountry(country) }
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
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
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
    @ObservedObject var globeState: GlobeState
    let onSelect: () -> Void

    private var isVisited: Bool { globeState.isVisited(country.name) }
    private var isWishlist: Bool { globeState.isInWishlist(country.name) }

    var body: some View {
        HStack(spacing: 12) {
            Text(globeState.flagForCountry(country.name))
                .font(.system(size: 24))

            Text(country.name)
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))

            Spacer()

            HStack(spacing: 4) {
                Button(action: toggleVisited) {
                    Image(systemName: isVisited ? "checkmark.circle.fill" : "plus.circle")
                        .font(.system(size: 20))
                        .foregroundColor(isVisited ? AppColors.buttonVisited : AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                }
                .buttonStyle(.plain)

                Button(action: toggleWishlist) {
                    Image(systemName: isWishlist ? "heart.fill" : "heart")
                        .font(.system(size: 20))
                        .foregroundColor(isWishlist ? AppColors.wishlist : AppColors.buttonColor(isDarkMode: globeState.isDarkMode))
                }
                .buttonStyle(.plain)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onSelect() }
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
