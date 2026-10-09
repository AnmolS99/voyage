package com.anmol.voyage.ui.home

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.data.FlagEmoji
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.VisitProgress
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.country.FlagText
import com.anmol.voyage.ui.map.StatusButtonColors
import com.anmol.voyage.ui.map.StatusButtons
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Home's bottom chrome: a compact progress dock (count · bar · % · +) that
 * morphs into the selected country's card — a port of iOS `CountriesDock`.
 *
 * It is one surface, not two composables swapping: the surface persists and
 * its padding, width and corner radius animate, so it grows upward from a
 * fixed bottom edge. The progress row persists too, shrinking into the card's
 * slim bottom row and growing back into the dock — a large and a small copy
 * crossfading would show two counts on top of each other.
 *
 * The country's header and buttons sit above the progress row in a container
 * whose height animates from nothing to their full height, clipped, so they
 * unfold upward out of the row's top edge and fold back down into it. An
 * enter/exit transition on them instead grows them from the card's bottom.
 *
 * @param countryNamed the parsed country behind a name, for its flag and capital.
 * @param compact a phone on its side: a lower dock, and a card whose header,
 *   buttons and close button share one row.
 */
@Composable
internal fun CountriesDock(
    state: VoyageState,
    countryNamed: (String) -> GeoJsonCountry?,
    compact: Boolean,
    onAddCountry: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = if (compact) DockMetrics.Compact else DockMetrics.Regular
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current

    val selected = state.selectedCountry
    val isOpen = selected != null
    // Kept so the card's content is still there to fold away while it closes.
    val shown = rememberLastNonNull(selected)

    // A country un-visited from this card; see VisitProgress.previewsVisit.
    var justUnvisited by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) { justUnvisited = null }

    val progress = VisitProgress.of(state.visitedCountries)
    val previewsVisit = VisitProgress.previewsVisit(
        selected = selected,
        isVisited = selected != null && state.isVisited(selected),
        justUnvisited = justUnvisited,
    )

    val morphDp = morph<Dp>(isOpen, reduceMotion)
    val padStart by animateDpAsState(if (isOpen) metrics.cardPadStart else metrics.dockPadStart, morphDp)
    val padTop by animateDpAsState(if (isOpen) metrics.cardPadTop else metrics.dockInset, morphDp)
    val padEnd by animateDpAsState(if (isOpen) metrics.cardPadEnd else metrics.dockInset, morphDp)
    val padBottom by animateDpAsState(if (isOpen) metrics.cardPadBottom else metrics.dockInset, morphDp)
    val radius by animateDpAsState(if (isOpen) metrics.cardRadius else metrics.dockHeight / 2, morphDp)
    val maxWidth by animateDpAsState(if (isOpen) metrics.cardMaxWidth else metrics.dockMaxWidth, morphDp)
    val shape = RoundedCornerShape(radius)

    // The swipe down that closes the card: the card follows at 0.6× the finger.
    var dragged by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dismissDistance = with(density) { 50.dp.toPx() }
    val dismissVelocity = with(density) { 800.dp.toPx() }
    LaunchedEffect(isOpen) { if (!isOpen) dragged = 0f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 21.dp)
            .padding(bottom = metrics.bottomGap),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                // The whole surface owns its touches: a gap between rows must
                // neither pan the globe nor select the country beneath it, and
                // must still start the swipe.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                // Outside the offset it drives, so the finger is measured in
                // the screen's terms rather than the moving card's.
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = isOpen,
                    state = rememberDraggableState { delta -> dragged += delta },
                    onDragStopped = { velocity ->
                        if (dragged > dismissDistance || velocity > dismissVelocity) state.clearSelection()
                        animate(dragged, 0f, animationSpec = spring(0.8f, stiffnessFor(0.3f))) { value, _ ->
                            dragged = value
                        }
                    },
                )
                .offset { IntOffset(0, (dragged.coerceAtLeast(0f) * DRAG_RESISTANCE).roundToInt()) }
                .shadow(6.dp, shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .clip(shape)
                .padding(start = padStart, top = padTop, end = padEnd, bottom = padBottom),
        ) {
            FoldUp(open = isOpen, reduceMotion = reduceMotion) {
                if (shown != null) {
                    CardContent(
                        country = shown,
                        countryNamed = countryNamed,
                        state = state,
                        metrics = metrics,
                        reduceMotion = reduceMotion,
                        onExplore = onExplore,
                        onToggleVisit = {
                            if (state.isVisited(shown)) {
                                justUnvisited = shown
                                state.removeVisit(shown)
                            } else {
                                state.addVisit(shown)
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        },
                        onToggleWish = {
                            state.toggleWishlist(shown)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        },
                        modifier = Modifier.padding(bottom = metrics.cardRowSpacing),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRow(
                    progress = progress,
                    previewsVisit = previewsVisit,
                    open = isOpen,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                )
                AddButton(visible = !isOpen, metrics = metrics, reduceMotion = reduceMotion, onClick = onAddCountry)
            }
        }
    }
}

/** How far the card moves per pixel the finger drags it down. */
private const val DRAG_RESISTANCE = 0.6f

// region Animation

/**
 * The spring of SwiftUI's `.spring(response:dampingFraction:)`, whose
 * `response` is the undamped period: stiffness (2π / response)² at unit mass.
 */
private fun stiffnessFor(responseSeconds: Float): Float = (2 * PI / responseSeconds).let { (it * it).toFloat() }

private val OPEN_STIFFNESS = stiffnessFor(0.42f)
private val CLOSE_STIFFNESS = stiffnessFor(0.36f)
private const val MORPH_DAMPING = 0.82f

/**
 * The morph between dock and card, as iOS: a spring to open, the same a little
 * faster to close — or, with animations turned off, a plain short resize.
 */
private fun <T> morph(open: Boolean, reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) {
        tween(durationMillis = 250)
    } else {
        spring(dampingRatio = MORPH_DAMPING, stiffness = if (open) OPEN_STIFFNESS else CLOSE_STIFFNESS)
    }

/** Visiting: the bar's fill, as iOS's `.spring(response: 0.4, dampingFraction: 0.8)`. */
private fun <T> visitSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = stiffnessFor(0.4f))

