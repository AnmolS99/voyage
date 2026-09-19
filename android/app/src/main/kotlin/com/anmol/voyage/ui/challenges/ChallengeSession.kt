package com.anmol.voyage.ui.challenges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ChallengeRegion
import com.anmol.voyage.challenges.ChallengeStatsRecorder
import com.anmol.voyage.challenges.ClickCountryGame
import com.anmol.voyage.challenges.NameCapitalGame
import com.anmol.voyage.challenges.RegionSweepGame
import com.anmol.voyage.challenges.SweepGuessOutcome
import com.anmol.voyage.data.CountryDataCache
import com.anmol.voyage.data.CountrySearchIndex
import com.anmol.voyage.data.FlagEmoji
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The banner shown after a guess. */
sealed interface SweepFeedback {
    /** Right: "Correct — [answer]", the country or the capital. */
    data class Correct(val answer: String) : SweepFeedback

    /** Click the Country, wrong: "[country] [flag] is here". */
    data class CountryIsHere(val country: String, val flag: String) : SweepFeedback

    /** Name the Capital, wrong: "The capital of [country] is [capital]". */
    data class CapitalOf(val country: String, val capital: String) : SweepFeedback

    /** Name the Flag, wrong: "This flag is [country] [flag]". */
    data class FlagOf(val country: String, val flag: String) : SweepFeedback
}

/**
 * One challenge being played: the sweep, the world it is played on, and the
 * choreography between them — what the globe does after each guess, how long a
 * revealed answer stays up. The three iOS game views (`ClickCountryGameView`,
 * `NameCapitalGameView`, `NameFlagGameView`) each carry this part themselves;
 * here it is one class with the differences spelled out per mode.
 *
 * A `ViewModel` scoped to the game's navigation entry, so a rotation keeps the
 * sweep, its clock and the colors already on the globe.
 */
