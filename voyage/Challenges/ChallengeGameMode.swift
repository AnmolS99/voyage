import SwiftUI

/// Trophy tiers awarded for flawless (100%) region sweeps in any challenge
/// game mode: bronze for the small continents, silver for the large ones,
/// gold for the whole world.
enum ChallengeTrophy: String, CaseIterable, Identifiable {
    case bronze, silver, gold

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .bronze: return "Bronze"
        case .silver: return "Silver"
        case .gold: return "Gold"
        }
    }

    var gradient: LinearGradient {
        let colors: [Color]
        switch self {
        case .bronze: colors = [AppColors.trophyBronzeLight, AppColors.trophyBronzeDark]
        case .silver: colors = [AppColors.trophySilverLight, AppColors.trophySilverDark]
        case .gold: colors = [AppColors.trophyGoldLight, AppColors.trophyGoldDark]
        }
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var glowColor: Color {
        switch self {
        case .bronze: return AppColors.trophyBronzeLight
        case .silver: return AppColors.trophySilverLight
        case .gold: return AppColors.trophyGoldLight
        }
    }
}

/// Game modes playable from the Challenges tab. Adding a mode means adding a
/// case here plus its own game view/view model; statistics are stored per
/// mode + region in `ChallengeStatsStore`.
enum ChallengeGameMode: String, CaseIterable, Identifiable {
    case clickCountry
    case nameCapital

    var id: String { rawValue }

    var title: String {
        switch self {
        case .clickCountry: return "Click the Country"
        case .nameCapital: return "Name the Capital"
        }
    }

    var subtitle: String {
        switch self {
        case .clickCountry: return "Find every country on the globe"
        case .nameCapital: return "Type the capital of every country"
        }
    }

    var icon: String {
        switch self {
        case .clickCountry: return "scope"
        case .nameCapital: return "building.columns"
        }
    }
}

/// Region scopes for challenge games: the whole world or a single continent.
enum ChallengeRegion: String, CaseIterable, Identifiable {
    case world
    case africa, asia, europe, northAmerica, southAmerica, oceania

    var id: String { rawValue }

    /// The continent backing this region (nil for world).
    var continent: Continent? {
        switch self {
        case .world: return nil
        case .africa: return .africa
        case .asia: return .asia
        case .europe: return .europe
        case .northAmerica: return .northAmerica
        case .southAmerica: return .southAmerica
        case .oceania: return .oceania
        }
    }

    var displayName: String {
        continent?.rawValue ?? "World"
    }

    var emoji: String {
        continent?.medal ?? "🌍"
    }

    /// UN countries in this region, matching how the app counts progress
    /// (non-UN territories and Antarctica are excluded).
    var countries: [String] {
        let pool: Set<String>
        if let continent = continent {
            pool = continent.countries
        } else {
            pool = Continent.allCases
                .filter { $0 != .antarctica }
                .reduce(into: Set<String>()) { $0.formUnion($1.countries) }
        }
        return pool.subtracting(GlobeState.nonUNTerritories).sorted()
    }

    /// Trophy tier a flawless sweep of this region awards.
    var trophy: ChallengeTrophy {
        switch self {
        case .world: return .gold
        case .africa, .asia, .europe: return .silver
        case .northAmerica, .southAmerica, .oceania: return .bronze
        }
    }

    /// Where the camera flies when a game in this region starts.
    var cameraTarget: GlobeState.CameraTarget {
        switch self {
        case .world: return .init(lat: 25, lon: 10, distance: 4.0)
        case .africa: return .init(lat: 2, lon: 20, distance: 3.2)
        case .asia: return .init(lat: 35, lon: 90, distance: 3.4)
        case .europe: return .init(lat: 54, lon: 15, distance: 2.4)
        case .northAmerica: return .init(lat: 40, lon: -95, distance: 3.4)
        case .southAmerica: return .init(lat: -18, lon: -60, distance: 3.2)
        case .oceania: return .init(lat: -22, lon: 145, distance: 3.2)
        }
    }
}

/// Formats a game duration as m:ss (or h:mm:ss for long world sweeps).
func formatGameTime(_ time: TimeInterval) -> String {
    let totalSeconds = Int(time)
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60
    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }
    return String(format: "%d:%02d", minutes, seconds)
}