/**
 * Whether the user has turned animations off, Android's nearest to iOS's
 * Reduce Motion. Compose already skips most motion then; the dock also swaps
 * its springs and slides for plain resizes and fades, as iOS does.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * [content], revealed from the bottom up: its height animates between nothing
 * and its full height while the content stays pinned to the bottom edge,
 * clipped — so it unfolds upward out of whatever sits below it.
 *
 * The height follows the content's too, with the same spring, so switching
 * between countries whose header takes one line and two resizes the card
 * rather than snapping it.
 */
@Composable
private fun FoldUp(open: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val height = remember { Animatable(0f) }
    var natural by remember { mutableIntStateOf(0) }
    val target = if (open) natural.toFloat() else 0f
    LaunchedEffect(target) { height.animateTo(target, morph(open, reduceMotion)) }
    val alpha by animateFloatAsState(if (open) 1f else 0f, morph(open, reduceMotion))

    Layout(
        content = content,
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
            .then(if (open) Modifier else Modifier.closedToInput()),
    ) { measurables, constraints ->
        val placeable = measurables.firstOrNull()
            ?.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        natural = placeable?.height ?: 0
        // Not capped at the content's height: a header that just lost a line
        // sits on the bottom edge while the space above it closes.
        val shownHeight = height.value.roundToInt().coerceAtLeast(0)
        // Full width even while empty, so only the height animates.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable?.width ?: 0
        layout(width, shownHeight) {
            placeable?.place(0, shownHeight - placeable.height)
        }
    }
}

/** Swallows touches and hides from TalkBack: what is folding away is not there. */
private fun Modifier.closedToInput(): Modifier = this
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * [value], or while it is null the last value it was not — what a closing card
 * keeps drawing. Held outside snapshot state: it only ever changes alongside
 * [value], so it never needs to trigger a recomposition of its own.
 */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

