package app.focuslite.engine.policy

import app.focuslite.core.model.BlockReason
import app.focuslite.core.model.Decision
import app.focuslite.core.model.Surface
import app.focuslite.core.model.SurfaceMode
import app.focuslite.core.model.SurfaceSetting

/** Per-surface settings. Anything not listed falls back to [defaultSetting]. */
data class PolicyConfig(
    val settings: Map<Surface, SurfaceSetting> = DEFAULT_SETTINGS,
    val defaultSetting: SurfaceSetting = SurfaceSetting(SurfaceMode.ALLOW),
) {
    fun settingFor(surface: Surface): SurfaceSetting = settings[surface] ?: defaultSetting

    companion object {
        /**
         * Short-video surfaces start blocked; DMs, reels sent in DMs and everything else play.
         * EXPLORE stays allowed until its detection rule is verified on the device.
         */
        val DEFAULT_SETTINGS: Map<Surface, SurfaceSetting> = mapOf(
            Surface.REELS_FEED to SurfaceSetting(SurfaceMode.BLOCK),
            Surface.SHORTS to SurfaceSetting(SurfaceMode.BLOCK),
            Surface.SPOTLIGHT to SurfaceSetting(SurfaceMode.BLOCK),
            Surface.BROWSER_SHORT_URL to SurfaceSetting(SurfaceMode.BLOCK),
            Surface.WHOLE_APP to SurfaceSetting(SurfaceMode.BLOCK),
            Surface.REEL_FROM_DM to SurfaceSetting(SurfaceMode.ALLOW),
            Surface.DM to SurfaceSetting(SurfaceMode.ALLOW),
            Surface.EXPLORE to SurfaceSetting(SurfaceMode.ALLOW),
            Surface.OTHER to SurfaceSetting(SurfaceMode.ALLOW),
        )
    }
}

/** What is going on right now, besides the surface itself. All times are epoch milliseconds. */
data class PolicyContext(
    val nowMs: Long = 0,
    /** Time already spent on this surface today, for daily limits. */
    val surfaceUsedMs: Long = 0,
    /** Gate unlocks already taken today, which makes the next wait longer. */
    val unlocksToday: Int = 0,
    /** This surface is unlocked until this moment (after passing the pause gate). */
    val unlockedUntilMs: Long = 0,
    val postModeUntilMs: Long = 0,
    val focusUntilMs: Long = 0,
    val sleepPhase: SleepPhase = SleepPhase.NONE,
    /** The foreground app can be locked as a whole (a social app, not a browser). */
    val appLockable: Boolean = false,
)

/** Decides what to do about a detected surface. Pure: same inputs, same answer. */
class PolicyEngine(private val config: PolicyConfig = PolicyConfig()) {

    fun decide(surface: Surface, ctx: PolicyContext = PolicyContext()): Decision {
        // Hard locks first: a focus session or the sleep routine beats every other setting.
        if (ctx.appLockable) {
            if (ctx.focusUntilMs > ctx.nowMs) return Decision.Block(Surface.WHOLE_APP, BlockReason.FOCUS)
            if (ctx.sleepPhase == SleepPhase.OVERNIGHT || ctx.sleepPhase == SleepPhase.MORNING) {
                return Decision.Block(Surface.WHOLE_APP, BlockReason.SLEEP)
            }
        }
        if (surface !in SHORT_VIDEO) return Decision.Allow

        // Wind-down: no short video at all, not even through Post Mode or an unlock.
        if (ctx.sleepPhase != SleepPhase.NONE) return Decision.Block(surface, BlockReason.SLEEP)
        if (ctx.focusUntilMs > ctx.nowMs) return Decision.Block(surface, BlockReason.FOCUS)

        // Post Mode suspends the short-video rules for a few minutes so you can post.
        if (ctx.postModeUntilMs > ctx.nowMs) return Decision.Allow
        if (ctx.unlockedUntilMs > ctx.nowMs) return Decision.AllowFor(ctx.unlockedUntilMs - ctx.nowMs)

        val setting = config.settingFor(surface)
        return when (setting.mode) {
            SurfaceMode.ALLOW -> Decision.Allow
            SurfaceMode.BLOCK -> Decision.Block(surface, BlockReason.SETTING)
            SurfaceMode.PAUSE -> Decision.Gate(surface, waitSeconds(ctx.unlocksToday))
            SurfaceMode.LIMIT -> {
                val budget = setting.limitMinutes * 60_000L
                if (ctx.surfaceUsedMs < budget) Decision.AllowFor(budget - ctx.surfaceUsedMs)
                else Decision.Block(surface, BlockReason.LIMIT_REACHED)
            }
        }
    }

    companion object {
        /** Surfaces that count as "short video" for limits, Post Mode and sleep. */
        val SHORT_VIDEO: Set<Surface> = setOf(
            Surface.REELS_FEED, Surface.REEL_FROM_DM, Surface.SHORTS, Surface.SPOTLIGHT,
            Surface.EXPLORE, Surface.BROWSER_SHORT_URL, Surface.WHOLE_APP,
        )

        private val WAITS = intArrayOf(10, 20, 40, 60)

        /** The wait before unlocking grows with every unlock already taken today: 10, 20, 40, then 60 seconds. */
        fun waitSeconds(unlocksToday: Int): Int = WAITS[unlocksToday.coerceIn(0, WAITS.lastIndex)]
    }
}
