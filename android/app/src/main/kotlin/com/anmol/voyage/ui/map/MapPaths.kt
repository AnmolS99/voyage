package com.anmol.voyage.ui.map

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.data.Ring

/**
 * One country's prebuilt geometry, in map space at scale 1.
 *
 * Paths are projected once per view size rather than per frame: re-projecting
 * ~171k boundary points on every pan would drop frames, so panning and zooming
 * only push a matrix.
 */
class CountryPaths(
    val name: String,
    /**
     * Outer rings *and* hole rings in one even-odd path. The holes toggle the
     * winding count, which leaves enclaves (Lesotho inside South Africa)
     * transparent so the enclosing country's neighbour shows through.
     */
    val fill: Path,
    /**
     * Outer rings only. Borders are stroked from these because an enclave's
     * border belongs to the enclave, not to the country surrounding it.
     */
    val outlines: List<Path>,
    /** Map-space extent, used to place the visited+wishlist gradient. */
    val bounds: Rect,
)

/**
 * Projects every polygon country into map-space paths. Point countries are drawn
 * as fixed-size dots straight from their coordinate and so are skipped here.
 */
fun buildCountryPaths(
    countries: List<GeoJsonCountry>,
    projection: MapProjection,
): List<CountryPaths> {
    fun ringPath(ring: Ring, into: Path) {
        if (ring.size == 0) return
        for (i in 0 until ring.size) {
            val x = projection.mapX(ring.lon(i))
            val y = projection.mapY(ring.lat(i))
            if (i == 0) into.moveTo(x, y) else into.lineTo(x, y)
        }
        into.close()
    }

    return countries.filterNot { it.isPointCountry }.map { country ->
        val fill = Path().apply { fillType = PathFillType.EvenOdd }
        val outlines = country.polygons.map { polygon ->
            val outline = Path()
            ringPath(polygon, outline)
            // The same ring feeds the fill; appending the built path avoids
            // projecting its points twice.
            fill.addPath(outline)
            outline
        }
        for (hole in country.holes) ringPath(hole, fill)

        CountryPaths(
            name = country.name,
            fill = fill,
            outlines = outlines,
            bounds = fill.getBounds(),
        )
    }
}
