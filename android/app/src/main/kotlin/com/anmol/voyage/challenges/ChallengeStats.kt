package com.anmol.voyage.challenges

import kotlinx.serialization.Serializable

/**
 * Lifetime statistics for one game mode in one region — iOS `ChallengeGameStats`.
 *
 * Saved in the app's one document (`PersistedState.challengeStats`), which is
 * why it is serializable; the field names are iOS's, with the time spelled out in
 * seconds.
 */
@Serializable
data class ChallengeGameStats(
    /**
     * Completed games: sweeps that went through every country in the region and
     * reached the result screen. Best result and time only ever come from these.
     */
    val gamesPlayed: Int = 0,
    /** Most countries answered correctly in one completed sweep. */
    val bestCorrect: Int? = null,
    /**
     * Countries in the sweep the best was set on. The country set can change
     * between releases, so best results compare as fractions.
     */
    val bestTotal: Int? = null,
    val bestTimeSeconds: Double? = null,
) {

    val bestFraction: Double?
        get() {
            val correct = bestCorrect ?: return null
            val total = bestTotal?.takeIf { it > 0 } ?: return null
            return correct.toDouble() / total
        }

    val bestPercentage: Int? get() = bestFraction?.let { Math.round(it * 100).toInt() }

    /** A flawless sweep: every country correct. It earns the region's trophy. */
    val isPerfect: Boolean get() = bestFraction == 1.0
}

/**
 * Every region's statistics for every mode, keyed `mode|region` exactly as iOS
 * `ChallengeStatsStore` keys them. Immutable: [recording] returns the updated
 * book, which the app state saves.
 */
data class ChallengeStatsBook(val byKey: Map<String, ChallengeGameStats> = emptyMap()) {

    fun stats(mode: ChallengeGameMode, region: ChallengeRegion): ChallengeGameStats =
        byKey[key(mode, region)] ?: ChallengeGameStats()

    fun totalGamesPlayed(mode: ChallengeGameMode): Int =
        ChallengeRegion.entries.sumOf { stats(mode, it).gamesPlayed }

    /** Trophies of [trophy]'s tier across every mode: one per flawless region per mode. */
    fun trophyCount(trophy: ChallengeTrophy): Int = ChallengeGameMode.entries.sumOf { mode ->
        ChallengeRegion.entries.count { it.trophy == trophy && stats(mode, it).isPerfect }
    }

    /**
     * This book with one more completed game, and whether it set a new best:
     * more of the region correct, or the same share in less time.
     */
    fun recording(
        mode: ChallengeGameMode,
        region: ChallengeRegion,
        correct: Int,
        total: Int,
        timeSeconds: Double,
    ): Recorded {
        if (total <= 0) return Recorded(this, isNewBest = false)
        val previous = stats(mode, region)
        val fraction = correct.toDouble() / total
        val bestFraction = previous.bestFraction
        val bestTime = previous.bestTimeSeconds
        val isNewBest = bestFraction == null || bestTime == null ||
            fraction > bestFraction || (fraction == bestFraction && timeSeconds < bestTime)

        val updated = if (isNewBest) {
            previous.copy(
                gamesPlayed = previous.gamesPlayed + 1,
                bestCorrect = correct,
                bestTotal = total,
                bestTimeSeconds = timeSeconds,
            )
        } else {
            previous.copy(gamesPlayed = previous.gamesPlayed + 1)
        }
        return Recorded(ChallengeStatsBook(byKey + (key(mode, region) to updated)), isNewBest)
    }

    class Recorded(val book: ChallengeStatsBook, val isNewBest: Boolean)

    private fun key(mode: ChallengeGameMode, region: ChallengeRegion) = "${mode.rawValue}|${region.rawValue}"
}

/**
 * Where a finished sweep is recorded. `VoyageState` is the one the app uses, so
 * results are saved with everything else the user has done.
 */
interface ChallengeStatsRecorder {
    val challengeStats: ChallengeStatsBook

    /** Records a completed game; true if it set a new best. */
    fun recordChallengeGame(
        mode: ChallengeGameMode,
        region: ChallengeRegion,
        correct: Int,
        total: Int,
        timeSeconds: Double,
    ): Boolean
}