// endregion

// region Progress (dock row ↔ card bottom row)

/**
 * The count, the bar and the percent: one persistent row in both states. Its
 * text sizes, bar height and spacing animate, so it shrinks into the card and
 * grows back into the dock rather than crossfading between two copies.
 */
@Composable
private fun ProgressRow(
    progress: VisitProgress,
    previewsVisit: Boolean,
    open: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val countSize by animateFloatAsState(if (open) 12f else 15f, morph(open, reduceMotion))
    val percentSize by animateFloatAsState(if (open) 12f else 14f, morph(open, reduceMotion))
    val barHeight by animateDpAsState(if (open) 4.dp else 6.dp, morph(open, reduceMotion))
    val spacing by animateDpAsState(if (open) 9.dp else 12.dp, morph(open, reduceMotion))
    val description = stringResource(R.string.dock_progress, progress.visited, progress.total, progress.percent)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RollingNumber(
                value = progress.visited,
                text = { "$it" },
                style = TextStyle(
                    fontSize = countSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFeatureSettings = TABULAR,
                ),
                reduceMotion = reduceMotion,
            )
            Text(
                text = "/${progress.total}",
                style = TextStyle(
                    fontSize = countSize.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFeatureSettings = TABULAR,
                ),
            )
        }

        DockProgressBar(
            fraction = progress.fraction,
            previewFraction = if (previewsVisit) 1f / progress.total else 0f,
            height = barHeight,
            modifier = Modifier.weight(1f),
        )

        RollingNumber(
            value = progress.percent,
            text = { stringResource(R.string.dock_percent, it) },
            style = TextStyle(
                fontSize = percentSize.sp,
                fontWeight = FontWeight.Bold,
                color = VoyagePalette.buttonColor,
                fontFeatureSettings = TABULAR,
            ),
            reduceMotion = reduceMotion,
        )
    }
}

/** Tabular figures, so a rolling count does not jitter sideways. */
private const val TABULAR = "tnum"

/**
 * [value] as text that rolls when it changes — up when it grows, down when it
 * shrinks — as iOS's `.contentTransition(.numericText)`.
 */
@Composable
private fun RollingNumber(value: Int, text: @Composable (Int) -> String, style: TextStyle, reduceMotion: Boolean) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            val enter = if (reduceMotion) {
                fadeIn(tween(150))
            } else {
                slideInVertically(visitSpring()) { if (up) it else -it } + fadeIn(tween(150))
            }
            val exit = if (reduceMotion) {
                fadeOut(tween(150))
            } else {
                slideOutVertically(visitSpring()) { if (up) -it else it } + fadeOut(tween(150))
            }
            // No size animation: the row resizes with the font, and tabular
            // digits keep the width steady across a roll anyway.
            (enter togetherWith exit) using null
        },
        modifier = Modifier.clipToBounds(),
        label = "rolling number",
    ) { shown ->
        Text(text = text(shown), style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * A capsule bar with an optional preview segment, at 40% of the accent, of the
 * fill a visit would add.
 *
 * The preview is always drawn, at zero width when there is none, so it grows
 * out of and shrinks back into the fill's end instead of fading in on its own.
 */
@Composable
private fun DockProgressBar(fraction: Float, previewFraction: Float, height: Dp, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(fraction, visitSpring())
    val preview by animateFloatAsState(previewFraction, visitSpring())
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val accent = VoyagePalette.buttonColor

    Canvas(modifier = modifier.height(height)) {
        val width = size.width
        val barHeight = size.height
        val corner = CornerRadius(barHeight / 2)
        // Never narrower than the bar is tall, so no visits still shows a dot.
        val fillWidth = max(barHeight, width * fill).coerceAtMost(width)
        drawRoundRect(color = track, cornerRadius = corner)
        drawRoundRect(
            color = accent.copy(alpha = 0.4f),
            size = Size(min(width, fillWidth + width * preview), barHeight),
            cornerRadius = corner,
        )
        drawRoundRect(color = accent, size = Size(fillWidth, barHeight), cornerRadius = corner)
    }
}

/**
 * The dock's +, which opens the country search — Android's add-country flow.
 * It shrinks to 60% and fades as the card opens, and returns as it closes.
 */
@Composable
private fun RowScope.AddButton(visible: Boolean, metrics: DockMetrics, reduceMotion: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandIn(morph(false, reduceMotion), expandFrom = Alignment.CenterEnd) +
            scaleIn(morph(false, reduceMotion), initialScale = 0.6f) +
            fadeIn(morph(false, reduceMotion)),
        exit = shrinkOut(morph(true, reduceMotion), shrinkTowards = Alignment.CenterEnd) +
            scaleOut(morph(true, reduceMotion), targetScale = 0.6f) +
            fadeOut(morph(true, reduceMotion)),
    ) {
        val label = stringResource(R.string.home_add_country)
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(metrics.plusSize)
                .clip(CircleShape)
                .background(VoyagePalette.buttonColor)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(metrics.plusSize * 0.55f),
            )
        }
    }
}

