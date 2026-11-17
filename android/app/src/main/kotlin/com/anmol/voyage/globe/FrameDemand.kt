package com.anmol.voyage.globe

/**
 * Decides when the globe draws: only while something on it is changing.
 *
 * A render loop that re-posts itself unconditionally draws the same picture at
 * the display's refresh rate for as long as the globe is on screen — under the
 * country sheet, after every drag — and keeps the GPU awake for a still image.
 * iOS gets the opposite for free, because SceneKit redraws only what changed
 * unless `rendersContinuously` is set. This is that rule for a
 * `Choreographer` loop: a frame is posted when something [request]s one or the
 * last frame said it was still animating, and otherwise the loop stops.
 *
 * It holds no reference to the `Choreographer`, only [post] and [cancel], so
 * the rule can be pinned by a JVM test.
 *
 * @param post asks for one frame callback. Never called while one is pending.
 * @param cancel withdraws the pending frame callback.
 */
internal class FrameDemand(
    private val post: () -> Unit,
    private val cancel: () -> Unit,
) {
    /** A frame callback is pending. */
    private var posted = false

    /** Something changed since the last frame was drawn. */
    private var requested = false

    /** The last frame asked for the one after it, so the two are a vsync apart. */
    private var continuing = false

    /**
     * Whether frames may be drawn at all — false for a globe that is hidden or
     * in a stopped app. Enabling draws one frame, since anything may have
     * changed while nobody was looking.
     */
    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                request()
            } else {
                if (posted) cancel()
                posted = false
                continuing = false
            }
        }

    /**
     * What is on screen is out of date: draw a frame. Safe to call any number
     * of times between frames, and while disabled — the request is kept and
     * drawn when frames are enabled again.
     */
    fun request() {
        requested = true
        schedule()
    }

    /**
     * Runs one frame. Call from the frame callback.
     *
     * [draw] is told whether this frame follows a pause — the loop was stopped,
     * so the time since the previous frame is not a step to animate by — and
     * returns whether something is still moving and wants the next frame too.
     */
    fun frame(draw: (afterPause: Boolean) -> Boolean) {
        posted = false
        if (!enabled) return
        val afterPause = !continuing
        requested = false
        val animating = draw(afterPause)
        continuing = animating || requested
        if (continuing) schedule()
    }

    private fun schedule() {
        if (!enabled || posted) return
        posted = true
        post()
    }
}
