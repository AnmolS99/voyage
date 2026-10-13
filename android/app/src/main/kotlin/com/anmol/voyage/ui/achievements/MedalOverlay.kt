package com.anmol.voyage.ui.achievements

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.anmol.voyage.R
import com.anmol.voyage.data.Achievement
import com.anmol.voyage.data.MedalTier
import com.anmol.voyage.ui.challenges.Confetti
import com.anmol.voyage.ui.theme.VoyagePalette
import java.util.function.Consumer

/**
 * The medal, full screen and spinnable — what tapping the small medal on a card
 * opens. The Android counterpart of iOS's `MedalOverlayView`.
 *
 * It is a [Dialog] rather than an overlay drawn inside the screen, which is what
 * gives it the whole window (the bottom bar included, as iOS's covers the tab
 * bar), a scrim, and dismissal by the system back gesture — the one exit an
 * Android user will always reach for. What it does not port is iOS's flight from
 * the small medal's frame to the middle of the screen: that is a shared-element
 * transition here, several times the code of the thing it decorates, so the coin
 * springs up in place instead.
 *
 * @param celebrating whether the medal has just been earned. The same overlay
 *   then announces it — a heading over the coin and the challenges' confetti
 *   bursting behind it — which is what `AchievementUnlockCelebration` opens.
 */
@Composable
fun MedalOverlay(
    achievement: Achievement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    celebrating: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Full window rather than the platform's dialog width: there is no card
        // here to be sized, only a scrim the medal floats on. And behind the
        // system bars, as the app itself draws: a dialog otherwise stops at
        // the status bar, leaving the screen behind undimmed above it.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        EdgeToEdgeDialogWindow()

        // iOS floats the medal on `.ultraThinMaterial`. The platform can blur
        // what is behind a window since API 31, but only when the device and
        // the user's current settings allow it, so the scrim behind the medal
        // does the work whenever the blur is not actually there.
        val blurred = dialogBackgroundBlur(BLUR_RADIUS)

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.scrim.copy(
                        alpha = if (blurred) SCRIM_OVER_BLUR_ALPHA else SCRIM_ALPHA,
                    ),
                )
                // Anywhere off the medal dismisses, as tapping iOS's blur does.
                // No ripple: there is no surface here for one to belong to.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.medal_close),
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // A Surface would normally hand its content a matching color;
            // there is none here, and the scrim is dark whatever the theme, so
            // the text is light in both.
            CompositionLocalProvider(LocalContentColor provides ON_SCRIM) {
                // The scrim runs under the system bars; the medal and its text
                // stay clear of them.
                BoxWithConstraints(
                    modifier = Modifier.safeDrawingPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (maxWidth > maxHeight) {
                        // Landscape: there is no height under the coin for the
                        // text, so it moves beside the coin, which stays in the
                        // middle of the screen — as on iOS.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            SpinningMedal(
                                medal = achievement.medal,
                                tier = achievement.tier,
                                size = COIN_SIZE,
                                shineAtOnce = celebrating,
                            )
                            MedalDetails(
                                achievement = achievement,
                                celebrating = celebrating,
                                onDismiss = onDismiss,
                                beside = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 32.dp),
                            )
                        }
                    } else {
                        // The coin sits in the middle of the screen, as on iOS,
                        // with the heading hung above it and the details below,
                        // so neither pulls it off center. Only when the details
                        // would run off a short screen does it move up.
                        Layout(
                            // The coin comes first so it draws first: its beams
                            // reach past it, and the text belongs on top of them.
                            content = {
                                SpinningMedal(
                                    medal = achievement.medal,
                                    tier = achievement.tier,
                                    size = COIN_SIZE,
                                    shineAtOnce = celebrating,
                                )
                                Box { if (celebrating) UnlockedHeading(achievement, TextAlign.Center) }
                                MedalDetails(
                                    achievement = achievement,
                                    celebrating = celebrating,
                                    onDismiss = onDismiss,
                                    beside = false,
                                    modifier = Modifier.padding(top = 20.dp),
                                )
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                        ) { measurables, constraints ->
                            val loose = constraints.copy(minWidth = 0, minHeight = 0)
                            val (coin, heading, details) = measurables.map { it.measure(loose) }
                            val width = constraints.maxWidth
                            val height = constraints.maxHeight
                            val coinTop = ((height - coin.height) / 2)
                                .coerceAtMost(height - coin.height - details.height)
                                .coerceAtLeast(heading.height)
                            layout(width, height) {
                                heading.placeRelative((width - heading.width) / 2, coinTop - heading.height)
                                coin.placeRelative((width - coin.width) / 2, coinTop)
                                details.placeRelative((width - details.width) / 2, coinTop + coin.height)
                            }
                        }
                    }
                }
            }
            // Last, so it falls in front of the medal; it draws only, so taps
            // still reach the coin and the scrim beneath it.
            if (celebrating) Confetti()
        }
    }
}

