package com.anmol.voyage.globe

import com.anmol.voyage.data.GeoJsonCountry

/**
 * Outline meshes for one country at a time — the geometry behind the selected
 * country's overlay border.
 *
 * Separate from [GlobeGeometryCache] because the two answer different
 * questions. That one builds the whole world once and keeps it; this one builds
 * whatever the user just tapped, which is a handful of rings, and remembers it
 * in case they tap back. iOS keeps the same per-country cache in
 * `GlobeView.Coordinator.selectedOutlineGeometries`.
 *
 * The meshes sit at [PolygonTriangulator.SELECTED_OUTLINE_RADIUS], just above
 * the shared black borders, so the overlay wins the depth test against the
 * neighbours it is meant to sit on top of.
 */
object SelectedOutlineCache {

    private val cached = HashMap<String, OutlineMesh>()
    private val lock = Any()

    /**
     * The outline for [name], or null when it has no rings to outline — a
     * Point-feature microstate, which is a dot rather than a shape.
     *
     * Called off the main thread: a large country is tens of thousands of
     * points and would otherwise drop a frame on selection.
     */
    fun of(countries: List<GeoJsonCountry>, name: String): OutlineMesh? {
        synchronized(lock) { cached[name] }?.let { return it }

        val country = countries.firstOrNull { it.name == name } ?: return null
        if (country.isPointCountry) return null
        val mesh = PolygonTriangulator.createBorderOutlineGeometry(
            polygons = country.polygons,
            radius = PolygonTriangulator.SELECTED_OUTLINE_RADIUS,
        ) ?: return null

        synchronized(lock) { cached[name] = mesh }
        return mesh
    }
}
