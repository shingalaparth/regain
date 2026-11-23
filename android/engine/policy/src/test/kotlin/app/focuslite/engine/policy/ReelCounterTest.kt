package app.focuslite.engine.policy

import app.focuslite.core.model.Surface
import kotlin.test.Test
import kotlin.test.assertEquals

class ReelCounterTest {
    private val reels = Surface.REELS_FEED
    private fun counter() = ReelCounter(minGapMs = 900, visitGapMs = 5_000)

    @Test
    fun `landing on a surface counts the first reel once`() {
        val c = counter()
        assertEquals(1, c.onSeen(reels, 0))
        assertEquals(0, c.onSeen(reels, 1_000)) // same visit
        assertEquals(0, c.onSeen(reels, 2_000))
    }

    @Test
    fun `each swipe is one more reel`() {
        val c = counter()
        var total = c.onSeen(reels, 0)
        total += c.onSwipe(reels, 3_000)
        total += c.onSwipe(reels, 7_000)
        total += c.onSwipe(reels, 12_000)
        assertEquals(4, total)
    }

    @Test
    fun `a burst of scroll events from one swipe counts once`() {
        val c = counter()
        c.onSeen(reels, 0)
        val burst = (0..9).sumOf { c.onSwipe(reels, 5_000L + it * 80) } // 10 events in 0.8 s
        assertEquals(1, burst)
    }

    @Test
    fun `scrolling when no surface is being watched counts nothing`() {
        val c = counter()
        assertEquals(0, c.onSwipe(reels, 1_000))
        c.onSeen(Surface.SHORTS, 2_000)
        assertEquals(0, c.onSwipe(reels, 5_000)) // a different surface
    }

    @Test
    fun `leaving and coming back is a new visit and counts again`() {
        val c = counter()
        assertEquals(1, c.onSeen(reels, 0))
        c.reset()
        assertEquals(1, c.onSeen(reels, 1_000))
    }

    @Test
    fun `a long silence also starts a new visit`() {
        val c = counter()
        assertEquals(1, c.onSeen(reels, 0))
        assertEquals(1, c.onSeen(reels, 6_000)) // not seen for more than 5 s
    }

    @Test
    fun `switching to another surface starts a visit there`() {
        val c = counter()
        c.onSeen(reels, 0)
        assertEquals(1, c.onSeen(Surface.SHORTS, 500))
    }

    @Test
    fun `a swipe right after landing does not double count`() {
        val c = counter()
        c.onSeen(reels, 0)
        assertEquals(0, c.onSwipe(reels, 300))
    }
}
