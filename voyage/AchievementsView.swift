import SwiftUI

struct AchievementsView: View {
    @ObservedObject var globeState: GlobeState
    @State private var expandedAchievementID: String? = nil
    @State private var medalAchievement: Achievement? = nil
    @State private var medalSourceFrames: [String: CGRect] = [:]

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

        // Seven wonders achievement
        list.append(Achievement(
            name: "Seven wonders of the world",
            medal: "⭐️",
            visitedCountries: SevenWonders.visited(from: globeState.checkedAttractions),
            remainingCountries: SevenWonders.remaining(from: globeState.checkedAttractions),
            itemLabel: "wonders"
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
        ZStack {
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
                                        isExpanded: expandedAchievementID == achievement.id,
                                        isMedalPresented: medalAchievement?.id == achievement.id,
                                        onMedalTap: { medalAchievement = achievement },
                                        onMedalFrameChange: { medalSourceFrames[achievement.id] = $0 }
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

            if let achievement = medalAchievement {
                MedalOverlayView(
                    achievement: achievement,
                    isDarkMode: globeState.isDarkMode,
                    sourceFrame: medalSourceFrames[achievement.id] ?? .zero,
                    onDismissed: { medalAchievement = nil }
                )
                .zIndex(1)
            }
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
    let isMedalPresented: Bool
    let onMedalTap: () -> Void
    let onMedalFrameChange: (CGRect) -> Void

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
                                AppColors.buttonColor,
                            style: StrokeStyle(lineWidth: 4, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: achievement.progress)

                    MedalCardView(achievement: achievement)
                        // Hidden while the 3D medal overlay is up: the overlay's
                        // coin starts exactly on this spot, so it reads as the
                        // small medal itself enlarging
                        .opacity(isMedalPresented ? 0 : 1)
                }
                .frame(width: 56, height: 56)
                .contentShape(Circle())
                .onTapGesture { onMedalTap() }
                .onGeometryChange(for: CGRect.self) { proxy in
                    proxy.frame(in: .global)
                } action: { frame in
                    onMedalFrameChange(frame)
                }

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
                                AppColors.buttonColor
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