class ChallengeSession(
    val mode: ChallengeGameMode,
    val region: ChallengeRegion,
    recorder: ChallengeStatsRecorder,
    private val cache: CountryDataCache,
) : ViewModel() {

    val scene = ChallengeWorldScene(onTap = ::handleTap)

    private val pool = { region.countries(cache.continents).shuffled() }

    val game: RegionSweepGame = when (mode) {
        ChallengeGameMode.ClickCountry -> ClickCountryGame(region, recorder, pool)
        ChallengeGameMode.NameCapital -> NameCapitalGame(region, recorder, pool, ::capitalOf)
        ChallengeGameMode.NameFlag -> RegionSweepGame(mode, region, recorder, pool)
    }

    /**
     * What a typed guess is matched against: every capital, or every country
     * name, in the dataset — never just the region's, so the answer does not
     * stand out as the only suggestion nearby. Null for Click the Country.
     */
    val suggestions: CountrySearchIndex<String>? = when (mode) {
        ChallengeGameMode.ClickCountry -> null
        ChallengeGameMode.NameCapital ->
            CountrySearchIndex(cache.countries.mapNotNull { it.capital?.name }.distinct()) { it }
        ChallengeGameMode.NameFlag -> CountrySearchIndex(cache.countries.map { it.name }) { it }
    }

    var feedback: SweepFeedback? by mutableStateOf(null)
        private set

    /** Outcomes the screen answers with a haptic. */
    val outcomes: Flow<SweepGuessOutcome> get() = outcomeChannel.receiveAsFlow()
    private val outcomeChannel = Channel<SweepGuessOutcome>(Channel.BUFFERED)

    private var feedbackJob: Job? = null
    private var revealJob: Job? = null

    init {
        frameRegion()
    }

    fun flagOf(country: String): String = FlagEmoji.of(cache.countryNamed(country)?.isoCode)

    fun capitalOf(country: String): String? = cache.countryNamed(country)?.capital?.name

    // ---- Input ----

    /** A tap on the world. Only Click the Country plays by tapping. */
    private fun handleTap(country: String) {
        val clickGame = game as? ClickCountryGame ?: return
        val target = clickGame.currentTarget
        val outcome = clickGame.handleTap(country)
        when (outcome) {
            // Only outline: flying to the tap would move the country out from
            // under the finger before the confirming tap. And the name is not
            // shown, or marking would give the answer away.
            SweepGuessOutcome.Marked -> scene.select(country)
            SweepGuessOutcome.Correct -> {
                scene.deselect()
                scene.highlight(country, VoyagePalette.challengeCorrect)
                showFeedback(SweepFeedback.Correct(country))
            }
            SweepGuessOutcome.Reveal -> if (target != null) {
                showFeedback(SweepFeedback.CountryIsHere(target, flagOf(target)), CLICK_REVEAL_MILLIS)
                scene.highlight(target, VoyagePalette.challengeWrong)
                scene.select(target, cache.hitTester.center(target))
                afterReveal(CLICK_REVEAL_MILLIS) { scene.deselect() }
            }
            SweepGuessOutcome.Ignored -> Unit
        }
        report(outcome)
    }

    /** A typed or picked guess, in the two naming modes. */
    fun submit(guess: String) {
        // Captured first: a right answer moves the sweep on.
        val target = game.currentTarget ?: return
        val answer = game.currentAnswer ?: return
        val outcome = game.submitGuess(guess)
        when (outcome) {
            SweepGuessOutcome.Correct -> {
                scene.deselect()
                scene.highlight(target, VoyagePalette.challengeCorrect)
                showFeedback(SweepFeedback.Correct(answer))
                if (mode == ChallengeGameMode.NameCapital) focusOnCurrentTarget(distance = null)
            }
            SweepGuessOutcome.Reveal -> {
                scene.highlight(target, VoyagePalette.challengeWrong)
                if (mode == ChallengeGameMode.NameCapital) {
                    showFeedback(SweepFeedback.CapitalOf(target, answer), NAME_REVEAL_MILLIS)
                    afterReveal(NAME_REVEAL_MILLIS) {
                        scene.deselect()
                        focusOnCurrentTarget(distance = null)
                    }
                } else {
                    // The flag's country was never outlined — that would be the
                    // answer — so show where it is now, then pull back out.
                    showFeedback(SweepFeedback.FlagOf(target, flagOf(target)), NAME_REVEAL_MILLIS)
                    scene.select(target, cache.hitTester.center(target))
                    afterReveal(NAME_REVEAL_MILLIS) {
                        scene.deselect()
                        scene.flyTo(region.cameraTarget, region.cameraDistance)
                    }
                }
            }
            SweepGuessOutcome.Marked, SweepGuessOutcome.Ignored -> Unit
        }
        report(outcome)
    }

    /** Starts the sweep over, with a fresh shuffle and a clean globe. */
    fun playAgain() {
        revealJob?.cancel()
        feedbackJob?.cancel()
        feedback = null
        scene.reset()
        game.restart()
        frameRegion()
    }

    fun pause() = game.pause()

    fun resume() = game.resume()

    // ---- Choreography ----

    /**
     * Where the camera goes as a sweep starts. Name the Capital shows the country
     * being asked about; the other two frame the whole region, since outlining
     * the target is the answer.
     */
    private fun frameRegion() {
        if (mode == ChallengeGameMode.NameCapital) {
            focusOnCurrentTarget(distance = region.cameraDistance)
        } else {
            scene.flyTo(region.cameraTarget, region.cameraDistance)
        }
    }

    /** Name the Capital: outline the current country and fly to it. */
    private fun focusOnCurrentTarget(distance: Float?) {
        val target = game.currentTarget ?: return
        scene.select(target)
        cache.hitTester.center(target)?.let { scene.flyTo(it, distance) }
    }

    /** Leaves a revealed answer up for [millis], then moves the sweep on. */
    private fun afterReveal(millis: Long, then: () -> Unit) {
        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            delay(millis)
            game.finishReveal()
            then()
        }
    }

    private fun showFeedback(newFeedback: SweepFeedback, millis: Long = FEEDBACK_MILLIS) {
        feedbackJob?.cancel()
        feedback = newFeedback
        feedbackJob = viewModelScope.launch {
            delay(millis)
            feedback = null
        }
    }

    private fun report(outcome: SweepGuessOutcome) {
        if (outcome != SweepGuessOutcome.Ignored) outcomeChannel.trySend(outcome)
    }

    companion object {
        /** iOS `showFeedback`'s default duration. */
        private const val FEEDBACK_MILLIS = 1_600L

        /** iOS `ClickCountryGameView.revealDuration`. */
        private const val CLICK_REVEAL_MILLIS = 2_200L

        /** iOS `NameCapitalGameView.revealDuration`, and Name the Flag's. */
        private const val NAME_REVEAL_MILLIS = 2_600L

        fun factory(
            mode: ChallengeGameMode,
            region: ChallengeRegion,
            recorder: ChallengeStatsRecorder,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChallengeSession(mode, region, recorder, CountryDataCache.shared) }
        }
    }
}