/**
 * "Silver medal unlocked!" in the light end of that medal's metal, over the coin
 * in portrait and over the text in landscape.
 */
@Composable
private fun UnlockedHeading(achievement: Achievement, textAlign: TextAlign) {
    val tier = achievement.tier ?: MedalTier.Bronze
    Text(
        text = tier.unlockedTitle(),
        style = MaterialTheme.typography.titleLarge,
        color = metalOf(tier).center,
        textAlign = textAlign,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .semantics { heading() },
    )
}

/**
 * The medal's name, progress, medal status, spin hint and close button: under the coin in
 * portrait, or [beside] it — left-aligned, with the unlock heading on top — in
 * landscape.
 */
@Composable
private fun MedalDetails(
    achievement: Achievement,
    celebrating: Boolean,
    onDismiss: () -> Unit,
    beside: Boolean,
    modifier: Modifier = Modifier,
) {
    val textAlign = if (beside) TextAlign.Start else TextAlign.Center
    Column(
        modifier = modifier,
        horizontalAlignment = if (beside) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (beside && celebrating) UnlockedHeading(achievement, textAlign)
        Text(
            text = achievement.kind.title(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = textAlign,
        )
        Text(
            text = stringResource(
                R.string.achievement_progress,
                achievement.current,
                achievement.total,
                achievement.unit.label(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = ON_SCRIM_MUTED,
            textAlign = textAlign,
        )
        Text(
            text = achievement.medalStatus(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            // The scrim is dark in either theme, so the light end of the metal
            // reads where the card's accent would not.
            color = achievement.tier?.let { metalOf(it).center } ?: VoyagePalette.buttonColor,
            textAlign = textAlign,
        )

        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = ON_SCRIM_MUTED,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.medal_drag_to_spin),
                style = MaterialTheme.typography.labelLarge,
                color = ON_SCRIM_MUTED,
            )
        }

        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(if (celebrating) R.string.achievement_unlocked_done else R.string.medal_close))
        }
    }
}

/**
 * Asks the platform to blur whatever is behind this dialog, and reports whether
 * it is actually blurring.
 *
 * Cross-window blur exists from API 31 but is not a given even there: it is off
 * on devices that cannot afford it, and the system switches it off at runtime
 * for battery saver. So this listens rather than asking once — the answer can
 * change while the medal is open, and the scrim behind it has to thicken the
 * moment it does.
 */
@Composable
private fun dialogBackgroundBlur(radius: Dp): Boolean {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    var blurring by remember { mutableStateOf(false) }

    DisposableEffect(view, radiusPx) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || window == null) {
            return@DisposableEffect onDispose { }
        }
        window.blurBehind(radiusPx)

        // Called immediately with the current state, and again on every change.
        val windowManager = view.context.getSystemService(WindowManager::class.java)
        val listener = Consumer<Boolean> { enabled -> blurring = enabled }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }

    return blurring
}

/**
 * Lets this dialog's window reach into the status bar and display cutout, with
 * the bars themselves transparent and their icons light over the dark scrim.
 * `decorFitsSystemWindows = false` alone lays the content out under the bars
 * but leaves the cutout band, and on older releases the bars' own color, to
 * the platform.
 */
@Composable
private fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Edge-to-edge from API 35 on; before it the bars keep a color.
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose { }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.blurBehind(radiusPx: Int) {
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    // The params have to be handed back for the change to take effect.
    attributes = attributes.apply { blurBehindRadius = radiusPx }
}

/**
 * The coin, turning about its vertical axis.
 *
 * The spin is stepped once per rendered frame from [MedalSpin], for the reason
 * the globe's is: one clock and one writer, so a drag, a coast and the idle turn
 * can never be moving the same angle at once. Past a quarter turn the reverse
 * face is drawn, counter-rotated so it is not a mirror image of the front.
 */