// endregion

// region Card

@Composable
private fun CardContent(
    country: String,
    countryNamed: (String) -> GeoJsonCountry?,
    state: VoyageState,
    metrics: DockMetrics,
    reduceMotion: Boolean,
    onExplore: () -> Unit,
    onToggleVisit: () -> Unit,
    onToggleWish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        CountryHeader(country, countryNamed, reduceMotion, modifier = headerModifier)
    }
    val buttons: @Composable () -> Unit = {
        ActionButtons(
            isVisited = state.isVisited(country),
            isWished = state.isInWishlist(country),
            metrics = metrics,
            onToggleVisit = onToggleVisit,
            onToggleWish = onToggleWish,
            onExplore = onExplore,
        )
    }
    if (metrics.buttonsFillWidth) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(metrics.cardRowSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                header(Modifier.weight(1f))
                CloseButton(onClick = state::clearSelection)
            }
            buttons()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // The buttons keep their natural width ("Visited" is wider than
            // "Visit"); the name and capital beside them give up the space.
            header(Modifier.weight(1f))
            buttons()
            CloseButton(onClick = state::clearSelection)
        }
    }
}

/**
 * Flag, name and capital. Picking another country while the card is open fades
 * the old header out before the new one fades in, settling down from 4 dp above,
 * so the two never overlap.
 */
@Composable
private fun CountryHeader(
    country: String,
    countryNamed: (String) -> GeoJsonCountry?,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val settle = with(LocalDensity.current) { 4.dp.roundToPx() }
    AnimatedContent(
        targetState = country,
        transitionSpec = {
            val fadeInAfterOut = tween<Float>(durationMillis = 200, delayMillis = HEADER_FADE_OUT_MILLIS)
            val enter = if (reduceMotion) {
                fadeIn(tween(150, delayMillis = HEADER_FADE_OUT_MILLIS))
            } else {
                fadeIn(fadeInAfterOut) +
                    slideInVertically(tween(200, delayMillis = HEADER_FADE_OUT_MILLIS)) { -settle }
            }
            // The card's own height animation resizes it; the swap must not.
            (enter togetherWith fadeOut(tween(HEADER_FADE_OUT_MILLIS))) using null
        },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
        label = "country header",
    ) { name ->
        val shownInfo = countryNamed(name)
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (shownInfo != null) {
                FlagText(flag = FlagEmoji.of(shownInfo), country = name, fontSize = 24.sp)
            }
            NameAndCapital(name = name, capital = shownInfo?.capital?.name, modifier = Modifier.weight(1f))
        }
    }
}

private const val HEADER_FADE_OUT_MILLIS = 100

/**
 * Name and capital on one line when both fit at full size; otherwise the
 * capital drops below the name. A name too long even for its own line
 * ("Democratic Republic of the Congo") shrinks to fit, down to half size,
 * rather than being cut off — iOS's `NameAndCapitalLayout`.
 */
