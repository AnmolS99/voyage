package com.anmol.voyage.ui.globe

import androidx.compose.ui.graphics.Color
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The fill colors for one country on the globe.
 *
 * [colorB] and [gradient] carry the visited+wishlist diagonal; for every other
 * state the fill is flat [colorA] and `colorB` is ignored.
 */
internal data class GlobeFill(
    val colorA: Color,
    val colorB: Color,
    val gradient: Boolean,
)

/**
 * How a country is painted on the globe.
 *
 * **This deliberately differs from `CountryStyles` for one state, and only
 * until Phase 7.5 lands.** On both iOS renderers a *selected* country keeps a
 * plain land-green fill and shows its visited/wishlist status on a thickened,
 * status-colored border. This globe has no borders yet — 7.5 (outline meshes)
 * and 7.7 (the selected overlay) are separate sub-steps — so applying that rule
 * verbatim would make selection invisible and tap-to-select untestable.
 *
 * Until the outlines land, selection is shown with the palette's brighter
 * `…Selected` variants instead. Those values already exist in `VoyagePalette`
 * (ported from `AppColors` in Phase 2) and are otherwise unused, and this is the
 * same trade the map made in Phase 4 with its interim selection card. When 7.5
 * lands, this object should collapse into `CountryStyles` rather than continuing
 * to state a second set of rules.
 */
internal object GlobeCountryFills {

    fun of(isVisited: Boolean, isWishlist: Boolean, isSelected: Boolean): GlobeFill = when {
        isVisited && isWishlist -> GlobeFill(
            colorA = if (isSelected) VoyagePalette.visitedSelected else VoyagePalette.visited,
            colorB = if (isSelected) VoyagePalette.wishlistSelected else VoyagePalette.wishlist,
            gradient = true,
        )

        isVisited -> flat(if (isSelected) VoyagePalette.visitedSelected else VoyagePalette.visited)
        isWishlist -> flat(if (isSelected) VoyagePalette.wishlistSelected else VoyagePalette.wishlist)
        else -> flat(if (isSelected) VoyagePalette.landSelected else VoyagePalette.land)
    }

    private fun flat(color: Color) = GlobeFill(colorA = color, colorB = color, gradient = false)
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
