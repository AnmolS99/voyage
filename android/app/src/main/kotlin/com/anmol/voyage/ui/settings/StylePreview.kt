package com.anmol.voyage.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import com.anmol.voyage.R
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.globe.GlobeInertia
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.ui.home.GlobeBackdrop
import com.anmol.voyage.ui.theme.LocalVoyageFonts
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A texture shown full screen at the size Home draws it, grown out of its
 * thumbnail and settling back into one on close — iOS `StylePreview`.
 *
 * The globe is Home's globe at its default zoom — same camera, same 45° field of
 * view, so the same diameter for the same screen ([SphereProjection.homeGlobeDiameter])
 * — over the same backdrop. It turns once a minute until touched, drags and
 * coasts on Home's pan speed and decay ([GlobeCamera.degreesPerDp],
 * [GlobeInertia]), and is drawn with no 3D engine: an AGSL shader ray-casts it
 * from API 33, and below that [TurntableLookup] spins a precomputed disc. It
 * shows the texture and the terrain only — no countries or borders.
 *
 * The map fills the screen's height and pans, as Home's does at its widest.
 *
 * The three styles can be compared without leaving: the switcher at the bottom
 * crossfades between them, and "Use" chooses the one on screen. Closing — the
 * button, back, a tap or a downward swipe off the globe — flies it back into the
 * thumbnail of the style on screen, turned back to where that thumbnail looks.
 */
@Composable
internal fun StylePreview(
    surface: StyleSurface,
    initialStyle: GlobeStyle,
    currentStyle: GlobeStyle,
    terrain: Boolean,
    isDark: Boolean,
    originOf: (GlobeStyle) -> Rect?,
    onPreviewing: (GlobeStyle) -> Unit,
    onUse: (GlobeStyle) -> Unit,
    onDismissed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var previewed by remember { mutableStateOf(initialStyle) }

    fun close(use: GlobeStyle? = null) {
        if (closing) return
        closing = true
        if (use != null) onUse(use)
        scope.launch {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            onDismissed()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PreviewWindow(lightBars = !isDark && surface == StyleSurface.Globe)
        var rootOffset by remember { mutableStateOf(Offset.Zero) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.positionOnScreen() }
                .testTag(STYLE_PREVIEW_TAG),
        ) {
            val viewport = with(LocalDensity.current) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val origin = originOf(previewed)?.translate(-rootOffset)
            when (surface) {
                StyleSurface.Globe -> GlobePreviewContent(
                    style = previewed,
                    terrain = terrain,
                    isDark = isDark,
                    viewport = viewport,
                    origin = origin,
                    progress = { progress.value },
                    closing = closing,
                    onDismissDrag = { fraction -> scope.launch { progress.snapTo(1f - fraction) } },
                    onDismissRelease = { dismiss ->
                        if (dismiss) {
                            close()
                        } else {
                            scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.86f)) }
                        }
                    },
                )
                StyleSurface.Map -> MapPreviewContent(
                    style = previewed,
                    terrain = terrain,
                    viewport = viewport,
                    origin = origin,
                    progress = { progress.value },
                    closing = closing,
                    onDismissDrag = { fraction -> scope.launch { progress.snapTo(1f - fraction) } },
                    onDismissRelease = { dismiss ->
                        if (dismiss) {
                            close()
                        } else {
                            scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.86f)) }
                        }
                    },
                )
            }

            PreviewChrome(
                surface = surface,
                previewed = previewed,
                currentStyle = currentStyle,
                progress = { progress.value },
                onPreview = {
                    previewed = it
                    onPreviewing(it)
                },
                onUse = { close(use = previewed) },
                onClose = { close() },
            )
        }
    }
}

/**
 * The globe: the backdrop fading in behind it, and the disc growing from the
 * thumbnail's circle to Home's.
 */
