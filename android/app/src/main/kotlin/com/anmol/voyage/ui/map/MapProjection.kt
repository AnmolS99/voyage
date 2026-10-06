package com.anmol.voyage.ui.map

import com.anmol.voyage.data.LatLon

/**
 * Equirectangular projection for the flat map, matching iOS `MapView` exactly.
 *
 * Three coordinate spaces are in play, and mixing them up is the classic way to
 * get a map that draws fine but hit-tests wrong:
 *
 *  - **map space** — origin at the map's own top-left, width [mapWidth], height
 *    [mapHeight] (always half the width, the 2:1 equirectangular ratio). Country
 *    paths are built once in this space.
 *  - **view space** — map space shifted by [horizontalOffset] and [verticalOffset]
 *    so the map is centred in the view, then panned and zoomed.
 *  - **lon/lat** — what the data and the hit tester speak.
 *
 * Both the renderer and the tap handler go through this one type so they can
 * never disagree about where a country is.
 */
class MapProjection(val viewWidth: Float, val viewHeight: Float) {

    /**
     * At scale 1 the map *fills* the view, keeping its 2:1 ratio: it spans the full
     * height in portrait (overhanging left and right) and the full width in
     * landscape (overhanging top and bottom). Since zoom never goes below 1, no
     * empty space ever shows beside or above the map — the same rule as iOS.
     */
    val mapHeight: Float = maxOf(viewHeight, viewWidth / 2f)
    val mapWidth: Float = mapHeight * 2f

    /** Centres the map in the view; zero or negative, as the map overhangs. */
    val horizontalOffset: Float = (viewWidth - mapWidth) / 2f
    val verticalOffset: Float = (viewHeight - mapHeight) / 2f

    fun mapX(lon: Double): Float = ((lon + 180.0) / 360.0).toFloat() * mapWidth

    fun mapY(lat: Double): Float = ((90.0 - lat) / 180.0).toFloat() * mapHeight

    /** Map-space point moved into view space (before pan/zoom). */
    fun viewX(lon: Double): Float = mapX(lon) + horizontalOffset

    fun viewY(lat: Double): Float = mapY(lat) + verticalOffset

    /**
     * Applies the pan/zoom transform to a view-space point: scale about the view
     * center, then translate. The same transform the canvas uses, so screen-space
     * markers (microstate dots, capital stars) land on their country.
     */
    fun transform(x: Float, y: Float, scale: Float, offsetX: Float, offsetY: Float): Pair<Float, Float> {
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        return Pair(
            (x - centerX) * scale + centerX + offsetX,
            (y - centerY) * scale + centerY + offsetY,
        )
    }

    /** The lon/lat under a touch, undoing pan/zoom and the centring. */
    fun lonLatAt(touchX: Float, touchY: Float, scale: Float, offsetX: Float, offsetY: Float): LatLon {
        val centerX = viewWidth / 2f + offsetX
        val centerY = viewHeight / 2f + offsetY
        val mapPointX = (touchX - centerX) / scale + viewWidth / 2f
        val mapPointY = (touchY - centerY) / scale + viewHeight / 2f
        return LatLon(
            lat = 90.0 - (mapPointY - verticalOffset).toDouble() / mapHeight * 180.0,
            lon = (mapPointX - horizontalOffset).toDouble() / mapWidth * 360.0 - 180.0,
        )
    }

    /**
     * Keeps the map from being dragged away from the view: pan is limited to
     * however far the scaled map overhangs each edge — which the fill above
     * guarantees is never negative (the `maxOf` only absorbs float rounding), so
     * no edge of the map can come into view.
     */
    fun clampOffset(offsetX: Float, offsetY: Float, scale: Float): Pair<Float, Float> {
        val maxX = maxOf(0f, (mapWidth * scale - viewWidth) / 2f)
        val maxY = maxOf(0f, (mapHeight * scale - viewHeight) / 2f)
        return Pair(offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
    }

    companion object {
        /** Zoom limits, as on iOS. */
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 10f
    }
}
