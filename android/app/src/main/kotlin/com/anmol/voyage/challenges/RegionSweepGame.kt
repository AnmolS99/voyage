package com.anmol.voyage.challenges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anmol.voyage.data.CountryAlias
import com.anmol.voyage.data.CountrySearchIndex

/** What one player input did in a region sweep. */
enum class SweepGuessOutcome {
    /** Click the Country: the first tap marked a country, awaiting confirmation. */
    Marked,
    Correct,

    /** A wrong guess: the answer is shown before the sweep moves on. */
    Reveal,

    /** Nothing happened: already answered, empty, or not playing. */
    Ignored,
}

/**
 * The shared engine of every challenge game — a port of iOS
 * `RegionSweepGameViewModel`. Every country in the region comes up once, in
 * shuffled order, with a single guess each; a wrong guess reveals the answer and
 * moves on, so a sweep's result is simply how many countries were answered
 * correctly. The sweep is timed until the queue runs out, and only then is it
 * recorded — a restarted or abandoned sweep leaves the statistics untouched.
 *
 * Subclasses turn their input (a tap on the globe, a typed capital) into a
 * right/wrong judgement and hand it to [resolveGuess]; typed-answer games can
 * use [submitGuess] directly by overriding [currentAnswers].
 *
 * Observable fields are Compose state, like `VoyageState`: every reader is a
 * composable. The clock is not — [elapsedSeconds] is read on demand, so the
 * screen decides how often a running timer repaints.
 *
 * @param pool the countries of one sweep, already in play order. Called again
 *   for each restart, so it should shuffle.
 * @param nanoTime a monotonic clock, replaceable in tests.
 */