@Composable
private fun GlobePreviewContent(
    style: GlobeStyle,
    terrain: Boolean,
    isDark: Boolean,
    viewport: Size,
    origin: Rect?,
    progress: () -> Float,
    closing: Boolean,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val diameter = SphereProjection.homeGlobeDiameter(viewport.height)
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val target = Rect(center, diameter / 2f)
    val start = origin ?: target.scaledAboutCenter(0.86f)

    // The camera, and where it was when closing began: closing turns it back to
    // the thumbnail's view as the globe shrinks, so it lands on the same picture.
    var camera by remember { mutableStateOf(PREVIEW_CAMERA) }
    var closedFrom by remember { mutableStateOf<GlobeCamera?>(null) }
    var closedAt by remember { mutableFloatStateOf(1f) }
    val inertia = remember { GlobeInertia() }
    var spinning by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    val texture = rememberPreviewTexture(style, terrain, StyleTextures.GLOBE_PREVIEW_WIDTH)
    val thumbnailPx = with(density) { 64.dp.roundToPx() }
    val thumbnail = rememberGlobeThumbnail(style, terrain, thumbnailPx)
    // Held still until the full texture replaces the thumbnail, which shows
    // the globe from exactly where it starts.
    val textureReady by rememberUpdatedState(texture != null)

    LaunchedEffect(closing) {
        if (closing) {
            closedFrom = camera
            closedAt = progress().coerceAtLeast(0.01f)
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, MAX_FRAME_STEP)
                last = now
                if (dt > 0f && !dragging && textureReady) {
                    camera = when {
                        inertia.isActive -> inertia.step(dt, camera)
                        spinning -> camera.autoRotated(dt)
                        else -> camera
                    }
                }
            }
        }
    }

    // A new style keeps the turn it is at, so switching reads as a repaint.
    val shownCamera = {
        val from = closedFrom
        if (from == null) camera else lerpCamera(PREVIEW_CAMERA, from, progress() / closedAt)
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress().coerceIn(0f, 1f) }) {
        GlobeBackdrop(isDark = isDark)
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(closing, viewport) {
                if (closing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val onGlobe = (down.position - center).getDistance() <= diameter / 2f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    if (onGlobe) {
                        spinning = false
                        inertia.reset()
                        dragging = true
                        try {
                            trackDrag(tracker) { change ->
                                val pan = change.positionChange() / density.density
                                val perDp = camera.degreesPerDp
                                camera = camera.rotatedBy(pan.y * perDp, -pan.x * perDp)
                            }
                        } finally {
                            dragging = false
                        }
                        val velocity = tracker.calculateVelocity()
                        val perDp = camera.degreesPerDp
                        inertia.latitude = (velocity.y / density.density * perDp).toFloat()
                        inertia.longitude = (-velocity.x / density.density * perDp).toFloat()
                    } else {
                        dismissGesture(tracker, down, viewport.height, touchSlop, onDismissDrag, onDismissRelease)
                    }
                }
            },
    ) {
        val sizeDp = with(density) { diameter.toDp() }
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(sizeDp)
                .graphicsLayer {
                    val p = progress()
                    val current = lerp(start, target, p)
                    val scale = current.width / target.width
                    scaleX = scale
                    scaleY = scale
                    translationX = current.center.x - target.center.x
                    translationY = current.center.y - target.center.y
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            // The thumbnail stands in, enlarged, while the full texture decodes.
            Crossfade(targetState = texture, animationSpec = tween(320), label = "texture") { loaded ->
                if (loaded == null) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            filterQuality = FilterQuality.Low,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ShaderGlobe(loaded.bitmap, shownCamera, Modifier.fillMaxSize())
                } else {
                    TurntableGlobe(loaded, shownCamera, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The map: the thumbnail's rounded tile growing into Home's full-height map. */
@Composable
private fun MapPreviewContent(
    style: GlobeStyle,
    terrain: Boolean,
    viewport: Size,
    origin: Rect?,
    progress: () -> Float,
    closing: Boolean,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    // Home's framing (`MapProjection`, iOS `MapFit`): the 2:1 map fills the
    // view, overhanging the sides in portrait and the top and bottom in
    // landscape, and never lets an edge into view.
    val mapHeight = max(viewport.height, viewport.width / 2f)
    val mapSize = Size(mapHeight * 2f, mapHeight)
    val slack = Offset((mapSize.width - viewport.width) / 2f, (mapSize.height - viewport.height) / 2f)
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val texture = rememberPreviewTexture(style, terrain, StyleTextures.MAP_PREVIEW_WIDTH)
    val thumbnail = rememberMapThumbnail(style, terrain)
    val cornerPx = with(density) { MAP_THUMBNAIL_CORNER.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(MaterialTheme.colorScheme.background),
    )

    val touchSlop = LocalViewConfiguration.current.touchSlop
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(closing, mapSize) {
                if (closing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // With no height to pan, a downward swipe closes, as it
                    // does off the globe.
                    var change = down
                    var travelled = Offset.Zero
                    while (travelled.getDistance() < touchSlop) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        tracker.addPosition(change.uptimeMillis, change.position)
                        travelled += change.positionChange()
                    }
                    if (slack.y < 1f && travelled.y > abs(travelled.x)) {
                        dismissGesture(tracker, down, viewport.height, 0f, onDismissDrag, onDismissRelease, change.position)
                        return@awaitEachGesture
                    }
                    scope.launch { panX.stop(); panY.stop() }
                    trackDrag(tracker) { moved ->
                        val delta = moved.positionChange()
                        scope.launch {
                            panX.snapTo((panX.value + delta.x).coerceIn(-slack.x, slack.x))
                            panY.snapTo((panY.value + delta.y).coerceIn(-slack.y, slack.y))
                        }
                    }
                    val velocity = tracker.calculateVelocity()
                    scope.launch {
                        panX.updateBounds(-slack.x, slack.x)
                        panX.animateDecay(velocity.x, exponentialDecay())
                    }
                    scope.launch {
                        panY.updateBounds(-slack.y, slack.y)
                        panY.animateDecay(velocity.y, exponentialDecay())
                    }
                }
            },
    ) {
        val sizeDp = with(density) { DpSize(mapSize.width.toDp(), mapSize.height.toDp()) }
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(sizeDp)
                .graphicsLayer {
                    val p = progress()
                    val target = Rect(
                        Offset(viewport.width / 2f - mapSize.width / 2f + panX.value, viewport.height / 2f - mapSize.height / 2f + panY.value),
                        mapSize,
                    )
                    val start = origin ?: target.scaledAboutCenter(0.86f)
                    val current = lerp(start, target, p)
                    val scale = current.width / target.width
                    scaleX = scale
                    scaleY = scale
                    translationX = current.center.x - viewport.width / 2f
                    translationY = current.center.y - viewport.height / 2f
                    transformOrigin = TransformOrigin.Center
                    // The tile's corners, in the layer's own unscaled units.
                    shape = RoundedCornerShape(cornerPx * (1f - p).coerceIn(0f, 1f) / scale.coerceAtLeast(0.01f))
                    clip = true
                },
        ) {
            Crossfade(targetState = texture?.image ?: thumbnail, animationSpec = tween(320), label = "texture") { image ->
                if (image != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            image = image,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The title and close button at the top, the style switcher and Use button at
 * the bottom — all fading in with the globe and out ahead of it.
 */
@Composable
private fun PreviewChrome(
    surface: StyleSurface,
    previewed: GlobeStyle,
    currentStyle: GlobeStyle,
    progress: () -> Float,
    onPreview: (GlobeStyle) -> Unit,
    onUse: () -> Unit,
    onClose: () -> Unit,
) {
    val chromeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Gone a little ahead of the globe, so it never hangs on its own.
                val p = ((progress() - 0.35f) / 0.65f).coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 12.dp.toPx()
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = chromeColor) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            when (surface) {
                                StyleSurface.Globe -> R.string.settings_globe_style
                                StyleSurface.Map -> R.string.settings_map_style
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = LocalVoyageFonts.current.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Crossfade(targetState = previewed, animationSpec = tween(220), label = "title") { style ->
                        Text(
                            text = stringResource(style.labelRes),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = LocalVoyageFonts.current.display,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = chromeColor,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .testTag(STYLE_PREVIEW_CLOSE_TAG),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.style_preview_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StyleSwitcher(previewed, chromeColor, onPreview)
            Spacer(Modifier.height(12.dp))
            val isCurrent = previewed == currentStyle
            Button(
                onClick = onUse,
                enabled = !isCurrent,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoyagePalette.buttonColor,
                    contentColor = Color.White,
                    disabledContainerColor = chromeColor,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(STYLE_PREVIEW_USE_TAG),
            ) {
                Crossfade(targetState = isCurrent to previewed, animationSpec = tween(200), label = "use") { (current, style) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.style_preview_current),
                                fontFamily = LocalVoyageFonts.current.text,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                stringResource(R.string.style_preview_use, stringResource(style.labelRes)),
                                fontFamily = LocalVoyageFonts.current.text,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The three styles as one pill, the one on screen filled. */
@Composable
private fun StyleSwitcher(previewed: GlobeStyle, background: Color, onPreview: (GlobeStyle) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Row(
            modifier = Modifier.padding(4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            GlobeStyle.entries.forEach { style ->
                val selected = style == previewed
                val fill by animateColorAsState(
                    if (selected) VoyagePalette.buttonColor else Color.Transparent,
                    tween(220),
                    label = "segment",
                )
                val ink by animateColorAsState(
                    if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    tween(220),
                    label = "segmentText",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(fill)
                        .selectable(selected = selected, role = Role.Tab) { onPreview(style) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag(previewStyleTag(style)),
                ) {
                    Text(
                        text = stringResource(style.labelRes),
                        color = ink,
                        fontFamily = LocalVoyageFonts.current.text,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** A preview texture, as the bitmap the shader samples and the pixels the fallback reads. */
private class PreviewTexture(val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    val pixels: EquirectPixels by lazy { bitmap.toEquirect() }
}

/**
 * [style]'s preview texture, or null while it decodes. Each style is decoded
 * once per preview: switching back is instant, and all of it is let go on close.
 */
@Composable
private fun rememberPreviewTexture(style: GlobeStyle, terrain: Boolean, width: Int): PreviewTexture? {
    val assets = LocalContext.current.assets
    val loaded = remember { HashMap<GlobeStyle, PreviewTexture>() }
    val texture by produceState(loaded[style], style) {
        value = loaded[style] ?: withContext(Dispatchers.IO) {
            StyleTextures.load(assets, style, width, terrain)?.let(::PreviewTexture)
        }?.also { loaded[style] = it }
    }
    return texture
}

/**
 * The AGSL globe: [SphereProjection.latLonAt], per pixel, on the GPU. The
 * texture repeats east–west, so the antimeridian filters like anywhere else.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderGlobe(texture: Bitmap, camera: () -> GlobeCamera, modifier: Modifier) {
    val shader = remember(texture) {
        RuntimeShader(GLOBE_SHADER).apply {
            setInputShader(
                "tex",
                BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
            setFloatUniform("texSize", texture.width.toFloat(), texture.height.toFloat())
        }
    }
    val paint = remember { android.graphics.Paint() }
    Canvas(modifier) {
        val view = camera()
        val projection = SphereProjection(view)
        shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
        shader.setFloatUniform("radius", size.minDimension / 2f)
        shader.setFloatUniform("tanHalf", projection.tanHalfAngle.toFloat())
        shader.setFloatUniform("dist", view.distance)
        shader.setFloatUniform("eye", projection.eye.toFloats())
        shader.setFloatUniform("rightV", projection.right.toFloats())
        shader.setFloatUniform("upV", projection.up.toFloats())
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

/**
 * The globe below API 33: a disc of at most [MAX_FALLBACK_PX] pixels, its
 * lookup rebuilt off the main thread when the tilt changes, scaled up to size.
 */
@Composable
private fun TurntableGlobe(texture: PreviewTexture, camera: () -> GlobeCamera, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val sizePx = with(LocalDensity.current) { min(maxWidth.roundToPx(), MAX_FALLBACK_PX) }.coerceAtLeast(1)
        val pixels = texture.pixels
        var lookup by remember(sizePx, texture) { mutableStateOf<TurntableLookup?>(null) }
        LaunchedEffect(sizePx, texture) {
            snapshotFlow { (camera().latitude * 4.0).roundToInt() / 4.0 }
                .distinctUntilChanged()
                .collectLatest { latitude ->
                    lookup = withContext(Dispatchers.Default) {
                        TurntableLookup(latitude, PREVIEW_CAMERA.distance, sizePx, pixels.width, pixels.height)
                    }
                }
        }
        val frame = remember(sizePx) { createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888) }
        val buffer = remember(sizePx) { IntArray(sizePx * sizePx) }
        DisposableEffect(frame) { onDispose { frame.recycle() } }
        val image = remember(frame) { frame.asImageBitmap() }
        Canvas(Modifier.fillMaxSize()) {
            val current = lookup ?: return@Canvas
            current.frame(pixels, camera().longitude, buffer)
            frame.setPixels(buffer, 0, sizePx, 0, 0, sizePx, sizePx)
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sizePx, sizePx),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/**
 * Follows one finger until it lifts, feeding every move to [onMove] and the
 * velocity [tracker], and consuming them so nothing underneath scrolls.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackDrag(
    tracker: VelocityTracker,
    onMove: (PointerInputChange) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        if (!change.pressed) break
        tracker.addPosition(change.uptimeMillis, change.position)
        onMove(change)
        change.consume()
    }
}

/**
 * Swipe down to close: the globe or map follows the finger back toward its
 * thumbnail, and lets go past a third of the way or on a downward flick —
 * otherwise it springs back. A tap that never becomes a swipe closes too.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dismissGesture(
    tracker: VelocityTracker,
    down: PointerInputChange,
    viewportHeight: Float,
    touchSlop: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Boolean) -> Unit,
    from: Offset = down.position,
) {
    val distance = viewportHeight * DISMISS_DRAG_FRACTION
    var moved = touchSlop == 0f
    var lastY = from.y
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
            val velocity = tracker.calculateVelocity()
            val dragged = (lastY - down.position.y).coerceAtLeast(0f)
            onRelease(!moved || dragged > distance / 3f || velocity.y > DISMISS_FLICK_VELOCITY * density)
            return
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        lastY = change.position.y
        if (!moved && (change.position - down.position).getDistance() > touchSlop) moved = true
        if (moved) {
            onDrag(((lastY - down.position.y) / distance).coerceIn(0f, 0.85f))
            change.consume()
        }
    }
}

/**
 * Lets the window reach under the system bars with no dim and no window
 * animation of its own — the preview animates itself — and picks bar icons
 * that read over what it draws. `MedalOverlay`'s `EdgeToEdgeDialogWindow`, less
 * the blur.
 */
@Composable
private fun PreviewWindow(lightBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightBars) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setDimAmount(0f)
            window.setWindowAnimations(0)
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
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        onDispose { }
    }
}

private fun Rect.scaledAboutCenter(scale: Float): Rect = Rect(
    left = center.x - width * scale / 2f,
    top = center.y - height * scale / 2f,
    right = center.x + width * scale / 2f,
    bottom = center.y + height * scale / 2f,
)

private fun Rect.translate(offset: Offset): Rect = translate(offset.x, offset.y)

private fun DoubleArray.toFloats(): FloatArray = FloatArray(size) { this[it].toFloat() }

/** iOS clamps a frame's step the same way, so a stall never teleports the globe. */
private const val MAX_FRAME_STEP = 1f / 20f

/** The fallback disc's resolution cap: spinning it costs a pass over every pixel. */
private const val MAX_FALLBACK_PX = 640

/** How far down, as a share of the screen, a swipe takes the preview all the way back. */
private const val DISMISS_DRAG_FRACTION = 0.5f

/** dp per second: a downward flick faster than this closes, however short. */
private const val DISMISS_FLICK_VELOCITY = 1000f

/**
 * [SphereProjection.latLonAt] and [SphereProjection.coverage] in AGSL. `uv` is
 * in texels, which is what an input shader is evaluated in.
 */
private const val GLOBE_SHADER = """
uniform shader tex;
uniform float2 texSize;
uniform float2 center;
uniform float radius;
uniform float tanHalf;
uniform float dist;
uniform float3 eye;
uniform float3 rightV;
uniform float3 upV;

half4 main(float2 fragCoord) {
    float2 q = (fragCoord - center) / radius;
    float coverage = clamp((1.0 - length(q)) * radius + 0.5, 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }
    float3 d = -eye + rightV * (q.x * tanHalf) + upV * (-q.y * tanHalf);
    float3 e = eye * dist;
    float a = dot(d, d);
    float b = 2.0 * dot(e, d);
    float c = dist * dist - 1.0;
    float t = (-b - sqrt(max(b * b - 4.0 * a * c, 0.0))) / (2.0 * a);
    float3 p = normalize(e + t * d);
    float lat = asin(clamp(p.y, -1.0, 1.0));
    float lon = -atan(p.z, p.x);
    float2 uv = float2((lon * 0.15915494 + 0.5) * texSize.x, (0.5 - lat * 0.31830989) * texSize.y);
    return tex.eval(uv) * half(coverage);
}
"""

/** Test tags for the preview, its close button, its Use button and its style switcher. */
internal const val STYLE_PREVIEW_TAG = "style_preview"
internal const val STYLE_PREVIEW_CLOSE_TAG = "style_preview_close"
internal const val STYLE_PREVIEW_USE_TAG = "style_preview_use"
internal fun previewStyleTag(style: GlobeStyle) = "style_preview:${style.name}"
