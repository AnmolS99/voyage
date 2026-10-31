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

/// Painted header art for the Explore sheet, one day (light mode) and one night
/// (dark mode) image per country, made by `scripts/journey_art.py` and bundled
/// from `shared/data/journey/` as `<ISO>_day.webp` / `<ISO>_night.webp`.
/// Countries without art yet keep the plain header.
enum JourneyArt {
    /// The banner's shape: the Journey design's 390 x 240 pt header.
    static let aspectRatio: CGFloat = 390.0 / 240.0

    private static var cache: [String: UIImage] = [:]

    static func image(for countryCode: String, isDarkMode: Bool) -> UIImage? {
        let name = "\(countryCode)_\(isDarkMode ? "night" : "day")"
        if let cached = cache[name] { return cached }
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp", subdirectory: "journey"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        cache[name] = image
        return image
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

    private var journeyArt: UIImage? {
        guard let code = country?.flagCode else { return nil }
        return JourneyArt.image(for: code, isDarkMode: globeState.isDarkMode)
    }

    var body: some View {
        let art = journeyArt
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    if let art {
                        JourneyHeader(
                            image: art,
                            flag: globeState.flagForCountry(countryName),
                            countryName: countryName,
                            isDarkMode: globeState.isDarkMode
                        )
                    }

                    VStack(spacing: 24) {
                        // Without art, the plain header: name, capital and a flag card.
                        // With it, the name and flag sit on the art, and the capital
                        // is marked in Top Cities.
                        if art == nil {
                            Text(countryName)
                                .font(.voyageDisplay(32))
                                .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))

                            if let capitalName = country?.capital?.name {
                                Text(capitalName)
                                    .font(.voyageText(18, weight: .medium))
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
                        }

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
            }
            // The art runs to the sheet's top edge, under the Done button.
            .ignoresSafeArea(.container, edges: art == nil ? [] : .top)
            .toolbarBackground(art == nil ? .automatic : .hidden, for: .navigationBar)
            .hidingTopScrollEdgeEffect(art != nil)
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .preferredColorScheme(globeState.themeMode.colorScheme)
    }
}

/// The Journey design's header: the country's painted art full-bleed, with its
/// flag and name over the top-left, which the art keeps as calm sky.
struct JourneyHeader: View {
    let image: UIImage
    let flag: String
    let countryName: String
    let isDarkMode: Bool

    var body: some View {
        Image(uiImage: image)
            .resizable()
            .aspectRatio(JourneyArt.aspectRatio, contentMode: .fill)
            .frame(maxWidth: .infinity)
            .clipped()
            .overlay(alignment: .bottom) {
                // Dissolves the art into the page instead of ending it on a hard edge.
                let page = AppColors.pageBackground(isDarkMode: isDarkMode)
                LinearGradient(
                    stops: [
                        .init(color: page.opacity(0), location: 0),
                        .init(color: page.opacity(0.55), location: 0.55),
                        .init(color: page, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 56)
            }
            .overlay(alignment: .topLeading) {
                HStack(spacing: 12) {
                    Text(flag)
                        .font(.system(size: 40))
                    Text(countryName)
                        .font(.voyageDisplay(40, weight: .medium))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                }
                .padding(.leading, 20)
                .padding(.trailing, 96) // clear of the Done button
                .padding(.top, 14)
            }
            .accessibilityElement(children: .combine)
    }
}

private extension View {
    /// iOS 26 blurs and fades whatever scrolls under the toolbar, which washes
    /// out the title laid over the Journey art.
    @ViewBuilder
    func hidingTopScrollEdgeEffect(_ hidden: Bool) -> some View {
        if #available(iOS 26, *) {
            scrollEdgeEffectHidden(hidden, for: .top)
        } else {
            self
        }
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
                    .foregroundColor(AppColors.buttonColor)
                Text(title)
                    .font(.voyageDisplay(20))
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
                                .font(.voyageText(16, weight: .medium))
                                .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                            if item == capitalName {
                                Image(systemName: "building.columns.fill")
                                    .font(.system(size: 13))
                                    .foregroundColor(AppColors.buttonColor)
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
