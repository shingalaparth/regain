package app.focuslite.engine.enforcement

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import app.focuslite.core.model.BlockReason
import app.focuslite.core.model.Surface

/**
 * Carries out a Block decision: jump to a safe tab (or press Back / Home) and show a short notice.
 * Also owns the small live timer shown while a time budget or unlock is running.
 * Must be called on the main thread.
 */
class Enforcer(
    private val service: AccessibilityService,
    private val stats: BlockStats,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val main = Handler(Looper.getMainLooper())
    private var lastActionAt = 0L
    private var notice: TextView? = null
    private var counter: CounterPill? = null
    private val removeNotice = Runnable { hideNotice() }

    val counterVisible: Boolean get() = counter != null

    /**
     * @param root window root, used to find [escapeTabId]; may be null.
     * @param escapeTabId short id of the tab to click instead of pressing Back.
     */
    fun block(surface: Surface, reason: BlockReason, packageName: String, root: AccessibilityNodeInfo?, escapeTabId: String?) {
        val now = clock()
        if (now - lastActionAt < COOLDOWN_MS) return // do not fight the app's own transitions
        lastActionAt = now

        // Locks and whole-app blocks send you Home; screen blocks step back or switch tab.
        val wholeApp = surface == Surface.WHOLE_APP || reason == BlockReason.FOCUS || reason == BlockReason.SLEEP
        val acted = if (wholeApp) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            clickEscapeTab(root, packageName, escapeTabId) ||
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        Log.i(TAG, "blocked $surface ($reason) in $packageName, action performed=$acted")
        stats.record(surface)
        showNotice(messageFor(surface, reason))
    }

    /**
     * Shows or updates the live reel counter on top of the video. [note] is the small grey text beside the
     * number, for example the time left on an unlock.
     */
    fun showCounter(count: Int, note: String?) {
        val existing = counter
        if (existing != null) {
            existing.update(count, note)
            return
        }
        val view = CounterPill(service).apply { update(count, note) }
        val params = overlayParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL, yDp = 56)
        runCatching {
            service.getSystemService(WindowManager::class.java).addView(view, params)
            counter = view
        }.onFailure { Log.w(TAG, "could not show counter", it) }
    }

    fun hideCounter() {
        val view = counter ?: return
        counter = null
        runCatching { service.getSystemService(WindowManager::class.java).removeView(view) }
    }

    fun shutdown() {
        main.removeCallbacks(removeNotice)
        hideNotice()
        hideCounter()
    }

    private fun clickEscapeTab(root: AccessibilityNodeInfo?, pkg: String, tabId: String?): Boolean {
        if (root == null || tabId == null) return false
        val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$tabId")
        for (node in nodes) {
            var target: AccessibilityNodeInfo? = node
            var hops = 0
            while (target != null && !target.isClickable && hops < MAX_CLICK_HOPS) {
                target = target.parent
                hops++
            }
            if (target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    private fun messageFor(surface: Surface, reason: BlockReason) = when (reason) {
        BlockReason.FOCUS -> "Focus session is on"
        BlockReason.SLEEP -> "Sleep mode"
        BlockReason.LIMIT_REACHED -> "Daily limit reached"
        BlockReason.SETTING -> when (surface) {
            Surface.REELS_FEED -> "Reels blocked"
            Surface.SHORTS -> "Shorts blocked"
            Surface.SPOTLIGHT -> "Spotlight blocked"
            Surface.BROWSER_SHORT_URL -> "Short videos blocked"
            Surface.WHOLE_APP -> "App blocked"
            else -> "Blocked"
        }
    }

    private fun showNotice(text: String) {
        hideNotice()
        val view = pill("Regain · $text", "#E6102A33")
        runCatching {
            service.getSystemService(WindowManager::class.java)
                .addView(view, overlayParams(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, yDp = 96))
            notice = view
            main.postDelayed(removeNotice, NOTICE_MS)
        }.onFailure { Log.w(TAG, "could not show notice", it) }
    }

    private fun hideNotice() {
        val view = notice ?: return
        notice = null
        runCatching { service.getSystemService(WindowManager::class.java).removeView(view) }
    }

    private fun pill(text: String, colorHex: String) = TextView(service).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = 999f
        }
    }

    private fun overlayParams(gravity: Int, yDp: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        y = (yDp * service.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val TAG = "Regain"
        const val COOLDOWN_MS = 1_000L
        const val NOTICE_MS = 1_600L
        const val MAX_CLICK_HOPS = 4
    }
}
