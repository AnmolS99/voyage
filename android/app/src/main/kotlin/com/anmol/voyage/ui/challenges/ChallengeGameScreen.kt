package com.anmol.voyage.ui.challenges

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anmol.voyage.R
import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ClickCountryGame
import com.anmol.voyage.challenges.NameCapitalGame
import com.anmol.voyage.challenges.RegionSweepGame
import com.anmol.voyage.challenges.SweepGuessOutcome
import com.anmol.voyage.challenges.formatGameTime
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.voyageCardColors
import kotlinx.coroutines.delay

/**
 * A challenge being played: the heads-up display over the world.
 *
 * The world itself is not drawn here. It is Home's globe (or map), painting
 * [ChallengeSession.scene] while this screen is up — `VoyageApp` hands the scene
 * over — so this composable is transparent everywhere except its controls, and
 * a tap anywhere else lands on the globe beneath it. That is iOS's full-screen
 * game view, minus the `GlobeView` it embeds.
 */
@Composable
fun ChallengeGameScreen(session: ChallengeSession, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val game = session.game
    val finished = game.phase == RegionSweepGame.Phase.Finished
    var confirmingQuit by rememberSaveable { mutableStateOf(false) }
    var confirmingRestart by rememberSaveable { mutableStateOf(false) }

    // The clock only runs while the game is on screen, as iOS pauses it when
    // the scene leaves the foreground.
    LifecycleResumeEffect(session) {
        session.resume()
        onPauseOrDispose { session.pause() }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(session) {
        session.outcomes.collect { outcome ->
            val type = when (outcome) {
                SweepGuessOutcome.Marked -> HapticFeedbackType.SegmentTick
                SweepGuessOutcome.Correct -> HapticFeedbackType.Confirm
                SweepGuessOutcome.Reveal -> HapticFeedbackType.Reject
                SweepGuessOutcome.Ignored -> return@collect
            }
            haptics.performHapticFeedback(type)
        }
    }

    BackHandler {
        if (finished) onExit() else confirmingQuit = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        // One column, so the bottom controls only ever get the space the prompt
        // leaves them: with the keyboard up, the suggestions shrink and scroll
        // rather than covering the flag or country being asked about.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SweepTopBar(game = game, onQuit = { confirmingQuit = true })
            SweepPromptCard(session = session)

            // Feedback, and the controls the thumb reaches for, at the bottom —
            // over the keyboard when it is up.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SweepBanner(session = session)
                    if (!finished) {
                        if (session.mode == ChallengeGameMode.ClickCountry) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                RestartButton(onClick = { confirmingRestart = true })
                            }
                        } else {
                            GuessEntry(
                                session = session,
                                onRestart = { confirmingRestart = true },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
            }
        }

        if (finished) {
            SweepResultOverlay(session = session, onPlayAgain = session::playAgain, onDone = onExit)
        }
    }

    if (confirmingQuit) {
        ConfirmDialog(
            title = R.string.challenge_quit_title,
            message = R.string.challenge_quit_message,
            confirm = R.string.challenge_quit_confirm,
            onConfirm = {
                confirmingQuit = false
                onExit()
            },
            onDismiss = { confirmingQuit = false },
        )
    }
    if (confirmingRestart) {
        ConfirmDialog(
            title = R.string.challenge_restart_title,
            message = R.string.challenge_restart_message,
            confirm = R.string.challenge_restart,
            onConfirm = {
                confirmingRestart = false
                session.playAgain()
            },
            onDismiss = { confirmingRestart = false },
        )
    }
}

/** Quit on the leading side; the clock and the score on the trailing one. */
@Composable
private fun SweepTopBar(game: RegionSweepGame, onQuit: () -> Unit) {
    val finished = game.phase == RegionSweepGame.Phase.Finished
    // Repainted twice a second, as iOS's timer does, and not at all once the
    // sweep is over.
    val seconds by produceState(game.elapsedSeconds(), game, finished) {
        while (true) {
            value = game.elapsedSeconds()
            if (finished) break
            delay(CLOCK_TICK_MILLIS)
        }
    }
    val time = formatGameTime(seconds)
    val description = stringResource(R.string.challenge_elapsed, time) + ", " +
        stringResource(R.string.challenge_correct_count, game.correctCount)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onQuit) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.challenge_quit))
        }
        Spacer(modifier = Modifier.weight(1f))
        HudPill(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Timer, null, tint = VoyagePalette.buttonColor, modifier = Modifier.size(16.dp))
                Text(time, style = MaterialTheme.typography.labelLarge)
                Text("·", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.CheckCircle, null, tint = VoyagePalette.buttonColor, modifier = Modifier.size(16.dp))
                Text(game.correctCount.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * How far the sweep is and what is being asked: the country's flag and name —
 * or, in Name the Flag, where the name is the answer, a large flag alone.
 */
@Composable
private fun SweepPromptCard(session: ChallengeSession) {
    val game = session.game
    val target = game.currentTarget ?: return
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = voyageCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.challenge_progress, game.answeredCount + 1, game.totalCountries),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.mode == ChallengeGameMode.NameFlag) {
                Text(session.flagOf(target), fontSize = 72.sp)
                Text(
                    text = stringResource(R.string.challenge_which_country),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(session.flagOf(target), fontSize = 26.sp)
                    Text(target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The feedback after a guess or, while a country is marked, how to confirm it. */
@Composable
private fun SweepBanner(session: ChallengeSession) {
    val feedback = session.feedback
    val marked = (session.game as? ClickCountryGame)?.pendingGuess != null
    val shown: Any? = feedback ?: if (marked) ConfirmHint else null

    AnimatedContent(
        targetState = shown,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
        },
        label = "sweepBanner",
    ) { banner ->
        when (banner) {
            is SweepFeedback -> FeedbackBanner(banner)
            // Neutral on purpose: naming the marked country would give the answer away.
            ConfirmHint -> HudPill {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = VoyagePalette.buttonColor, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.challenge_tap_to_confirm), style = MaterialTheme.typography.labelLarge)
                }
            }
            else -> Unit
        }
    }
}

