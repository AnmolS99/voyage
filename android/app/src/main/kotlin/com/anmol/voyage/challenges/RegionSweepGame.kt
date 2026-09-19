package com.anmol.voyage.challenges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
 * use [submitGuess] directly by overriding [currentAnswer].
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
     * The answer expected for the current target: the country's own name (Name
     * the Flag) unless a subclass says otherwise.
     */
    open val currentAnswer: String? get() = currentTarget

    /** Scores a typed guess against [currentAnswer], ignoring case and surrounding whitespace. */
    fun submitGuess(guess: String): SweepGuessOutcome {
        if (phase != Phase.Playing) return SweepGuessOutcome.Ignored
        val answer = currentAnswer ?: return SweepGuessOutcome.Ignored
        val trimmed = guess.trim()
        if (trimmed.isEmpty()) return SweepGuessOutcome.Ignored
        return resolveGuess(correct = answer.equals(trimmed, ignoreCase = true))
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
 * the player types its capital. A country without capital data could never be
 * answered, so it is dropped from the pool.
 */
class NameCapitalGame(
    region: ChallengeRegion,
    recorder: ChallengeStatsRecorder,
    pool: () -> List<String>,
    private val capitalOf: (String) -> String?,
    nanoTime: () -> Long = System::nanoTime,
) : RegionSweepGame(
    ChallengeGameMode.NameCapital,
    region,
    recorder,
    { pool().filter { capitalOf(it) != null } },
    nanoTime,
) {

    override val currentAnswer: String? get() = currentTarget?.let(capitalOf)

    fun capital(of: String): String? = capitalOf(of)
}
