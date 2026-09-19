package com.anmol.voyage.challenges

import com.anmol.voyage.data.Continent
import com.anmol.voyage.data.ContinentIndex
import com.anmol.voyage.data.LatLon
import com.anmol.voyage.data.UnMembership
import java.util.Locale

/**
 * Trophy tiers awarded for flawless (100%) region sweeps in any game mode:
 * bronze for the small continents, silver for the large ones, gold for the whole
 * world. A port of iOS `ChallengeTrophy`; the colors live in `VoyagePalette`.
 */
enum class ChallengeTrophy { Bronze, Silver, Gold }

/**
 * Game modes playable from the Challenges tab, in iOS `ChallengeGameMode`'s
 * order. [rawValue] is iOS's, and is what statistics are keyed by — so a synced
 * document would mean the same thing on both platforms.
 */
enum class ChallengeGameMode(val rawValue: String) {
    ClickCountry("clickCountry"),
    NameCapital("nameCapital"),
    NameFlag("nameFlag"),
    ;

    companion object {
        fun fromRawValue(raw: String): ChallengeGameMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * Where a challenge is played: the whole world or a single continent. A port of
 * iOS `ChallengeRegion`, in the same order, with the same trophy tiers and the
 * same camera targets.
 */
enum class ChallengeRegion(
    val rawValue: String,
    /** The continent backing this region; null for the world. */
    val continent: Continent?,
    val trophy: ChallengeTrophy,
    /** Where the globe flies when a game in this region starts. */
    val cameraTarget: LatLon,
    val cameraDistance: Float,
) {
    World("world", null, ChallengeTrophy.Gold, LatLon(lat = 25.0, lon = 10.0), 4.0f),
    Africa("africa", Continent.AFRICA, ChallengeTrophy.Silver, LatLon(lat = 2.0, lon = 20.0), 3.2f),
    Asia("asia", Continent.ASIA, ChallengeTrophy.Silver, LatLon(lat = 35.0, lon = 90.0), 3.4f),
    Europe("europe", Continent.EUROPE, ChallengeTrophy.Silver, LatLon(lat = 54.0, lon = 15.0), 2.4f),
    NorthAmerica(
        "northAmerica",
        Continent.NORTH_AMERICA,
        ChallengeTrophy.Bronze,
        LatLon(lat = 40.0, lon = -95.0),
        3.4f,
    ),
    SouthAmerica(
        "southAmerica",
        Continent.SOUTH_AMERICA,
        ChallengeTrophy.Bronze,
        LatLon(lat = -18.0, lon = -60.0),
        3.2f,
    ),
    Oceania("oceania", Continent.OCEANIA, ChallengeTrophy.Bronze, LatLon(lat = -22.0, lon = 145.0), 3.2f),
    ;

    /** The region's emoji: its continent's medal, or a globe for the world. */
    val emoji: String get() = continent?.medal ?: "🌍"

    /**
     * UN states in this region, alphabetical — counted the way the app counts
     * progress, so territories and Antarctica are never targets.
     */
    fun countries(continents: ContinentIndex): List<String> {
        val pool = continent?.let { continents.countries(of = it) }
            ?: Continent.entries
                .filter { it != Continent.ANTARCTICA }
                .flatMapTo(HashSet()) { continents.countries(of = it) }
        return UnMembership.membersOf(pool).sorted()
    }

    companion object {
        fun fromRawValue(raw: String): ChallengeRegion? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** A game duration as m:ss, or h:mm:ss for a long world sweep — iOS `formatGameTime`. */
fun formatGameTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
