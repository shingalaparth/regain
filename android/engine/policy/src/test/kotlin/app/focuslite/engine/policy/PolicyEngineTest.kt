package app.focuslite.engine.policy

import app.focuslite.core.model.BlockReason
import app.focuslite.core.model.Decision
import app.focuslite.core.model.Surface
import app.focuslite.core.model.SurfaceMode
import app.focuslite.core.model.SurfaceSetting
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEngineTest {
    private val engine = PolicyEngine()
    private val min = 60_000L

    private fun engineWith(surface: Surface, setting: SurfaceSetting) =
        PolicyEngine(PolicyConfig(settings = PolicyConfig.DEFAULT_SETTINGS + (surface to setting)))

    @Test
    fun `short video surfaces are blocked by default`() {
        listOf(Surface.REELS_FEED, Surface.SHORTS, Surface.SPOTLIGHT, Surface.BROWSER_SHORT_URL, Surface.WHOLE_APP)
            .forEach { assertEquals(Decision.Block(it, BlockReason.SETTING), engine.decide(it), it.name) }
    }

    @Test
    fun `messages and everything else are allowed`() {
        listOf(Surface.DM, Surface.REEL_FROM_DM, Surface.EXPLORE, Surface.OTHER)
            .forEach { assertEquals(Decision.Allow, engine.decide(it), it.name) }
    }

    @Test
    fun `a custom setting can allow a normally blocked surface`() {
        val custom = engineWith(Surface.SHORTS, SurfaceSetting(SurfaceMode.ALLOW))
        assertEquals(Decision.Allow, custom.decide(Surface.SHORTS))
        assertEquals(Decision.Block(Surface.REELS_FEED), custom.decide(Surface.REELS_FEED))
    }

    @Test
    fun `surfaces missing from the config use the default setting`() {
        val strict = PolicyEngine(PolicyConfig(settings = emptyMap(), defaultSetting = SurfaceSetting(SurfaceMode.BLOCK)))
        assertEquals(Decision.Allow, strict.decide(Surface.OTHER)) // non short-video is never touched
        assertEquals(Decision.Block(Surface.SHORTS), strict.decide(Surface.SHORTS))
    }

    // ---- daily limit ----

    @Test
    fun `a limit allows the budget then blocks`() {
        val limited = engineWith(Surface.REELS_FEED, SurfaceSetting(SurfaceMode.LIMIT, limitMinutes = 15))
        assertEquals(Decision.AllowFor(15 * min), limited.decide(Surface.REELS_FEED, PolicyContext(surfaceUsedMs = 0)))
        assertEquals(Decision.AllowFor(5 * min), limited.decide(Surface.REELS_FEED, PolicyContext(surfaceUsedMs = 10 * min)))
        assertEquals(
            Decision.Block(Surface.REELS_FEED, BlockReason.LIMIT_REACHED),
            limited.decide(Surface.REELS_FEED, PolicyContext(surfaceUsedMs = 15 * min)),
        )
    }

    // ---- pause gate ----

    @Test
    fun `the pause gate waits longer after each unlock`() {
        val gated = engineWith(Surface.SHORTS, SurfaceSetting(SurfaceMode.PAUSE))
        val waits = (0..5).map { (gated.decide(Surface.SHORTS, PolicyContext(unlocksToday = it)) as Decision.Gate).waitSeconds }
        assertEquals(listOf(10, 20, 40, 60, 60, 60), waits)
    }

    @Test
    fun `an unlock lets the surface through until it expires`() {
        val gated = engineWith(Surface.SHORTS, SurfaceSetting(SurfaceMode.PAUSE))
        val now = 1_000_000L
        assertEquals(
            Decision.AllowFor(3 * min),
            gated.decide(Surface.SHORTS, PolicyContext(nowMs = now, unlockedUntilMs = now + 3 * min)),
        )
        assertEquals(
            Decision.Gate(Surface.SHORTS, 10),
            gated.decide(Surface.SHORTS, PolicyContext(nowMs = now, unlockedUntilMs = now - 1)),
        )
    }

    // ---- Post Mode ----

    @Test
    fun `post mode lets blocked surfaces through until it ends`() {
        val now = 5_000L
        assertEquals(Decision.Allow, engine.decide(Surface.REELS_FEED, PolicyContext(nowMs = now, postModeUntilMs = now + 1)))
        assertEquals(
            Decision.Block(Surface.REELS_FEED),
            engine.decide(Surface.REELS_FEED, PolicyContext(nowMs = now, postModeUntilMs = now)),
        )
    }

    // ---- focus session ----

    @Test
    fun `a focus session locks a social app completely`() {
        val ctx = PolicyContext(nowMs = 10, focusUntilMs = 100, appLockable = true)
        assertEquals(Decision.Block(Surface.WHOLE_APP, BlockReason.FOCUS), engine.decide(Surface.OTHER, ctx))
        assertEquals(Decision.Block(Surface.WHOLE_APP, BlockReason.FOCUS), engine.decide(Surface.DM, ctx))
    }

    @Test
    fun `a focus session does not lock a browser as a whole, but blocks short video in it`() {
        val ctx = PolicyContext(nowMs = 10, focusUntilMs = 100, appLockable = false)
        assertEquals(Decision.Allow, engine.decide(Surface.OTHER, ctx))
        assertEquals(Decision.Block(Surface.BROWSER_SHORT_URL, BlockReason.FOCUS), engine.decide(Surface.BROWSER_SHORT_URL, ctx))
    }

    @Test
    fun `focus beats post mode and unlocks`() {
        val ctx = PolicyContext(nowMs = 10, focusUntilMs = 100, postModeUntilMs = 200, unlockedUntilMs = 200)
        assertEquals(Decision.Block(Surface.REELS_FEED, BlockReason.FOCUS), engine.decide(Surface.REELS_FEED, ctx))
    }

    @Test
    fun `an expired focus session changes nothing`() {
        val ctx = PolicyContext(nowMs = 100, focusUntilMs = 100, appLockable = true)
        assertEquals(Decision.Allow, engine.decide(Surface.OTHER, ctx))
    }

    // ---- sleep ----

    @Test
    fun `wind down blocks short video even through post mode, but leaves the app usable`() {
        val ctx = PolicyContext(nowMs = 10, sleepPhase = SleepPhase.WIND_DOWN, postModeUntilMs = 99, appLockable = true)
        assertEquals(Decision.Block(Surface.REELS_FEED, BlockReason.SLEEP), engine.decide(Surface.REELS_FEED, ctx))
        assertEquals(Decision.Allow, engine.decide(Surface.DM, ctx))
    }

    @Test
    fun `overnight and morning lock the app completely`() {
        listOf(SleepPhase.OVERNIGHT, SleepPhase.MORNING).forEach { phase ->
            val ctx = PolicyContext(sleepPhase = phase, appLockable = true)
            assertEquals(Decision.Block(Surface.WHOLE_APP, BlockReason.SLEEP), engine.decide(Surface.DM, ctx), phase.name)
        }
    }
}
