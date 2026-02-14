import SwiftUI

struct AchievementsView: View {
    @ObservedObject var globeState: GlobeState
    @State private var expandedAchievementID: String? = nil

    private var achievements: [Achievement] {
        var list: [Achievement] = []

        // World traveler achievement (first)
        let allCountries = CountryDataCache.shared.countryNames
        let unCountries = allCountries.subtracting(GlobeState.nonUNTerritories)
        let visitedUN = Array(globeState.visitedUNCountries).sorted()
        let remainingUN = Array(unCountries.subtracting(globeState.visitedUNCountries)).sorted()

        list.append(Achievement(
            name: "World Traveler",
            medal: "🌍",
            visitedCountries: visitedUN,
            remainingCountries: remainingUN
        ))

        // Capital Collector achievement
        let countriesWithCapitals = CountryDataCache.shared.countries.filter { country in
            guard country.capital != nil else { return false }
            return unCountries.contains(country.name)
        }
        let visitedCapitals = countriesWithCapitals.filter { country in
            globeState.checkedCitiesForCountry(country.name).contains(country.capital!.name)
        }.map { $0.capital!.name }.sorted()
        let remainingCapitals = countriesWithCapitals.filter { country in
            !globeState.checkedCitiesForCountry(country.name).contains(country.capital!.name)
        }.map { $0.capital!.name }.sorted()

        list.append(Achievement(
            name: "Capital Collector",
            medal: "🏛️",
            visitedCountries: visitedCapitals,
            remainingCountries: remainingCapitals,
            itemLabel: "capitals"
        ))

        // Continent achievements
        for continent in Continent.allCases where continent != .antarctica {
            let countries = continent.countries
            let visited = ContinentData.visitedCountries(in: continent, from: globeState.visitedCountries)
            let visitedSorted = Array(visited).sorted()
            let remainingSorted = Array(countries.subtracting(visited)).sorted()

            list.append(Achievement(
                name: "Explorer of \(continent.rawValue)",
                medal: continent.medal,
                visitedCountries: visitedSorted,
                remainingCountries: remainingSorted
            ))
        }

        return list
    }

    private var completedCount: Int {
        achievements.filter { $0.isCompleted }.count
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Summary header
                    summaryCard

                    // Achievements list
                    VStack(spacing: 12) {
                        ForEach(achievements) { achievement in
                            Button {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    if expandedAchievementID == achievement.id {
                                        expandedAchievementID = nil
                                    } else {
                                        expandedAchievementID = achievement.id
                                    }
                                }
                            } label: {
                                AchievementCard(
                                    achievement: achievement,
                                    isDarkMode: globeState.isDarkMode,
                                    isExpanded: expandedAchievementID == achievement.id
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 16)
            }
            .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
            .navigationTitle("Achievements")
            .navigationBarTitleDisplayMode(.inline)
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }

    private var summaryCard: some View {
        VStack(spacing: 8) {
            Text("\(completedCount) of \(achievements.count)")
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(AppColors.textPrimary(isDarkMode: globeState.isDarkMode))

            Text("Achievements Unlocked")
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: globeState.isDarkMode))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }
}

struct AchievementCard: View {
    let achievement: Achievement
    let isDarkMode: Bool
    let isExpanded: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Main card header
            HStack(spacing: 16) {
                // Progress circle with medal
                ZStack {
                    Circle()
                        .stroke(
                            AppColors.track(isDarkMode: isDarkMode),
                            lineWidth: 4
                        )

                    Circle()
                        .trim(from: 0, to: achievement.progress)
                        .stroke(
                            achievement.isCompleted ?
                                AppColors.buttonVisited :
                                AppColors.buttonColor(isDarkMode: isDarkMode),
                            style: StrokeStyle(lineWidth: 4, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: achievement.progress)

                    Text(achievement.medal)
                        .font(.system(size: 24))
                        .grayscale(achievement.isCompleted ? 0 : 0.8)
                        .opacity(achievement.isCompleted ? 1 : 0.5)
                }
                .frame(width: 56, height: 56)

                VStack(alignment: .leading, spacing: 4) {
                    Text(achievement.name)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                    Text("\(achievement.current)/\(achievement.total) \(achievement.itemLabel)")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                }

                Spacer()

                HStack(spacing: 8) {
                    Text("\(achievement.percentage)%")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundColor(
                            achievement.isCompleted ?
                                AppColors.buttonVisited :
                                AppColors.buttonColor(isDarkMode: isDarkMode)
                        )

                    Image(systemName: "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))
                }
            }
            .padding(16)

            if isExpanded {
                AchievementDetailSection(
                    achievement: achievement,
                    isDarkMode: isDarkMode
                )
                .transition(.opacity)
            }
        }
        .clipped()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    achievement.isCompleted ?
                        AppColors.buttonVisited.opacity(0.5) :
                        Color.clear,
                    lineWidth: 2
                )
        )
        .contentShape(Rectangle())
    }
}

struct AchievementDetailSection: View {
    let achievement: Achievement
    let isDarkMode: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Rectangle()
                .fill(isDarkMode ? AppColors.closeButtonDark : AppColors.trackLight)
                .frame(height: 1)
                .padding(.horizontal, 16)

            if !achievement.visitedCountries.isEmpty {
                CountryListSection(
                    title: "Visited",
                    count: achievement.visitedCountries.count,
                    countries: achievement.visitedCountries,
                    icon: "checkmark.circle.fill",
                    iconColor: AppColors.buttonVisited,
                    isDarkMode: isDarkMode
                )
            }

            if !achievement.remainingCountries.isEmpty {
                CountryListSection(
                    title: "Remaining",
                    count: achievement.remainingCountries.count,
                    countries: achievement.remainingCountries,
                    icon: "circle",
                    iconColor: AppColors.textMuted(isDarkMode: isDarkMode),
                    isDarkMode: isDarkMode
                )
            }
        }
        .padding(.bottom, 16)
    }
}

struct CountryListSection: View {
    let title: String
    let count: Int
    let countries: [String]
    let icon: String
    let iconColor: Color
    let isDarkMode: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12))
                    .foregroundColor(iconColor)

                Text("\(title) (\(count))")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(isDarkMode ? AppColors.badgeTextDark : AppColors.badgeTextLight)
            }
            .padding(.horizontal, 16)

            Text(countries.joined(separator: ", "))
                .font(.system(size: 12, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                .lineLimit(4)
                .padding(.horizontal, 16)
        }
    }
}