@Composable
private fun NameAndCapital(name: String, capital: String?, modifier: Modifier = Modifier) {
    val nameStyle = TextStyle(
        fontSize = NAME_SIZE,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val capitalStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Layout(
        content = {
            BasicText(
                text = name,
                style = nameStyle,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = NAME_SIZE * 0.5f, maxFontSize = NAME_SIZE),
                // Announced as it changes: a country selected from the search
                // sheet is otherwise chosen in silence.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (capital != null) {
                Text(text = capital, style = capitalStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val spacing = 9.dp.roundToPx()
        val lineSpacing = 1.dp.roundToPx()
        // The name's width at full size; its own measurement would already be
        // the shrunk one.
        val nameIdeal = measurer.measure(name, nameStyle, maxLines = 1, softWrap = false).size.width
        val nameMeasurable = measurables[0]
        val capitalMeasurable = measurables.getOrNull(1)

        fun upTo(maxWidth: Int) = Constraints(maxWidth = maxWidth.coerceAtMost(width).coerceAtLeast(0))

        if (capitalMeasurable == null) {
            val namePlaced = nameMeasurable.measure(upTo(nameIdeal))
            return@Layout layout(namePlaced.width, namePlaced.height) { namePlaced.place(0, 0) }
        }

        val capitalIdeal = capitalMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        if (nameIdeal + spacing + capitalIdeal <= width) {
            // One line, centered on each other.
            val namePlaced = nameMeasurable.measure(upTo(nameIdeal))
            val capitalPlaced = capitalMeasurable.measure(upTo(capitalIdeal))
            val height = max(namePlaced.height, capitalPlaced.height)
            layout(namePlaced.width + spacing + capitalPlaced.width, height) {
                namePlaced.place(0, (height - namePlaced.height) / 2)
                capitalPlaced.place(namePlaced.width + spacing, (height - capitalPlaced.height) / 2)
            }
        } else {
            val namePlaced = nameMeasurable.measure(upTo(nameIdeal))
            val capitalPlaced = capitalMeasurable.measure(upTo(capitalIdeal))
            layout(
                max(namePlaced.width, capitalPlaced.width),
                namePlaced.height + lineSpacing + capitalPlaced.height,
            ) {
                namePlaced.place(0, 0)
                capitalPlaced.place(0, namePlaced.height + lineSpacing)
            }
        }
    }
}

private val NAME_SIZE = 18.sp

/** The card's ✕: a subtle circle with a red glyph. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val label = stringResource(R.string.dock_close)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(subtleFill())
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Visit, Wish and Explore. */
@Composable
private fun ActionButtons(
    isVisited: Boolean,
    isWished: Boolean,
    metrics: DockMetrics,
    onToggleVisit: () -> Unit,
    onToggleWish: () -> Unit,
    onExplore: () -> Unit,
) {
    val fill = metrics.buttonsFillWidth
    Row(
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.buttonSpacing),
    ) {
        val each = if (fill) Modifier.weight(1f) else Modifier
        CardActionButton(
            label = stringResource(if (isVisited) R.string.country_visited else R.string.dock_visit),
            icon = if (isVisited) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
            colors = StatusButtons.visit(isVisited),
            metrics = metrics,
            onClick = onToggleVisit,
            modifier = each,
        )
        CardActionButton(
            label = stringResource(if (isWished) R.string.dock_wished else R.string.dock_wish),
            icon = if (isWished) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            colors = StatusButtons.wish(isWished),
            metrics = metrics,
            onClick = onToggleWish,
            bounceOn = isWished,
            modifier = each,
        )
        CardActionButton(
            label = stringResource(R.string.dock_explore),
            icon = Icons.Rounded.Explore,
            colors = null,
            metrics = metrics,
            onClick = onExplore,
            modifier = each,
        )
    }
}

/**
 * One of the card's capsule buttons. With [colors] it is solid; without, a
 * subtle fill with an accent icon.
 *
 * @param bounceOn the icon bounces when this turns true — iOS's `.bounce`
 *   symbol effect on the wishlist heart.
 */
@Composable
private fun CardActionButton(
    label: String,
    icon: ImageVector,
    colors: StatusButtonColors?,
    metrics: DockMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bounceOn: Boolean = false,
) {
    val container by animateColorAsState(colors?.container ?: subtleFill(), tween(200), label = "button fill")
    val content by animateColorAsState(colors?.content ?: MaterialTheme.colorScheme.onSurface, tween(200))
    val iconTint by animateColorAsState(colors?.content ?: VoyagePalette.buttonColor, tween(200))

    val bounce = remember { Animatable(1f) }
    var bounced by remember { mutableStateOf(bounceOn) }
    LaunchedEffect(bounceOn) {
        if (bounceOn && !bounced) {
            bounce.animateTo(1.25f, tween(90))
            bounce.animateTo(1f, spring(dampingRatio = 0.4f))
        }
        bounced = bounceOn
    }

    Box(
        modifier = modifier
            .height(metrics.buttonHeight)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            // Equal-width buttons get just enough inset to fit "Explore".
            .padding(horizontal = if (metrics.buttonsFillWidth) 6.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp).scale(bounce.value),
            )
            BasicText(
                text = label,
                style = TextStyle(fontSize = metrics.buttonFontSize, fontWeight = FontWeight.SemiBold, color = content),
                maxLines = 1,
                softWrap = false,
                // iOS lets these shrink to 85% before giving up characters.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = metrics.buttonFontSize * 0.85f,
                    maxFontSize = metrics.buttonFontSize,
                ),
            )
        }
    }
}