@Composable
private fun SpinningMedal(medal: String, tier: MedalTier?, size: Dp, shineAtOnce: Boolean) {
    val spin = remember { MedalSpin() }

    LaunchedEffect(spin) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (previousFrame != 0L) {
                    val dt = (now - previousFrame) / NANOS_PER_SECOND
                    spin.advance(dt.coerceIn(0f, MAX_FRAME_STEP))
                }
                previousFrame = now
            }
        }
    }

    // The coin springs up rather than appearing at full size, standing in for
    // iOS's animation out of the card's small medal. The flag is flipped after
    // the first composition on purpose: `animateFloatAsState` starts *at* its
    // target, so there is nothing to animate unless the target changes.
    var shown by remember { mutableStateOf(false) }
    var fullSize by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val entry by animateFloatAsState(
        targetValue = if (shown) 1f else ENTRY_SCALE,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "medal-entry",
        finishedListener = { fullSize = true },
    )
    // An unlock shines from the moment the coin springs up; a medal opened from
    // its card only once the coin has reached full size, fading in then — as
    // iOS waits for its coin to finish flying out of the card.
    val raysAlpha by animateFloatAsState(
        targetValue = if (shineAtOnce || fullSize) 1f else 0f,
        animationSpec = tween(RAYS_FADE_MILLIS),
        label = "medal-rays-fade",
    )

    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(spin) { trackSpin(spin) }
            .graphicsLayer {
                scaleX = entry
                scaleY = entry
            },
        contentAlignment = Alignment.Center,
    ) {
        // Beams in the medal's color once it is earned, reaching past the coin
        // without taking up any more room than it. The scrim is dark in either
        // theme, so it is the light end of the metal that shines.
        if (tier != null) {
            MedalRays(
                color = metalOf(tier).center,
                modifier = Modifier
                    .requiredSize(size * RAYS_SCALE)
                    .graphicsLayer { alpha = raysAlpha },
            )
        }
        // The turn is handed over as a function, not a value: the coin re-reads
        // it while drawing, so a spinning medal never recomposes.
        MedalCoin(
            medal = medal,
            tier = tier,
            size = size,
            rotationDegrees = { spin.degrees },
        )
    }
}

/**
 * Turns horizontal drags into spin, and the finger's parting speed into a coast.
 *
 * Written out rather than reaching for `detectHorizontalDragGestures` for the
 * same reason the globe's `trackFlicks` is: what the coin needs is the two
 * things that detector does not report — the moment the finger lands, which
 * stops a spin still in flight, and how fast it was moving when it left.
 * Travel is accumulated in dp, since that is what [MedalSpin] turns into degrees.
 */
private suspend fun PointerInputScope.trackSpin(spin: MedalSpin) {
    awaitEachGesture {
        // Consumed, so the tap does not fall through to the scrim behind and
        // dismiss the overlay: on iOS the coin is its own view and only the
        // blur around it closes things.
        awaitFirstDown(requireUnconsumed = false).consume()
        spin.startDrag()

        val velocity = VelocityTracker()
        var travel = 0f
        var event: PointerEvent
        do {
            event = awaitPointerEvent()
            val moving = event.changes.filter { it.pressed && it.previousPressed }
            if (moving.isNotEmpty()) {
                var pan = 0f
                for (change in moving) pan += change.position.x - change.previousPosition.x
                val dp = pan / (moving.size * density)
                spin.dragBy(dp)
                travel += dp
                velocity.addPosition(moving.first().uptimeMillis, Offset(travel, 0f))
            }
        } while (event.changes.any { it.pressed })

        spin.endDrag(velocity.calculateVelocity().x)
    }
}

private val COIN_SIZE = 280.dp

/** Nanoseconds in a second, as a float. */
private const val NANOS_PER_SECOND = 1_000_000_000f

/** The longest step one frame may take, in seconds. The globe clamps the same. */
private const val MAX_FRAME_STEP = 0.1f

private const val ENTRY_SCALE = 0.7f

/** How long a medal opened from its card takes to fade its beams in. iOS's 0.35 s. */
private const val RAYS_FADE_MILLIS = 350

/** How much the medal's blur softens what is behind it. */
private val BLUR_RADIUS = 32.dp

/**
 * How far the scrim dims the screen behind the medal when there is no blur —
 * enough that the achievements list stops competing with the medal's own text.
 */
private const val SCRIM_ALPHA = 0.72f

/** The same scrim over a blur, which is already doing most of the separating. */
private const val SCRIM_OVER_BLUR_ALPHA = 0.4f

private val ON_SCRIM = Color.White

private val ON_SCRIM_MUTED = Color.White.copy(alpha = 0.72f)
