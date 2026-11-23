package app.focuslite.engine.enforcement

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "pause first" screen. Covers the app, shows a breathing circle and a countdown, then asks why you
 * want it. Picking a reason unlocks the surface for a few minutes; "Not now" leaves. No text input, so it
 * works without a keyboard. Must be used on the main thread.
 */
class GateOverlay(private val service: AccessibilityService) {
    private var root: FrameLayout? = null
    private var breath: ValueAnimator? = null
    private var countdown: CountDownTimer? = null

    /** The package the gate is covering, or null when hidden. */
    var packageName: String? = null
        private set

    val showing: Boolean get() = root != null

    /**
     * @param waitSeconds how long to wait before the reasons appear
     * @param unlockMinutes how long a reason unlocks the surface for
     * @param unlockNumber which unlock of the day this would be (1 = first)
     */
    fun show(
        pkg: String,
        title: String,
        waitSeconds: Int,
        unlockMinutes: Int,
        unlockNumber: Int,
        reasons: List<String>,
        onUnlock: (reason: String) -> Unit,
        onLeave: () -> Unit,
    ) {
        if (showing) return
        packageName = pkg
        val d = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val ring = View(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(5), Color.parseColor("#F7B23B"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(28) }
        }
        val heading = text("Take a breath", 26f, Color.WHITE, bold = true)
        val sub = text("$title · unlock #$unlockNumber today", 15f, Color.parseColor("#B5B8C2"))
        val count = text("$waitSeconds", 56f, Color.parseColor("#F7B23B"), bold = true).apply {
            setPadding(0, dp(18), 0, dp(6))
        }
        val hint = text("Breathe in… and out. Then decide.", 15f, Color.parseColor("#B5B8C2"))

        val reasonBox = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            gravity = Gravity.CENTER_HORIZONTAL
        }
        reasonBox.addView(text("Why do you want it? ($unlockMinutes min)", 16f, Color.WHITE).apply { setPadding(0, 0, 0, dp(10)) })
        reasons.forEach { reason ->
            reasonBox.addView(button(reason, Color.parseColor("#26FFFFFF"), Color.WHITE) { onUnlock(reason) }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(260), dp(48)).apply { topMargin = dp(8) }
            })
        }
        reasonBox.addView(button("Not now", Color.parseColor("#F7B23B"), Color.parseColor("#121318")) { onLeave() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(52)).apply { topMargin = dp(18) }
        })

        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ring); addView(heading); addView(sub); addView(count); addView(hint); addView(reasonBox)
        }
        val frame = FrameLayout(service).apply {
            setBackgroundColor(Color.parseColor("#F00B0C10"))
            isClickable = true // swallow touches so the app underneath cannot be used
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { service.getSystemService(WindowManager::class.java).addView(frame, params) }
            .onFailure { Log.w(TAG, "could not show gate", it); packageName = null; return }
        root = frame

        // Breathing: the ring grows and shrinks on a slow 4-second beat. With animations switched off in the
        // phone's settings the ring stays still and the words carry the rhythm instead.
        val animationsOn = android.provider.Settings.Global.getFloat(
            service.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) > 0f
        if (!animationsOn) hint.text = "Breathe in slowly… and out."
        if (animationsOn) breath = ValueAnimator.ofFloat(0.72f, 1f).apply {
            duration = 4_000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { v ->
                val s = v.animatedValue as Float
                ring.scaleX = s; ring.scaleY = s
                hint.text = if (v.animatedFraction < 0.5f) "Breathe in…" else "Breathe out…"
            }
            start()
        }

        countdown = object : CountDownTimer(waitSeconds * 1_000L, 250) {
            override fun onTick(msLeft: Long) { count.text = ((msLeft + 999) / 1000).toString() }
            override fun onFinish() {
                count.visibility = View.GONE
                hint.visibility = View.GONE
                reasonBox.visibility = View.VISIBLE
                breath?.cancel()
                ring.scaleX = 1f; ring.scaleY = 1f
            }
        }.start()
    }

    fun hide() {
        countdown?.cancel(); countdown = null
        breath?.cancel(); breath = null
        val view = root ?: return
        root = null
        packageName = null
        runCatching { service.getSystemService(WindowManager::class.java).removeView(view) }
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(service).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        if (bold) paint.flags = paint.flags or Paint.FAKE_BOLD_TEXT_FLAG
    }

    private fun button(label: String, bg: Int, fg: Int, onClick: () -> Unit) = TextView(service).apply {
        text = label
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        paint.flags = paint.flags or Paint.FAKE_BOLD_TEXT_FLAG
        background = GradientDrawable().apply { setColor(bg); cornerRadius = 999f }
        setOnClickListener { onClick() }
    }

    private companion object {
        const val TAG = "Regain"
    }
}
