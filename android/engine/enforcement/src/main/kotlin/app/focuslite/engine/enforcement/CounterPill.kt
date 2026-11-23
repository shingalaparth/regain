package app.focuslite.engine.enforcement

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The live reel counter that floats on top of the video: a reel glyph and a bold number, plus a small
 * grey note on the right (for example "4:32 left") while an unlock or budget is running.
 * Black on white to match the app. The number gives a small pop each time it goes up.
 */
class CounterPill(context: Context) : LinearLayout(context) {
    private val number = TextView(context)
    private val note = TextView(context)
    private var shown = -1

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        setPadding(dp(14), dp(8), dp(18), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F7F7F7"))
            cornerRadius = 999f
            setStroke(dp(1), Color.parseColor("#33000000"))
        }

        addView(ReelGlyph(context), LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })

        number.apply {
            setTextColor(Color.parseColor("#0B0B0B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum" // equal-width digits, so the pill does not wobble as numbers change
        }
        addView(number)

        note.apply {
            setTextColor(Color.parseColor("#5A5A5A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            visibility = GONE
        }
        addView(note, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
    }

    /** Sets the number and the optional note. Pops when the number went up. */
    fun update(count: Int, noteText: String?) {
        number.text = count.toString()
        if (noteText.isNullOrEmpty()) {
            note.visibility = GONE
        } else {
            note.text = noteText
            note.visibility = VISIBLE
        }
        if (shown in 0 until count) pop()
        shown = count
    }

    private fun pop() {
        val scale = android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        if (scale <= 0f) return // animations are off: the number just changes
        animate().cancel()
        animate().scaleX(1.16f).scaleY(1.16f).setDuration(110).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(170).start()
        }.start()
    }

    /** A small reel: a rounded frame with a play triangle, drawn here so there is no image file. */
    private class ReelGlyph(context: Context) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0B0B")
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * resources.displayMetrics.density
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0B0B") }
        private val frame = RectF()
        private val tri = Path()

        override fun onDraw(canvas: Canvas) {
            val s = stroke.strokeWidth
            val w = width.toFloat()
            val h = height.toFloat()
            // portrait frame, like a phone screen showing a reel
            frame.set(w * 0.2f + s / 2, s / 2, w * 0.8f - s / 2, h - s / 2)
            canvas.drawRoundRect(frame, w * 0.2f, w * 0.2f, stroke)
            // play triangle in the middle
            val cx = w / 2
            val cy = h / 2
            val r = w * 0.17f
            tri.reset()
            tri.moveTo(cx - r * 0.6f, cy - r)
            tri.lineTo(cx + r, cy)
            tri.lineTo(cx - r * 0.6f, cy + r)
            tri.close()
            canvas.drawPath(tri, fill)
        }
    }
}