open class RegionSweepGame(
    val mode: ChallengeGameMode,
    val region: ChallengeRegion,
    private val recorder: ChallengeStatsRecorder,
    private val pool: () -> List<String>,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    enum class Phase { Playing, Revealing, Finished }

    private var queue: List<String> = pool()

    var totalCountries: Int by mutableIntStateOf(queue.size)
        private set

    var currentTarget: String? by mutableStateOf(queue.firstOrNull())
        private set

    /** Countries answered correctly — the score of the sweep. */
    var correctCount: Int by mutableIntStateOf(0)
        private set

    /** Countries whose answer was revealed after a wrong guess, in sweep order. */
    var missedCountries: List<String> by mutableStateOf(emptyList())
        private set

    var phase: Phase by mutableStateOf(if (queue.isEmpty()) Phase.Finished else Phase.Playing)
        private set

    var isNewBest: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether this sweep earned the region's trophy for the first time — a
     * repeat flawless run does not earn it again.
     */
    var didEarnTrophy: Boolean by mutableStateOf(false)
        private set

    /** How far through the queue the sweep is. */
    val answeredCount: Int get() = correctCount + missedCountries.size

    private var answered: Set<String> = emptySet()
    private var accumulatedNanos = 0L

    /** When the running stretch of the clock began; null while paused or finished. */
    private var segmentStart: Long? = if (phase == Phase.Playing) nanoTime() else null

    private var finalSeconds = 0.0

    /** The time on the clock, excluding stretches spent paused. */
    fun elapsedSeconds(): Double {
        if (phase == Phase.Finished) return finalSeconds
        val running = segmentStart?.let { nanoTime() - it } ?: 0L
        return (accumulatedNanos + running) / NANOS_PER_SECOND
    }

    // ---- Subclass interface ----

    /**
     * The answers accepted for the current target, the main one first: the
     * country's own name (Name the Flag) unless a subclass says otherwise —
     * Name the Capital takes every capital the country has (La Paz and Sucre).
     */
    open val currentAnswers: List<String> get() = listOfNotNull(currentTarget)

    /**
     * The accepted answer [guess] names, ignoring case and surrounding
     * whitespace, or null when it names none of them.
     */
    open fun acceptedAnswer(guess: String): String? {
        val trimmed = guess.trim()
        return currentAnswers.firstOrNull { it.equals(trimmed, ignoreCase = true) }
    }

    /** Scores a typed guess against [currentAnswers]. */
    fun submitGuess(guess: String): SweepGuessOutcome {
        if (phase != Phase.Playing || currentAnswers.isEmpty()) return SweepGuessOutcome.Ignored
        if (guess.isBlank()) return SweepGuessOutcome.Ignored
        return resolveGuess(correct = acceptedAnswer(guess) != null)
    }

    /** Scores one submitted guess against the current target. */
    fun resolveGuess(correct: Boolean): SweepGuessOutcome {
        if (phase != Phase.Playing) return SweepGuessOutcome.Ignored
        val target = currentTarget ?: return SweepGuessOutcome.Ignored

        if (!correct) {
            missedCountries = missedCountries + target
            phase = Phase.Revealing
            return SweepGuessOutcome.Reveal
        }

        correctCount += 1
        advance(past = target)
        return SweepGuessOutcome.Correct
    }

    fun isAnswered(country: String): Boolean = country in answered

    /** Called whenever the sweep moves to a new target; clears per-target input. */
    protected open fun targetDidChange() {}

    // ---- Lifecycle ----

    /** Called once the answer to a wrong guess has been shown. */
    fun finishReveal() {
        if (phase != Phase.Revealing) return
        val target = currentTarget ?: return
        phase = Phase.Playing
        advance(past = target)
    }

    /** Starts the sweep over, with a fresh shuffle. */
    fun restart() {
        queue = pool()
        answered = emptySet()
        missedCountries = emptyList()
        correctCount = 0
        totalCountries = queue.size
        currentTarget = queue.firstOrNull()
        accumulatedNanos = 0L
        finalSeconds = 0.0
        isNewBest = false
        didEarnTrophy = false
        phase = if (queue.isEmpty()) Phase.Finished else Phase.Playing
        segmentStart = if (phase == Phase.Playing) nanoTime() else null
        targetDidChange()
    }

    /** Stops the clock — the app went to the background. */
    fun pause() {
        val start = segmentStart ?: return
        accumulatedNanos += nanoTime() - start
        segmentStart = null
    }

    fun resume() {
        if (phase == Phase.Finished || segmentStart != null) return
        segmentStart = nanoTime()
    }

    private fun advance(past: String) {
        answered = answered + past
        queue = queue.drop(1)
        currentTarget = queue.firstOrNull()
        targetDidChange()
        if (currentTarget == null) finish()
    }

    private fun finish() {
        finalSeconds = elapsedSeconds()
        segmentStart = null
        phase = Phase.Finished
        val hadTrophy = recorder.challengeStats.stats(mode, region).isPerfect
        isNewBest = recorder.recordChallengeGame(mode, region, correctCount, totalCountries, finalSeconds)
        didEarnTrophy = !hadTrophy && correctCount == totalCountries
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/**
 * Click the Country — iOS `ClickCountryGameViewModel`. A guess takes two taps:
 * the first marks a country, a second on the same one submits it, and a tap on a
 * different country moves the mark instead. A mis-tap never costs the country's
 * only guess.
 */
class ClickCountryGame(
    region: ChallengeRegion,
    recorder: ChallengeStatsRecorder,
    pool: () -> List<String>,
    nanoTime: () -> Long = System::nanoTime,
) : RegionSweepGame(ChallengeGameMode.ClickCountry, region, recorder, pool, nanoTime) {

    /** The country marked by a first tap, awaiting a confirming second one. */
    var pendingGuess: String? by mutableStateOf(null)
        private set

    fun handleTap(country: String): SweepGuessOutcome {
        if (phase != Phase.Playing) return SweepGuessOutcome.Ignored
        val target = currentTarget ?: return SweepGuessOutcome.Ignored
        if (isAnswered(country)) return SweepGuessOutcome.Ignored

        if (country != pendingGuess) {
            pendingGuess = country
            return SweepGuessOutcome.Marked
        }
        pendingGuess = null
        return resolveGuess(correct = country == target)
    }

    override fun targetDidChange() {
        pendingGuess = null
    }
}

/**
 * Name the Capital — iOS `NameCapitalGameViewModel`: each country is shown and
 * the player types its capital. A country with several capitals (Bolivia: La
 * Paz and Sucre) takes any of them. A country without capital data could never
 * be answered, so it is dropped from the pool.
 *
 * @param capitalsOf every capital of a country, the main one first — empty when
 *   it has none.
 */
class NameCapitalGame(
    region: ChallengeRegion,
    recorder: ChallengeStatsRecorder,
    pool: () -> List<String>,
    private val capitalsOf: (String) -> List<String>,
    nanoTime: () -> Long = System::nanoTime,
) : RegionSweepGame(
    ChallengeGameMode.NameCapital,
    region,
    recorder,
    { pool().filter { capitalsOf(it).isNotEmpty() } },
    nanoTime,
) {

    override val currentAnswers: List<String> get() = currentTarget?.let(capitalsOf).orEmpty()

    fun capitals(of: String): List<String> = capitalsOf(of)
}

/**
 * Name the Flag — iOS `NameFlagGameViewModel`: each country's flag is shown and
 * the player names the country. A typed guess counts when it is the country's
 * name or another name for it — "UK", "Great Britain", "Burma" — compared the
 * way search compares them, so "turkiye" names Türkiye. A part of a country
 * ("England", "Hawaii") or its ISO code is not a name for it and does not count.
 *
 * Only exact names count, never the top suggestion: search ranks prefixes, and
 * "Ni" must not score Niger.
 *
 * @param aliasesOf every alias of a country, as `CountryDataCache.aliases` gives them.
 */
class NameFlagGame(
    region: ChallengeRegion,
    recorder: ChallengeStatsRecorder,
    pool: () -> List<String>,
    private val aliasesOf: (String) -> List<CountryAlias>,
    nanoTime: () -> Long = System::nanoTime,
) : RegionSweepGame(ChallengeGameMode.NameFlag, region, recorder, pool, nanoTime) {

    /**
     * The country's name when [guess] is its name or one of its other names,
     * else null — the banner names the country, not the alias typed.
     */
    override fun acceptedAnswer(guess: String): String? {
        val target = currentTarget ?: return null
        val typed = CountrySearchIndex.normalize(guess.trim())
        if (typed.isEmpty()) return null
        val names = listOf(target) + aliasesOf(target).filter { it.kind in NAME_KINDS }.map { it.text }
        return target.takeIf { names.any { CountrySearchIndex.normalize(it) == typed } }
    }

    private companion object {
        val NAME_KINDS = setOf(CountryAlias.Kind.NAME, CountryAlias.Kind.FORMER_NAME)
    }
}
