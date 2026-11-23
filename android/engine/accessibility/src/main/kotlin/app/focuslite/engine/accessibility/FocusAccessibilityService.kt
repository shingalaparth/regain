package app.focuslite.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.focuslite.core.model.BlockReason
import app.focuslite.core.model.Decision
import app.focuslite.core.model.Surface
import app.focuslite.engine.detection.Detector
import app.focuslite.engine.enforcement.BlockStats
import app.focuslite.engine.enforcement.Enforcer
import app.focuslite.engine.enforcement.GateOverlay
import app.focuslite.engine.policy.PolicyContext
import app.focuslite.engine.policy.PolicyEngine
import app.focuslite.engine.policy.ReelCounter
import app.focuslite.core.model.normalizeViewId
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches the covered apps (whatever the rules list), detects short-video screens and applies the user's
 * rules: block, pause first, daily limit, Post Mode, focus session, sleep routine.
 *
 * Event flood control: window-content events fire constantly while a video plays, so events are only a
 * "something changed in package X" signal; the screen is inspected at most every [SAMPLE_MS]. While a
 * counted short-video surface is on screen a one-second heartbeat keeps the clock honest, because a video
 * playing without swiping produces almost no events.
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changed = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var detector: Detector? = null
    private var policyPrefs: PolicyPrefs? = null
    private var state: RegainState? = null
    private var unlockLog: UnlockLog? = null
    private var enforcer: Enforcer? = null
    private var gate: GateOverlay? = null
    private var dumpReceiver: BroadcastReceiver? = null

    // time accounting for short-video surfaces
    private var lastTickMs = 0L
    private var countedSurface: Surface? = null
    private var heartbeatPending = false
    @Volatile private var timer: RunningTimer? = null
    @Volatile private var pillNote: String? = null

    // reel counter: what is on screen right now, and whether watching it is allowed
    private val reelCounter = ReelCounter()
    @Volatile private var currentSurface: Surface? = null
    @Volatile private var currentPkg: String? = null
    @Volatile private var currentlyWatching = false
    private var widened = false

    @OptIn(FlowPreview::class)
    override fun onServiceConnected() {
        super.onServiceConnected()
        val rules = RulesLoader.load(this)
        val det = Detector(rules)
        detector = det
        policyPrefs = PolicyPrefs(this)
        state = RegainState(this).also { recordProtectionGap(it) }
        unlockLog = UnlockLog(this)
        enforcer = Enforcer(this, BlockStats(this))
        gate = GateOverlay(this)

        narrowFilter()
        instance = this
        Log.i(TAG, "connected, rules v${rules.version}, watching ${det.packages().size} packages")
        scope.launch { changed.sample(SAMPLE_MS).collect { inspect(it) } }
        registerDebugReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            countSwipe(event, pkg)
            return
        }
        // While the gate or timer is up we listen to every app, only to notice the user leaving. Leaving means a
        // real window change in another app. System UI (clock, network-speed ticker, shade) and keyboards fire
        // events all the time and must not count, or the pause screen would reset itself.
        if (widened && detector?.watches(pkg) != true) {
            // Our own overlays also report our package, so only our real screens count as "left".
            val ownActivity = pkg == packageName && event.className?.toString()?.endsWith("Activity") == true
            val leftForOtherApp = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                (pkg != packageName || ownActivity) && !pkg.startsWith("com.android.systemui") && !isKeyboard(pkg)
            if (leftForOtherApp) scope.launch(Dispatchers.Main) { dismissOverlays() }
            return
        }
        if (detector?.watches(pkg) == true) changed.tryEmit(pkg)
    }

    /**
     * A scroll of the video pager is one swipe to the next reel. Counted only while the surface is on screen
     * and watching it is allowed: a blocked or paused reel is never watched, so it is never counted.
     */
    private fun countSwipe(event: AccessibilityEvent, pkg: String) {
        val det = detector ?: return
        val surface = currentSurface ?: return
        if (!currentlyWatching || currentPkg != pkg) return
        val pagers = det.counterIds(pkg)
        if (pagers.isEmpty()) return
        var node = event.source
        var hops = 0
        while (node != null && hops < 5) {
            val id = node.viewIdResourceName?.let(::normalizeViewId)
            if (id != null && id in pagers) {
                val n = synchronized(reelCounter) { reelCounter.onSwipe(surface, System.currentTimeMillis()) }
                if (n > 0) {
                    state?.addReels(surface, n)
                    // update the pill right away so the number pops the moment you swipe
                    state?.let { st -> enforcer?.takeIf { it.counterVisible }?.showCounter(st.reelsTotal(), pillNote) }
                }
                return
            }
            node = node.parent
            hops++
        }
    }

    private fun isKeyboard(pkg: String): Boolean =
        pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains(".ime")

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // Protection is going away (switched off, or the system stopped us). Remember when.
        state?.serviceStoppedAt = System.currentTimeMillis()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        dumpReceiver?.let { runCatching { unregisterReceiver(it) } }
        gate?.hide()
        enforcer?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    /** If protection was off since the last run, log the gap so the app can show it. */
    private fun recordProtectionGap(s: RegainState) {
        val stoppedAt = s.serviceStoppedAt
        if (stoppedAt > 0) {
            TamperLog(this).append(ProtectionGap(stoppedAt, System.currentTimeMillis()))
            s.serviceStoppedAt = 0
        }
    }

    private suspend fun inspect(pkg: String) {
        val det = detector ?: return
        val st = state ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return // a different app came to the front meanwhile

        val snapshot = SnapshotBuilder.build(root, pkg, det.urlBarId(pkg))
        val surface = det.detect(snapshot)
        if (surface != Surface.OTHER) Log.d(TAG, "detected $surface in $pkg")

        val now = System.currentTimeMillis()
        val ctx = PolicyContext(
            nowMs = now,
            surfaceUsedMs = st.usedMs(surface),
            unlocksToday = unlockLog?.since(startOfTodayMs())?.size ?: 0,
            unlockedUntilMs = st.unlockedUntil(surface),
            postModeUntilMs = st.postModeUntil,
            focusUntilMs = st.focusUntil,
            sleepPhase = st.sleepSchedule().phaseAt(LocalTime.now().let { it.hour * 60 + it.minute }),
            appLockable = det.lockable(pkg),
        )
        val decision = PolicyEngine(policyPrefs?.config() ?: app.focuslite.engine.policy.PolicyConfig()).decide(surface, ctx)

        when (decision) {
            is Decision.Block -> {
                stopCounting()
                notWatching()
                timer = null
                withContext(Dispatchers.Main) {
                    dismissOverlays()
                    enforcer?.block(decision.surface, decision.reason, pkg, root, det.escapeTab(pkg))
                }
            }
            is Decision.Gate -> {
                stopCounting()
                notWatching()
                timer = null
                withContext(Dispatchers.Main) {
                    enforcer?.hideCounter() // an expired unlock must not leave its countdown behind
                    showGate(pkg, surface, decision.waitSeconds, ctx.unlocksToday)
                }
            }
            is Decision.Allow, is Decision.AllowFor -> {
                val remaining = (decision as? Decision.AllowFor)?.remainingMs
                val shortVideo = surface in PolicyEngine.SHORT_VIDEO
                if (shortVideo) {
                    count(surface, now, pkg)
                    currentSurface = surface
                    currentPkg = pkg
                    currentlyWatching = true
                    val n = synchronized(reelCounter) { reelCounter.onSeen(surface, now) }
                    if (n > 0) st.addReels(surface, n)
                } else {
                    stopCounting()
                    notWatching()
                }

                // The countdown pill belongs to the whole visit, not to one screen: it stays up on every
                // screen of the app until the time runs out or you leave, instead of blinking off whenever
                // Instagram moves between screens.
                if (remaining != null && shortVideo) {
                    val unlock = ctx.unlockedUntilMs > now
                    timer = RunningTimer(pkg, label(surface), if (unlock) ctx.unlockedUntilMs else 0L, remaining)
                } else if (timer?.pkg != pkg || timer?.isOver(now) == true) {
                    timer = null
                }
                val shown = timer

                // The reel counter pill: on whenever a short-video surface is being watched, and (with the time
                // left beside the number) on every screen of the app while an unlock or budget is running.
                val note = when {
                    shown != null -> shown.left(now)
                    shortVideo && st.postModeUntil > now -> "Post Mode"
                    else -> null
                }
                val pill = st.showCounterPill && (shortVideo || shown != null)
                pillNote = note
                val total = st.reelsTotal()
                withContext(Dispatchers.Main) {
                    if (gate?.showing == true) gate?.hide()
                    if (pill) {
                        widen(true)
                        enforcer?.showCounter(total, note)
                        ensureHeartbeat(pkg)
                    } else {
                        enforcer?.hideCounter()
                        if (gate?.showing != true) widen(false)
                    }
                }
            }
        }
    }

    // ---- pause gate ----

    private fun showGate(pkg: String, surface: Surface, waitSeconds: Int, unlocksToday: Int) {
        val g = gate ?: return
        if (g.showing) return
        widen(true)
        g.show(
            pkg = pkg,
            title = label(surface),
            waitSeconds = waitSeconds,
            unlockMinutes = UNLOCK_MINUTES,
            unlockNumber = unlocksToday + 1,
            reasons = REASONS,
            onUnlock = { reason ->
                val now = System.currentTimeMillis()
                state?.setUnlockedUntil(surface, now + UNLOCK_MINUTES * 60_000L)
                unlockLog?.append(UnlockEvent(now, surface, reason, waitSeconds))
                g.hide()
                widen(false)
            },
            onLeave = {
                g.hide()
                widen(false)
                performGlobalAction(GLOBAL_ACTION_BACK)
            },
        )
    }

    private fun dismissOverlays() {
        timer = null
        gate?.hide()
        enforcer?.hideCounter()
        widen(false)
    }

    /** Listen to every app while an overlay is up (to notice the user leaving); back to the watched apps otherwise. */
    private fun widen(on: Boolean) {
        if (widened == on) return
        widened = on
        if (on) serviceInfo = serviceInfo.apply { packageNames = null } else narrowFilter()
    }

    private fun narrowFilter() {
        val det = detector ?: return
        serviceInfo = serviceInfo.apply { packageNames = det.packages().toTypedArray() }
        widened = false
    }

    // ---- time on short-video surfaces ----

    private fun count(surface: Surface, now: Long, pkg: String) {
        val st = state ?: return
        if (countedSurface == surface && lastTickMs > 0) {
            st.addUsedMs(surface, minOf(now - lastTickMs, MAX_TICK_GAP_MS))
        }
        countedSurface = surface
        lastTickMs = now
        ensureHeartbeat(pkg)
    }

    /** Re-checks the screen once a second, so clocks tick even when the app produces no events. */
    private fun ensureHeartbeat(pkg: String) {
        if (heartbeatPending) return
        heartbeatPending = true
        scope.launch {
            delay(HEARTBEAT_MS)
            heartbeatPending = false
            changed.tryEmit(pkg)
        }
    }

    /** The countdown behind the pill's note while an unlock or daily budget is running. [untilMs] is 0 for a budget. */
    private data class RunningTimer(val pkg: String, val label: String, val untilMs: Long, val budgetLeftMs: Long) {
        fun isOver(now: Long) = untilMs > 0 && untilMs <= now
        fun left(now: Long): String {
            val ms = if (untilMs > 0) untilMs - now else budgetLeftMs
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d left".format(s / 60, s % 60)
        }
    }

    private fun notWatching() {
        currentlyWatching = false
        currentSurface = null
        synchronized(reelCounter) { reelCounter.reset() }
    }

    private fun stopCounting() {
        countedSurface = null
        lastTickMs = 0
    }

    private fun startOfTodayMs(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun label(surface: Surface) = when (surface) {
        Surface.REELS_FEED -> "Reels"
        Surface.REEL_FROM_DM -> "Reels from friends"
        Surface.SHORTS -> "Shorts"
        Surface.SPOTLIGHT -> "Spotlight"
        Surface.BROWSER_SHORT_URL -> "Short videos"
        Surface.WHOLE_APP -> "This app"
        else -> surface.name
    }

    // ---- Diagnostics: record the real view tree so rules can be written and tested against it ----

    /** Saves the current foreground window's structure (ids only, no text) to the app's files dir. */
    fun dumpCurrentWindow(): File? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val now = System.currentTimeMillis()
        val dir = File(getExternalFilesDir(null), "dumps").apply { mkdirs() }
        val file = File(dir, "$pkg-$now.json")
        file.writeText(TreeDumper.dump(root, pkg, now).toString(2))
        Log.i(TAG, "dumped $pkg to ${file.absolutePath}")
        return file
    }

    /** Dumps after [delayMs], so the user has time to switch to the screen they want recorded. */
    fun dumpAfter(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            dumpCurrentWindow()
        }
    }

    /** Debug builds only: `adb shell am broadcast -a app.focuslite.DUMP --el delay_ms 5000`. */
    private fun registerDebugReceiver() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                dumpAfter(intent.getLongExtra("delay_ms", 0L))
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(ACTION_DUMP), ContextCompat.RECEIVER_EXPORTED,
        )
        dumpReceiver = receiver
    }

    companion object {
        const val ACTION_DUMP = "app.focuslite.DUMP"
        const val UNLOCK_MINUTES = 5
        val REASONS = listOf("I'm bored", "I need it for something", "A friend sent it", "Just a quick look")
        private const val TAG = "Regain"
        private const val SAMPLE_MS = 250L
        private const val HEARTBEAT_MS = 1_000L
        private const val MAX_TICK_GAP_MS = 3_000L

        /** The running service, or null when it is switched off in Accessibility settings. */
        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }
}
