package app.focuslite.engine.policy

import app.focuslite.core.model.Surface

/**
 * Counts short videos as you watch them. Only call it while a surface is really being watched (allowed),
 * never while it is blocked or behind the pause screen.
 *
 * A reel is counted when
 *  - a visit starts (the first reel you land on), and
 *  - each swipe to the next one, but never twice within [minGapMs] (one swipe fires many scroll events).
 * A visit ends when you leave the surface, or when it was not seen for [visitGapMs].
 *
 * Not thread-safe: call it from one thread, or wrap calls in a lock.
 */
class ReelCounter(
    private val minGapMs: Long = 900,
    private val visitGapMs: Long = 5_000,
) {
    private var surface: Surface? = null
    private var lastSeenMs = 0L
    private var lastCountMs = Long.MIN_VALUE / 2

    /** Call each time the surface is seen (every screen check). Returns 1 when this starts a new visit. */
    fun onSeen(surface: Surface, nowMs: Long): Int {
        val newVisit = this.surface != surface || nowMs - lastSeenMs > visitGapMs
        this.surface = surface
        lastSeenMs = nowMs
        if (!newVisit) return 0
        lastCountMs = nowMs
        return 1
    }

    /** Call on a swipe (scroll) event of the video pager. Returns 1 when it is a new reel. */
    fun onSwipe(surface: Surface, nowMs: Long): Int {
        if (this.surface != surface) return 0
        lastSeenMs = nowMs
        if (nowMs - lastCountMs < minGapMs) return 0
        lastCountMs = nowMs
        return 1
    }

    /** Forget the current visit (the surface was left, blocked or gated). */
    fun reset() {
        surface = null
    }
}
