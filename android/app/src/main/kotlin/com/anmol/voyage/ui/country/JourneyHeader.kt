package com.anmol.voyage.ui.country

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R
import com.anmol.voyage.ui.theme.DisplayCapitalsCentre
import com.anmol.voyage.ui.theme.LocalVoyageFonts
import com.anmol.voyage.ui.theme.VoyagePalette
import com.anmol.voyage.ui.theme.displayCapitalsCentre
import java.io.IOException
import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Painted header art for the country sheet, one day (light theme) and one night
 * (dark theme) image per country, made by `scripts/journey_art.py` and packaged
 * from `shared/data/journey/` as `<ISO>_day.webp` / `<ISO>_night.webp` — the
 * same files iOS bundles for `CountryExploreView`. Countries without art yet
 * keep the plain header.
 */
internal object JourneyArt {

    /** The banner's shape: the Journey design's 390 x 240 header. */
    const val ASPECT_RATIO = 390f / 240f

    fun assetPath(isoCode: String, isDark: Boolean): String =
        "journey/${isoCode}_${if (isDark) "night" else "day"}.webp"

    /** The art for [isoCode], or null when that country has none yet. */
    fun load(assets: AssetManager, isoCode: String, isDark: Boolean): ImageBitmap? = try {
        assets.open(assetPath(isoCode, isDark)).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: IOException) {
        null
    }

    /** What each painting shows, keyed by ISO code — written by `journey_art.py export`. */
    const val PLACES = "journey/places.json"

    private val placesSerializer = MapSerializer(String.serializer(), String.serializer())

    @OptIn(ExperimentalSerializationApi::class)
    fun parsePlaces(input: InputStream): Map<String, String> =
        input.use { Json.decodeFromStream(placesSerializer, it) }

    /** Parsed on first use and kept, as iOS's `JourneyArt.places` is. */
    @Volatile private var places: Map<String, String>? = null

    fun place(assets: AssetManager, isoCode: String): String? {
        val loaded = places ?: try {
            parsePlaces(assets.open(PLACES))
        } catch (_: IOException) {
            emptyMap()
        }.also { places = it }
        return loaded[isoCode]
    }
}

/** The name of the place [isoCode]'s art shows, or null when it has none. */
@Composable
internal fun rememberJourneyPlace(isoCode: String?): String? {
    val assets = LocalContext.current.assets
    return remember(assets, isoCode) { isoCode?.let { JourneyArt.place(assets, it) } }
}

/**
 * The country's art, or null when it has none. Decoded during composition, not
 * after: whether there is art decides the sheet's layout (and its drag handle),
 * so loading it late would make the sheet jump. A 1170 x 720 WebP decodes in a
 * few milliseconds.
 */
@Composable
internal fun rememberJourneyArt(isoCode: String?, isDark: Boolean): ImageBitmap? {
    val assets = LocalContext.current.assets
    return remember(assets, isoCode, isDark) {
        isoCode?.let { JourneyArt.load(assets, it, isDark) }
    }
}

/**
 * The Journey design's header, as on iOS: the art full-bleed with the flag and
 * name over its top-left, which the art keeps as calm sky, fading at the bottom
 * into the sheet instead of ending on a hard edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JourneyHeader(
    art: ImageBitmap,
    flag: String,
    countryName: String,
    place: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(JourneyArt.ASPECT_RATIO),
    ) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(JourneyArt.ASPECT_RATIO),
        )

        val sheet = BottomSheetDefaults.ContainerColor
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(
                        0f to sheet.copy(alpha = 0f),
                        0.55f to sheet.copy(alpha = 0.55f),
                        1f to sheet,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, end = 20.dp, top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlagText(
                flag = flag,
                country = countryName,
                fontSize = TITLE_SIZE,
                modifier = Modifier.alignBy { it.measuredHeight / 2 },
            )
            Text(
                text = countryName,
                fontFamily = LocalVoyageFonts.current.display,
                fontWeight = FontWeight.Medium,
                fontSize = TITLE_SIZE,
                lineHeight = 44.sp,
                color = VoyagePalette.textPrimary(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .alignBy(DisplayCapitalsCentre)
                    .displayCapitalsCentre(TITLE_SIZE),
            )
        }

        if (place != null) {
            JourneyPlaceButton(
                place = place,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Above the fade.
                    .padding(end = 12.dp, bottom = 24.dp),
            )
        }
    }
}

private val TITLE_SIZE = 40.sp

/**
 * A small pin over the Journey art that opens into the name of the place the
 * painting shows, and closes again on a second tap — iOS `JourneyPlaceButton`.
 * Kept faint so the art stays the subject.
 */
@Composable
private fun JourneyPlaceButton(place: String, modifier: Modifier = Modifier) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.journey_place)
    // One spring for the padding and the text, so the pill grows and shrinks in
    // a single motion — iOS animates all of it with one withAnimation. Fading the
    // text out before shrinking left the pill wide and empty for a moment.
    val padding by animateDpAsState(if (isOpen) 10.dp else 0.dp, placeSpring(), label = "place padding")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(VoyagePalette.journeyChipBackground)
            .clickable(role = Role.Button, onClickLabel = label) { isOpen = !isOpen }
            .semantics { stateDescription = place }
            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Place,
            contentDescription = label,
            tint = VoyagePalette.journeyChipContent,
            modifier = Modifier.size(13.dp),
        )
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(placeSpring()) + expandHorizontally(placeSpring(), expandFrom = Alignment.Start),
            exit = fadeOut(placeSpring()) + shrinkHorizontally(placeSpring(), shrinkTowards = Alignment.Start),
        ) {
            Text(
                text = place,
                fontFamily = LocalVoyageFonts.current.text,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = VoyagePalette.journeyChipContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The gap to the pin, inside the text so it shrinks away with it.
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}

/** Close to iOS `JourneyPlaceButton`'s spring(response: 0.35, dampingFraction: 0.85). */
private fun <T> placeSpring() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
