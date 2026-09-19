package com.anmol.voyage.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * Material's shared-axis X transition, for the one push within a tab: a mode on
 * the Challenges tab opening its region picker, iOS's `NavigationLink` slide.
 *
 * Only that pair animates. Tab switches stay instant (see `VoyageApp`), and so
 * does a game starting or ending: the game is drawn over Home's globe, which
 * appears the moment the game's route does, and a picker sliding away over it
 * would show the globe through the fade.
 *
 * The outgoing screen slides a tenth of the width and fades while the incoming
 * one does the same from the other side — Material's 30 dp, in proportion.
 * [SlideDirection.Start] and [SlideDirection.End] follow the layout direction,
 * so the push runs the other way in a right-to-left language.
 */
internal typealias Transition = AnimatedContentTransitionScope<NavBackStackEntry>

private const val DURATION_MS = 300

private val slide = tween<IntOffset>(DURATION_MS, easing = FastOutSlowInEasing)
private val fade = tween<Float>(DURATION_MS)

private fun Transition.pushes(): Boolean =
    initialState.destination.route == VoyageDestination.Challenges.route &&
        targetState.destination.route == ChallengeRoutes.REGION_SELECT

private fun Transition.pops(): Boolean =
    initialState.destination.route == ChallengeRoutes.REGION_SELECT &&
        targetState.destination.route == VoyageDestination.Challenges.route

private fun Transition.enterFrom(direction: SlideDirection): EnterTransition =
    slideIntoContainer(direction, slide) { it / 10 } + fadeIn(fade)

private fun Transition.exitTo(direction: SlideDirection): ExitTransition =
    slideOutOfContainer(direction, slide) { it / 10 } + fadeOut(fade)

/** The Challenges list leaving as a region picker is pushed over it. */
internal fun Transition.challengesToRegionsExit(): ExitTransition =
    if (pushes()) exitTo(SlideDirection.Start) else ExitTransition.None

/** A region picker arriving over the Challenges list. */
internal fun Transition.challengesToRegionsEnter(): EnterTransition =
    if (pushes()) enterFrom(SlideDirection.Start) else EnterTransition.None

/** A region picker leaving on back. */
internal fun Transition.regionsToChallengesExit(): ExitTransition =
    if (pops()) exitTo(SlideDirection.End) else ExitTransition.None

/** The Challenges list returning from a region picker. */
internal fun Transition.regionsToChallengesEnter(): EnterTransition =
    if (pops()) enterFrom(SlideDirection.End) else EnterTransition.None