private data object ConfirmHint

@Composable
private fun FeedbackBanner(feedback: SweepFeedback) {
    val text = when (feedback) {
        is SweepFeedback.Correct -> stringResource(R.string.challenge_feedback_correct, feedback.answer)
        is SweepFeedback.CountryIsHere ->
            stringResource(R.string.challenge_feedback_country_here, feedback.country, feedback.flag)
        is SweepFeedback.CapitalOf ->
            stringResource(R.string.challenge_feedback_capital, feedback.country, feedback.capital)
        is SweepFeedback.FlagOf -> stringResource(R.string.challenge_feedback_flag, feedback.country, feedback.flag)
    }
    val color = if (feedback is SweepFeedback.Correct) VoyagePalette.challengeCorrect else VoyagePalette.challengeWrong
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(color.copy(alpha = 0.95f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * The guess field, with its suggestions opening upward above it and the
 * restart button at its side. It takes the keyboard as soon as a sweep starts,
 * and gives it back when the sweep ends so the result is not hidden behind it.
 */
@Composable
private fun GuessEntry(session: ChallengeSession, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    val index = session.suggestions ?: return
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit(guess: String) {
        session.submit(guess)
        query = ""
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val finished = session.game.phase == RegionSweepGame.Phase.Finished
    LaunchedEffect(finished) {
        if (finished) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val matches = remember(query, index) {
        if (query.isBlank()) emptyList() else index.search(query).take(MAX_SUGGESTIONS)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (matches.isNotEmpty()) {
            // Weighted, so it is measured after the field and gets only the room
            // left over — capped at iOS's 200 points when there is more.
            Card(
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(20.dp),
                colors = voyageCardColors(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = SUGGESTIONS_MAX_HEIGHT)) {
                    itemsIndexed(matches, key = { _, item -> item }) { position, suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { submit(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                        if (position < matches.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.challenge_guess_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.challenge_clear_guess))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                // The keyboard's own button submits whatever was typed, as
                // iOS's search field does, and keeps the keyboard up for the
                // next country.
                keyboardActions = KeyboardActions(onDone = { submit(query) }),
            )
            RestartButton(onClick = onRestart)
        }
    }
}

@Composable
private fun RestartButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.challenge_restart))
    }
}

/** A capsule of the card color, iOS's `GlassPill` without the glass. */
@Composable
private fun HudPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
        content = content,
    )
}

/**
 * The end of a sweep: trophy and new-best badges, the score, what was missed,
 * and the way out. Its scrim swallows taps, so the globe beneath stays put.
 */
@Composable
private fun SweepResultOverlay(session: ChallengeSession, onPlayAgain: () -> Unit, onDone: () -> Unit) {
    val game = session.game
    val percentage = if (game.totalCountries > 0) {
        Math.round(game.correctCount * 100.0 / game.totalCountries).toInt()
    } else {
        0
    }
    val missed = game.missedCountries
    val missedWithCapital = stringResource(R.string.challenge_missed_with_capital)
    val missedSummary = missed.takeIf { it.isNotEmpty() }?.joinToString(", ") { country ->
        val capital = (game as? NameCapitalGame)?.capital(of = country)
        if (capital != null) missedWithCapital.format(country, capital) else country
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = voyageCardColors(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(session.region.emoji, fontSize = 44.sp)
                Text(
                    text = stringResource(R.string.challenge_sweep_complete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (game.didEarnTrophy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrophyIcon(trophy = session.region.trophy, earned = true, modifier = Modifier.size(44.dp))
                        Text(
                            text = stringResource(R.string.challenge_trophy_earned, session.region.trophy.displayName()),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (game.isNewBest) {
                    Text(
                        text = stringResource(R.string.challenge_new_best),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .background(VoyagePalette.buttonVisited, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.challenge_score, game.correctCount, game.totalCountries),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagePalette.buttonColor,
                    )
                    Text(
                        text = stringResource(
                            R.string.challenge_score_detail,
                            percentage,
                            formatGameTime(game.elapsedSeconds()),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (missedSummary != null) {
                    Text(
                        text = missedSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onPlayAgain,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VoyagePalette.buttonColor, contentColor = Color.White),
                    ) {
                        Text(stringResource(R.string.challenge_play_again))
                    }
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoyagePalette.buttonColor),
                    ) {
                        Text(stringResource(R.string.challenge_done))
                    }
                }
            }
        }
        if (game.isNewBest) Confetti()
    }
}

@Composable
private fun ConfirmDialog(title: Int, message: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.challenge_keep_playing)) }
        },
    )
}

private const val CLOCK_TICK_MILLIS = 500L
private const val MAX_SUGGESTIONS = 5
private val SUGGESTIONS_MAX_HEIGHT = 200.dp
