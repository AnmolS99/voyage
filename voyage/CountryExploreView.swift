import SwiftUI

struct CountryHighlights: Codable {
    let cities: [String]
    let attractions: [String]
}

class CountryHighlightsParser {
    static func loadHighlights() -> [String: CountryHighlights] {
        guard let url = Bundle.main.url(forResource: "country_highlights", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let highlights = try? JSONDecoder().decode([String: CountryHighlights].self, from: data) else {
            print("Failed to load country highlights")
            return [:]
        }
        return highlights
    }
}

struct CountryExploreView: View {
    @ObservedObject var globeState: GlobeState
    let countryName: String
    @Environment(\.dismiss) private var dismiss

    private var country: GeoJSONCountry? {
        CountryDataCache.shared.countries.first { $0.name == countryName }
    }

    private var highlights: CountryHighlights? {
        guard let code = country?.flagCode else { return nil }
        return CountryDataCache.shared.countryHighlights[code]
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    Text(countryName)
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))

                    if let capitalName = country?.capital?.name {
                        Text(capitalName)
                            .font(.system(size: 18, weight: .medium, design: .rounded))
                            .foregroundColor(AppColors.textSecondary(isDarkMode: globeState.isDarkMode))
                    }

                    Text(globeState.flagForCountry(countryName))
                        .font(.system(size: 80))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 20)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                        )

                    if let highlights = highlights {
                        ChecklistSection(
                            title: "Top Cities",
                            icon: "building.2.fill",
                            items: highlights.cities,
                            checkedItems: globeState.checkedCitiesForCountry(countryName),
                            isDarkMode: globeState.isDarkMode,
                            capitalName: country?.capital?.name,
                            onToggle: { globeState.toggleCheckedCity($0, for: countryName) }
                        )

                        ChecklistSection(
                            title: "Top Attractions",
                            icon: "star.fill",
                            items: highlights.attractions,
                            checkedItems: globeState.checkedAttractionsForCountry(countryName),
                            isDarkMode: globeState.isDarkMode,
                            onToggle: { globeState.toggleCheckedAttraction($0, for: countryName) }
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }
}

struct ChecklistSection: View {
    let title: String
    let icon: String
    let items: [String]
    let checkedItems: Set<String>
    let isDarkMode: Bool
    var capitalName: String? = nil
    let onToggle: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(AppColors.buttonColor(isDarkMode: isDarkMode))
                Text(title)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
            }

            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element) { index, item in
                    Button(action: {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            onToggle(item)
                        }
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: checkedItems.contains(item) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 22))
                                .foregroundColor(checkedItems.contains(item) ?
                                    AppColors.buttonVisited :
                                    AppColors.textMuted(isDarkMode: isDarkMode))

                            Text(item)
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                            if item == capitalName {
                                Image(systemName: "building.columns.fill")
                                    .font(.system(size: 13))
                                    .foregroundColor(AppColors.buttonColor(isDarkMode: isDarkMode))
                            }

                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 16)
                    }
                    .buttonStyle(.plain)

                    if index < items.count - 1 {
                        Divider()
                            .padding(.leading, 50)
                    }
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                    .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
            )
        }
    }
}
