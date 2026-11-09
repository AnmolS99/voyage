package com.anmol.voyage.ui.settings

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.anmol.voyage.R
import com.anmol.voyage.globe.GlobeCamera
import com.anmol.voyage.state.GlobeStyle
import com.anmol.voyage.ui.theme.LocalVoyageFonts
import com.anmol.voyage.ui.theme.VoyagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a texture picker chooses for: Home's globe or its flat map. */
internal enum class StyleSurface { Globe, Map }

/**
 * Where every thumbnail and the preview look from: Home's camera at its
 * default distance, turned to Europe and Africa, as iOS's globe starts.
 * Shared so the preview opens on exactly the picture the thumbnail shows.
 */
internal val PREVIEW_CAMERA = GlobeCamera(latitude = 20.0, longitude = 15.0)

/** A globe thumbnail's diameter. */
private val GLOBE_THUMBNAIL = 64.dp

/** The gap between a thumbnail and its selection ring. */
private val RING_GAP = 4.dp

/** The selection ring's width. */
private val RING_WIDTH = 2.dp

/** A map thumbnail's corner radius. */
internal val MAP_THUMBNAIL_CORNER = 8.dp

/**
 * One texture preference as a row of small globes — or small maps — each
 * painted with its texture: tap one to choose it, and "Preview" opens the chosen
 * one full screen, at the size Home draws it (see [StylePreview]).
 *
 * The thumbnail that is open in the preview is hidden here, so the preview's
 * globe seems to lift out of its slot and settle back into it — into whichever
 * slot it ends up previewing. iOS `StylePicker`.
 */
@Composable
internal fun StylePicker(
    surface: StyleSurface,
    icon: ImageVector,
    @StringRes label: Int,
    selected: GlobeStyle,
    onSelect: (GlobeStyle) -> Unit,
    terrain: Boolean,
    isDark: Boolean,
    tag: String,
) {
    val origins = remember { mutableStateMapOf<GlobeStyle, Rect>() }
    var previewing by remember { mutableStateOf<GlobeStyle?>(null) }
    var previewOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tinted with the app's orange, as iOS tints its settings icons.
            Icon(icon, contentDescription = null, tint = VoyagePalette.buttonColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    previewing = selected
                    previewOpen = true
                },
                modifier = Modifier.testTag(previewButtonTag(tag)),
            ) {
                Text(
                    text = stringResource(R.string.style_preview),
                    color = VoyagePalette.buttonColor,
                    fontFamily = LocalVoyageFonts.current.text,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = null,
                    tint = VoyagePalette.buttonColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlobeStyle.entries.forEach { style ->
                StyleOption(
                    surface = surface,
                    style = style,
                    isSelected = style == selected,
                    terrain = terrain,
                    hidden = previewOpen && previewing == style,
                    onClick = { onSelect(style) },
                    onPositioned = { origins[style] = it.boundsOnScreen() },
                    modifier = Modifier.weight(1f).testTag(styleOptionTag(tag, style)),
                )
            }
        }
    }

    val opened = previewing
    if (previewOpen && opened != null) {
        StylePreview(
            surface = surface,
            initialStyle = opened,
            currentStyle = selected,
            terrain = terrain,
            isDark = isDark,
            originOf = { origins[it] },
            onPreviewing = { previewing = it },
            onUse = onSelect,
            onDismissed = {
                previewOpen = false
                previewing = null
            },
        )
    }
}

