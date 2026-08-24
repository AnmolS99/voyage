package com.anmol.voyage.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.globe.PolygonTriangulator
import com.anmol.voyage.globe.SphereMesh
import com.anmol.voyage.globe.UvSphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the globe draws, as buffers ready to hand to Filament. */
internal class GlobeGeometry(
    val ocean: SphereMesh,
    val countries: List<NamedCountryMesh>,
)

/**
 * Triangulates the world for the globe, off the main thread.
 *
 * This is the expensive step — 181 countries, ~171k coordinates, ear-clipped and
 * subdivided for curvature — so it runs on [Dispatchers.Default] and the caller
 * shows a spinner until it lands. It is keyed on the country list, which the
 * data cache hands out as a single immutable value, so it runs once per process
 * rather than once per recomposition.
 *
 * Point-feature microstates produce no fill mesh (they are dots, drawn by the
 * map at a fixed screen radius) and are skipped here; the globe will get their
 * markers with the capital stars in 7.5.
 */
@Composable
internal fun rememberGlobeGeometry(countries: List<GeoJsonCountry>?): GlobeGeometry? {
    val geometry by produceState<GlobeGeometry?>(initialValue = null, countries) {
        val source = countries ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val meshes = source.mapNotNull { country ->
                PolygonTriangulator.createCountryGeometry(country.polygons, country.holes)
                    ?.let { NamedCountryMesh(country.name, it) }
            }
            GlobeGeometry(ocean = UvSphere.build(), countries = meshes)
        }
    }
    return geometry
}
