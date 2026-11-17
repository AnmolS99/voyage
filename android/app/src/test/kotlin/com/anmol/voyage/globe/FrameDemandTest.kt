package com.anmol.voyage.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The globe draws on demand: a still globe posts no frames (issue #160).
 *
 * [Loop] stands in for the `Choreographer`: it counts the callbacks asked for
 * and runs one when told to, as a vsync would.
 */
class FrameDemandTest {

    private class Loop {
        var pending = 0
        var posts = 0
        val demand = FrameDemand(
            post = { pending++; posts++ },
            cancel = { pending-- },
        )

        /** One vsync: runs the pending callback, if any. Returns whether one ran. */
        fun vsync(animating: Boolean = false, draw: (Boolean) -> Unit = {}): Boolean {
            if (pending == 0) return false
            pending--
            demand.frame { afterPause ->
                draw(afterPause)
                animating
            }
            return true
        }
    }

    @Test
    fun `enabling draws one frame and then the loop stops`() {
        val loop = Loop()
        loop.demand.enabled = true

        assertTrue(loop.vsync())
        assertFalse("an idle globe re-posted its frame callback", loop.vsync())
        assertEquals(1, loop.posts)
    }

    @Test
    fun `a request restarts a stopped loop for one frame`() {
        val loop = Loop()
        loop.demand.enabled = true
        loop.vsync()

        loop.demand.request()

        assertTrue(loop.vsync())
        assertFalse(loop.vsync())
    }

    @Test
    fun `requests between frames post a single callback`() {
        val loop = Loop()
        loop.demand.enabled = true
        loop.demand.request()
        loop.demand.request()

        assertEquals(1, loop.pending)
        assertEquals(1, loop.posts)
    }

    @Test
    fun `an animating frame keeps the loop going until it settles`() {
        val loop = Loop()
        loop.demand.enabled = true

        repeat(5) { assertTrue(loop.vsync(animating = true)) }
        assertTrue(loop.vsync(animating = false))
        assertFalse(loop.vsync())
    }

    @Test
    fun `a request made while drawing is not lost`() {
        val loop = Loop()
        loop.demand.enabled = true

        // Filament skipped the frame, say: it asks again from inside the draw.
        loop.vsync { loop.demand.request() }

        assertTrue(loop.vsync())
        assertFalse(loop.vsync())
    }

    @Test
    fun `only the first frame after a stop is marked as following a pause`() {
        val loop = Loop()
        loop.demand.enabled = true
        val afterPause = mutableListOf<Boolean>()

        loop.vsync(animating = true) { afterPause += it }
        loop.vsync(animating = false) { afterPause += it }
        loop.demand.request()
        loop.vsync { afterPause += it }

        assertEquals(listOf(true, false, true), afterPause)
    }

    @Test
    fun `nothing is posted while disabled, and a request made then is drawn on enabling`() {
        val loop = Loop()
        loop.demand.request()
        assertEquals(0, loop.posts)

        loop.demand.enabled = true
        assertTrue(loop.vsync())
    }

    @Test
    fun `disabling withdraws the pending frame and resets the clock`() {
        val loop = Loop()
        loop.demand.enabled = true
        loop.vsync(animating = true)

        loop.demand.enabled = false
        assertEquals(0, loop.pending)

        loop.demand.enabled = true
        var afterPause = false
        loop.vsync { afterPause = it }
        assertTrue(afterPause)
    }
}
