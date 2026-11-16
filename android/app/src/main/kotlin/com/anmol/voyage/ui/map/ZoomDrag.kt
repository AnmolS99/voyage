package com.anmol.voyage.ui.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tap, then press and drag vertically: a one-finger zoom, ported from iOS's
 * `handleDoubleTapDrag` (globe) and `MapView`'s zoom drag (map). Both Android
 * renderers use this one detector, so the gesture starts and ends the same way
 * on the globe and the map; what a drag *does* is each renderer's —
 * [com.anmol.voyage.globe.GlobeCamera.zoomDraggedBy] and
 * [MapProjection.zoomDragScale].
 *
 * Dragging **down** zooms in, the direction Google Maps uses on Android and the
 * opposite of iOS — the gesture is a port, its direction deliberately is not.
 *
 * Install it as the innermost pointer handler (last in the modifier chain), so
 * it sees each event on the main pass before the transform and tap detectors
 * do. Consuming is what makes them stand down: `detectTransformGestures`
 * abandons a gesture the moment a change is consumed and `detectTapGestures`
 * reports no tap, so a gesture meant only to zoom neither pans nor turns.
 *
 * Nothing happens until the second touch actually moves, so a plain double tap
 * stays a plain double tap.
 *
 * @param onStart the drag began, at [Offset] — where the second touch landed.
 * @param onDrag the finger's vertical travel since [onStart], in dp, positive
 *   downwards.
 */
internal suspend fun PointerInputScope.detectZoomDrags(
    onStart: (anchor: Offset) -> Unit,
    onDrag: (travelDp: Float) -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        // A first touch that is a tap: back up quickly, and from where it
        // landed. A touch that is held or travels is a pan or a rotation, and
        // belongs to the detectors above — iOS spends `minimumPressDuration`
        // and `allowableMovement` on the same two questions.
        val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        } ?: return@awaitEachGesture
        if ((firstUp.position - firstDown.position).getDistance() > viewConfiguration.touchSlop) {
            return@awaitEachGesture
        }

        // ...and a second touch soon enough after it to be the same gesture.
        val press = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false)
        } ?: return@awaitEachGesture

        var travelDp = 0f
        var zooming = false
        while (true) {
            val event = awaitPointerEvent()
            val finger = event.changes.firstOrNull { it.id == press.id } ?: break
            travelDp += (finger.position.y - finger.previousPosition.y) / density
            if (!zooming && travelDp != 0f) {
                onStart(press.position)
                zooming = true
            }
            if (zooming) {
                onDrag(travelDp)
                // Every change, not only the zooming finger: a second finger
                // landing mid-drag would otherwise reach the transform detector
                // as the beginning of a pinch.
                event.changes.forEach { it.consume() }
            }
            if (!finger.pressed) break
        }
    }
}
