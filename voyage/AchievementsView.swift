import SwiftUI

struct Achievement: Identifiable {
    let id = UUID()
    let name: String
    let medal: String
    let current: Int
    let total: Int

    var isCompleted: Bool { current >= total }
    var progress: Double { total > 0 ? Double(current) / Double(total) : 0 }
    var percentage: Int { Int(progress * 100) }
}

struct AchievementsView: View {
    @ObservedObject var globeState: GlobeState
    @Environment(\.dismiss) private var dismiss

    private var achievements: [Achievement] {
        var list: [Achievement] = []

        // World traveler achievement (first)
        list.append(Achievement(
            name: "World Traveler",
            medal: "🌍",
            current: globeState.visitedUNCountries.count,
            total: globeState.totalUNCountries
        ))

        // Continent achievements
        for continent in Continent.allCases where continent != .antarctica {
            let countries = continent.countries
            let visited = ContinentData.visitedCountries(in: continent, from: globeState.visitedCountries)
            list.append(Achievement(
                name: "Explorer of \(continent.rawValue)",
                medal: continent.medal,
                current: visited.count,
                total: countries.count
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
                            AchievementCard(
                                achievement: achievement,
                                isDarkMode: globeState.isDarkMode
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 16)
            }
            .background(
                globeState.isDarkMode ?
                    Color(red: 0.1, green: 0.1, blue: 0.12) :
                    Color(red: 0.96, green: 0.95, blue: 0.93)
            )
            .navigationTitle("Achievements")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(globeState.isDarkMode ?
                        Color(red: 0.6, green: 0.5, blue: 0.8) :
                        Color(red: 0.85, green: 0.55, blue: 0.35))
                }
            }
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }

    private var summaryCard: some View {
        VStack(spacing: 8) {
            Text("\(completedCount) of \(achievements.count)")
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(globeState.isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))

            Text("Achievements Unlocked")
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(globeState.isDarkMode ?
                    Color(red: 0.7, green: 0.7, blue: 0.75) :
                    Color(red: 0.5, green: 0.45, blue: 0.4))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(globeState.isDarkMode ?
                    Color(red: 0.2, green: 0.2, blue: 0.25) :
                    .white)
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }
}

struct AchievementCard: View {
    let achievement: Achievement
    let isDarkMode: Bool

    var body: some View {
        HStack(spacing: 16) {
            // Progress circle with medal
            ZStack {
                // Background circle
                Circle()
                    .stroke(
                        isDarkMode ?
                            Color(red: 0.25, green: 0.25, blue: 0.3) :
                            Color(red: 0.9, green: 0.88, blue: 0.85),
                        lineWidth: 4
                    )

                // Progress arc
                Circle()
                    .trim(from: 0, to: achievement.progress)
                    .stroke(
                        achievement.isCompleted ?
                            Color(red: 0.3, green: 0.7, blue: 0.4) :
                            (isDarkMode ?
                                Color(red: 0.5, green: 0.4, blue: 0.8) :
                                Color(red: 0.85, green: 0.55, blue: 0.35)),
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .animation(.spring(response: 0.5, dampingFraction: 0.8), value: achievement.progress)

                // Medal
                Text(achievement.medal)
                    .font(.system(size: 24))
                    .grayscale(achievement.isCompleted ? 0 : 0.8)
                    .opacity(achievement.isCompleted ? 1 : 0.5)
            }
            .frame(width: 56, height: 56)

            // Achievement info
            VStack(alignment: .leading, spacing: 4) {
                Text(achievement.name)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(isDarkMode ? .white : Color(red: 0.2, green: 0.15, blue: 0.1))

                Text("\(achievement.current)/\(achievement.total) countries")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(isDarkMode ?
                        Color(red: 0.6, green: 0.6, blue: 0.65) :
                        Color(red: 0.5, green: 0.45, blue: 0.4))
            }

            Spacer()

            // Percentage
            Text("\(achievement.percentage)%")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(
                    achievement.isCompleted ?
                        Color(red: 0.3, green: 0.7, blue: 0.4) :
                        (isDarkMode ?
                            Color(red: 0.6, green: 0.5, blue: 0.8) :
                            Color(red: 0.85, green: 0.55, blue: 0.35))
                )
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(isDarkMode ?
                    Color(red: 0.2, green: 0.2, blue: 0.25) :
                    .white)
                .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    achievement.isCompleted ?
                        Color(red: 0.3, green: 0.7, blue: 0.4).opacity(0.5) :
                        Color.clear,
                    lineWidth: 2
                )
        )
    }
}