/** One choice: its thumbnail inside a ring that shows when it is chosen, and its name. */
@Composable
private fun StyleOption(
    surface: StyleSurface,
    style: GlobeStyle,
    isSelected: Boolean,
    terrain: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) VoyagePalette.buttonColor else Color.Transparent,
        animationSpec = tween(220),
        label = "ring",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "label",
    )
    val ringShape = when (surface) {
        StyleSurface.Globe -> CircleShape
        StyleSurface.Map -> RoundedCornerShape(MAP_THUMBNAIL_CORNER + RING_GAP)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        val ringed = Modifier
            .border(RING_WIDTH, ringColor, ringShape)
            .padding(RING_GAP)
        // Kept in layout while previewed, so its slot does not collapse.
        val thumbnail = Modifier.onGloballyPositioned(onPositioned).alpha(if (hidden) 0f else 1f)
        when (surface) {
            StyleSurface.Globe -> Box(ringed) {
                GlobeThumbnail(style, terrain, thumbnail.size(GLOBE_THUMBNAIL))
            }
            StyleSurface.Map -> Box(ringed.fillMaxWidth()) {
                MapThumbnail(style, terrain, thumbnail.fillMaxWidth().aspectRatio(2f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(style.labelRes),
            color = labelColor,
            fontFamily = LocalVoyageFonts.current.text,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** The style's globe from [PREVIEW_CAMERA], fading in once it has been drawn. */
@Composable
internal fun GlobeThumbnail(style: GlobeStyle, terrain: Boolean, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { GLOBE_THUMBNAIL.roundToPx() }
    val image = rememberGlobeThumbnail(style, terrain, sizePx)
    FadingThumbnail(image, CircleShape, ContentScale.Fit, modifier)
}

/** The style's whole texture, as the flat map shows it. */
@Composable
private fun MapThumbnail(style: GlobeStyle, terrain: Boolean, modifier: Modifier = Modifier) {
    val image = rememberMapThumbnail(style, terrain)
    FadingThumbnail(image, RoundedCornerShape(MAP_THUMBNAIL_CORNER), ContentScale.FillBounds, modifier)
}

/**
 * A thumbnail that fades in over a quiet placeholder of its own shape, so the
 * row never jumps or flashes while the textures decode.
 */
@Composable
private fun FadingThumbnail(
    image: ImageBitmap?,
    shape: androidx.compose.ui.graphics.Shape,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(260), label = "thumbnail")
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().alpha(shown),
            )
        }
    }
}

/** [GlobeThumbnails.render], off the main thread the first time. */
@Composable
internal fun rememberGlobeThumbnail(style: GlobeStyle, terrain: Boolean, sizePx: Int): ImageBitmap? {
    val assets = LocalContext.current.assets
    val image by produceState(GlobeThumbnails.cached(style, terrain, sizePx), style, terrain, sizePx) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { GlobeThumbnails.render(assets, style, terrain, sizePx) }
        }
    }
    return image
}

/** The thumbnail texture itself, off the main thread the first time. */
@Composable
internal fun rememberMapThumbnail(style: GlobeStyle, terrain: Boolean): ImageBitmap? {
    val assets = LocalContext.current.assets
    val image by produceState(
        StyleTextures.cachedThumbnail(style, terrain)?.asImageBitmap(),
        style,
        terrain,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { StyleTextures.thumbnail(assets, style, terrain) }?.asImageBitmap()
        }
    }
    return image
}

/**
 * Rendered globe thumbnails, kept for the process: three styles, with and
 * without terrain, at most a few hundred kilobytes in all.
 */
internal object GlobeThumbnails {
    private data class Key(val style: GlobeStyle, val terrain: Boolean, val sizePx: Int)

    private val rendered = HashMap<Key, ImageBitmap>()

    fun cached(style: GlobeStyle, terrain: Boolean, sizePx: Int): ImageBitmap? =
        synchronized(rendered) { rendered[Key(style, terrain, sizePx)] }

    /**
     * Ray-casts the globe at twice [sizePx] and filters it down, which smooths
     * the limb and the texture alike. Off the main thread.
     */
    fun render(assets: android.content.res.AssetManager, style: GlobeStyle, terrain: Boolean, sizePx: Int): ImageBitmap? {
        cached(style, terrain, sizePx)?.let { return it }
        val texture = StyleTextures.thumbnail(assets, style, terrain) ?: return null
        val supersampled = sizePx * 2
        val pixels = SphereProjection(PREVIEW_CAMERA).render(texture.toEquirect(), supersampled)
        val large = createBitmap(supersampled, supersampled, Bitmap.Config.ARGB_8888)
        large.setPixels(pixels, 0, supersampled, 0, 0, supersampled, supersampled)
        val image = large.scale(sizePx, sizePx, filter = true).asImageBitmap()
        large.recycle()
        synchronized(rendered) { rendered[Key(style, terrain, sizePx)] = image }
        return image
    }
}

/** This layout's bounds on the screen, which a dialog's window shares. */
internal fun LayoutCoordinates.boundsOnScreen(): Rect {
    val origin = positionOnScreen()
    return Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
}

/** Test tag for a picker's Preview button. */
internal fun previewButtonTag(pickerTag: String) = "$pickerTag:preview"
