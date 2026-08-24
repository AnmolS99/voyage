package com.anmol.voyage.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.anmol.voyage.data.GeoJsonCountry
import com.anmol.voyage.globe.GlobeGeometry
import com.anmol.voyage.globe.GlobeGeometryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The globe's geometry, for a composable that wants to draw it.
 *
 * The work itself belongs to [GlobeGeometryCache], which outlives composition —
 * so navigating away from Home and back returns the same meshes instead of
 * re-triangulating them. This only decides whether the caller has to wait: if
 * the cache is already warm (the usual case, since it is prewarmed at app
 * start) the geometry is returned on the first composition and no spinner is
 * ever shown.
 */
@Composable
internal fun rememberGlobeGeometry(countries: List<GeoJsonCountry>?): GlobeGeometry? {
    val geometry by produceState<GlobeGeometry?>(
        // Warm cache: return it immediately, so returning to the globe does not
        // flash a spinner for one frame on the way back.
        initialValue = if (GlobeGeometryCache.isReady && countries != null) {
            GlobeGeometryCache.get(countries)
        } else {
            null
        },
        countries,
    ) {
        if (value != null) return@produceState
        val source = countries ?: return@produceState
        value = withContext(Dispatchers.Default) { GlobeGeometryCache.get(source) }
    }
    return geometry
}
