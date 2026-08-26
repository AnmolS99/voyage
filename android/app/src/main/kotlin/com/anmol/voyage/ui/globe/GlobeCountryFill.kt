package com.anmol.voyage.ui.globe

import androidx.compose.ui.graphics.Color
import com.anmol.voyage.ui.map.CountryStyles
import com.anmol.voyage.ui.map.MapShading
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * One painted surface on the globe: a country's fill, or an outline.
 *
 * [colorB] and [gradient] carry the visited+wishlist diagonal; for every other
 * state the surface is flat [colorA] and `colorB` is ignored. Both the country
 * material and the outline material take exactly this trio, which is why one
 * type covers fills and borders alike.
 */
internal data class GlobeFill(
    val colorA: Color,
    val colorB: Color,
    val gradient: Boolean,
)

/**
 * How a country is painted on the globe.
 *
 * The rules themselves live in [CountryStyles], shared with the flat map, which
 * is what CLAUDE.md's globe/map consistency rule asks for: status outranks
 * selection, so a selected country's *fill* drops to plain land green and its
 * visited/wishlist status moves to a thickened border. This file only translates
 * a [MapShading] into the uniforms the Filament materials take.
 *
 * (Until Phase 7.5 the globe had no borders and had to state a second set of
 * rules here — selection shown with the palette's brighter `…Selected` variants.
 * Now that the outlines exist, those variants are unused on Android, exactly as
 * they are on iOS.)
 */
internal object GlobeCountryFills {

    /** The country's fill. */
    fun of(isVisited: Boolean, isWishlist: Boolean, isSelected: Boolean): GlobeFill =
        CountryStyles.of(isVisited, isWishlist, isSelected).fill.toGlobeFill()

    /**
     * The selected country's overlay border. Always asked for a selected
     * country, because that is the only one drawn with an overlay — the rest
     * share the black sector outlines.
     */
    fun selectedBorderOf(isVisited: Boolean, isWishlist: Boolean): GlobeFill =
        CountryStyles.of(isVisited, isWishlist, isSelected = true).border.toGlobeFill()

    private fun MapShading.toGlobeFill(): GlobeFill = when (this) {
        is MapShading.Solid -> GlobeFill(colorA = color, colorB = color, gradient = false)
        // Yellow at the bottom-left of the country's box, purple at the top
        // right — the direction the outline's gradient parameter runs in, and
        // the one the map's linear brush runs in.
        MapShading.VisitedWishlist -> GlobeFill(
            colorA = VoyagePalette.visited,
            colorB = VoyagePalette.wishlist,
            gradient = true,
        )
    }
}

/**
 * Hands a palette color to Filament.
 *
 * The components are passed through **unconverted**, and that is correct only
 * because [GlobeRenderer] disables post-processing. Filament works in linear
 * space and normally encodes to sRGB in the post-processing pass; with that pass
 * off, whatever a material writes lands in the framebuffer verbatim, so passing
 * the palette's sRGB components puts the exact palette color on screen.
 *
 * Converting sRGB → linear here — the instinctive thing to do — is what renders
 * #2F86A6 ocean as #073D61, which is how this was first written and caught.
 *
 * **If post-processing is ever enabled, this must convert to linear**, or every
 * country color will shift. The two settings only make sense together.
 */
internal fun Color.toFilamentColor(): FloatArray = floatArrayOf(red, green, blue, alpha)