/** Fill for the card's Explore and close buttons. */
@Composable
private fun subtleFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

// endregion

/** Sizes for the dock and card, upright and on its side — iOS `CountriesDock.Metrics`. */
private class DockMetrics(
    val dockHeight: Dp,
    val plusSize: Dp,
    val dockMaxWidth: Dp,
    val cardMaxWidth: Dp,
    val cardRadius: Dp,
    val cardPadTop: Dp,
    val cardPadStart: Dp,
    val cardPadBottom: Dp,
    val cardPadEnd: Dp,
    val cardRowSpacing: Dp,
    val buttonHeight: Dp,
    val buttonFontSize: TextUnit,
    val buttonSpacing: Dp,
    /** Equal-width buttons upright; sized to fit in the compact single row. */
    val buttonsFillWidth: Boolean,
    val bottomGap: Dp,
) {
    /** The + sits this far from the dock's top, bottom and end edges. */
    val dockInset: Dp get() = (dockHeight - plusSize) / 2
    val dockPadStart: Dp get() = 18.dp

    companion object {
        val Regular = DockMetrics(
            dockHeight = 56.dp, plusSize = 42.dp, dockMaxWidth = 480.dp, cardMaxWidth = 480.dp,
            cardRadius = 26.dp, cardPadTop = 11.dp, cardPadStart = 16.dp, cardPadBottom = 13.dp, cardPadEnd = 11.dp,
            cardRowSpacing = 11.dp, buttonHeight = 36.dp, buttonFontSize = 14.sp, buttonSpacing = 7.dp,
            buttonsFillWidth = true, bottomGap = 10.dp,
        )

        val Compact = DockMetrics(
            dockHeight = 46.dp, plusSize = 36.dp, dockMaxWidth = 514.dp, cardMaxWidth = 640.dp,
            cardRadius = 24.dp, cardPadTop = 9.dp, cardPadStart = 16.dp, cardPadBottom = 11.dp, cardPadEnd = 9.dp,
            cardRowSpacing = 9.dp, buttonHeight = 34.dp, buttonFontSize = 13.5.sp, buttonSpacing = 6.dp,
            buttonsFillWidth = false, bottomGap = 8.dp,
        )
    }
}
