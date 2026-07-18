import SwiftUI

/// Challenges tab: a menu of playable game modes with lifetime statistics.
struct ChallengesView: View {
    @ObservedObject var globeState: GlobeState
    @ObservedObject private var statsStore = ChallengeStatsStore.shared
    @State private var activeGame: ActiveGame?

    private struct ActiveGame: Identifiable {
        let mode: ChallengeGameMode
        let region: ChallengeRegion
        var id: String { "\(mode.rawValue)|\(region.rawValue)" }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    trophyShowcase

                    VStack(spacing: 12) {
                        ForEach(ChallengeGameMode.allCases) { mode in
                            NavigationLink {
                                RegionSelectView(mode: mode, globeState: globeState) { region in
                                    activeGame = ActiveGame(mode: mode, region: region)
                                }
                            } label: {
                                GameModeCard(
                                    mode: mode,
                                    gamesPlayed: statsStore.totalGamesPlayed(for: mode),
                                    isDarkMode: globeState.isDarkMode
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
            .navigationTitle("Challenges")
            .navigationBarTitleDisplayMode(.inline)
        }
        .fullScreenCover(item: $activeGame) { game in
            switch game.mode {
            case .clickCountry:
                ClickCountryGameView(region: game.region, mainState: globeState) {
                    activeGame = nil
                }
            }
        }
        .preferredColorScheme(globeState.isDarkMode ? .dark : .light)
    }

    /// Trophy cabinet: bronze, silver and gold trophies earned by flawless
    /// region sweeps (across all game modes), with counters below.
    private var trophyShowcase: some View {
        HStack(spacing: 0) {
            ForEach(ChallengeTrophy.allCases) { trophy in
                TrophyItem(
                    trophy: trophy,
                    count: statsStore.trophyCount(trophy),
                    isDarkMode: globeState.isDarkMode
                )
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 20)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppColors.cardBackground(isDarkMode: globeState.isDarkMode))
                .shadow(color: .black.opacity(globeState.isDarkMode ? 0.3 : 0.08), radius: 12, y: 4)
        )
        .padding(.horizontal, 20)
    }
}

struct TrophyItem: View {
    let trophy: ChallengeTrophy
    let count: Int
    let isDarkMode: Bool

    private var isEarned: Bool {
        count > 0
    }

    var body: some View {
        VStack(spacing: 8) {
            // Always tier-colored so bronze/silver/gold is readable;
            // outline = not yet earned, filled + glow = earned
            Image(systemName: isEarned ? "trophy.fill" : "trophy")
                .font(.system(size: 34))
                .foregroundStyle(trophy.gradient)
                .opacity(isEarned ? 1 : 0.5)
                .shadow(color: isEarned ? trophy.glowColor.opacity(0.45) : .clear, radius: 8, y: 2)

            Text("\(count)")
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(isEarned ?
                                 AppColors.textPrimary(isDarkMode: isDarkMode) :
                                 AppColors.textMuted(isDarkMode: isDarkMode))

            Text(trophy.displayName)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
        }
    }
}

struct GameModeCard: View {
    let mode: ChallengeGameMode
    let gamesPlayed: Int
    let isDarkMode: Bool

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(AppColors.buttonColor.opacity(0.15))
                Image(systemName: mode.icon)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(AppColors.buttonColor)
            }
            .frame(width: 56, height: 56)

            VStack(alignment: .leading, spacing: 4) {
                Text(mode.title)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))
                Text(mode.subtitle)
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))
                Text(gamesPlayed > 0 ? "Played \(gamesPlayed) time\(gamesPlayed == 1 ? "" : "s")" : "Not played yet")
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
        )
        .contentShape(Rectangle())
    }
}

/// Region picker for a game mode: World or a single continent, with each
/// region's lifetime stats (best result and games played).
struct RegionSelectView: View {
    let mode: ChallengeGameMode
    @ObservedObject var globeState: GlobeState
    let onPlay: (ChallengeRegion) -> Void

    @ObservedObject private var statsStore = ChallengeStatsStore.shared

    init(mode: ChallengeGameMode, globeState: GlobeState, onPlay: @escaping (ChallengeRegion) -> Void) {
        self.mode = mode
        self.globeState = globeState
        self.onPlay = onPlay
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                ForEach(ChallengeRegion.allCases) { region in
                    Button {
                        onPlay(region)
                    } label: {
                        RegionCard(
                            region: region,
                            stats: statsStore.stats(for: mode, region: region),
                            isDarkMode: globeState.isDarkMode
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .background(AppColors.pageBackground(isDarkMode: globeState.isDarkMode))
        .navigationTitle(mode.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct RegionCard: View {
    let region: ChallengeRegion
    let stats: ChallengeGameStats
    let isDarkMode: Bool

    /// A flawless sweep (100%) completes the region and earns its trophy;
    /// completed cards get a green ring, mirroring completed achievements.
    private var isCompleted: Bool {
        stats.isPerfect
    }

    var body: some View {
        HStack(spacing: 16) {
            Text(region.emoji)
                .font(.system(size: 34))

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(region.displayName)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundColor(AppColors.textPrimary(isDarkMode: isDarkMode))

                    // The trophy at stake, always in its tier color:
                    // outline = not yet earned, filled = earned
                    Image(systemName: isCompleted ? "trophy.fill" : "trophy")
                        .font(.system(size: 13))
                        .foregroundStyle(region.trophy.gradient)
                        .opacity(isCompleted ? 1 : 0.55)
                }

                Text("\(region.countries.count) countries")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(AppColors.textTertiary(isDarkMode: isDarkMode))

                statsLine
            }

            Spacer()

            Image(systemName: "play.circle.fill")
                .font(.system(size: 30))
                .foregroundColor(AppColors.buttonColor)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(AppColors.cardBackground(isDarkMode: isDarkMode))
                .shadow(color: .black.opacity(isDarkMode ? 0.2 : 0.06), radius: 8, y: 2)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    isCompleted ? AppColors.buttonVisited.opacity(0.5) : Color.clear,
                    lineWidth: 2
                )
        )
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var statsLine: some View {
        if let percentage = stats.bestPercentage, let time = stats.bestScoreTime {
            Text("Best \(percentage)% · \(formatGameTime(time)) · Played \(stats.gamesPlayed) time\(stats.gamesPlayed == 1 ? "" : "s")")
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(isCompleted ? AppColors.buttonVisited : AppColors.buttonColor)
        } else {
            Text("Not played yet")
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(AppColors.textMuted(isDarkMode: isDarkMode))
        }
    }
}
