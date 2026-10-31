package com.anmol.voyage.ui.country

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.voyage.ui.theme.LocalVoyageFonts
import com.anmol.voyage.ui.theme.VoyagePalette
import java.io.IOException

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlagText(flag = flag, country = countryName, fontSize = 40.sp)
            Text(
                text = countryName,
                fontFamily = LocalVoyageFonts.current.display,
                fontWeight = FontWeight.Medium,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = VoyagePalette.textPrimary(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
