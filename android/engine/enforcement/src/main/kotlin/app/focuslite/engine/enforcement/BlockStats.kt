package app.focuslite.engine.enforcement

import android.content.Context
import app.focuslite.core.model.Surface
import java.time.LocalDate

/**
 * Counts how often each surface was blocked, per day. Plain SharedPreferences for now;
 * moves to Room when the stats screen needs richer history queries (Phase 3).
 */
class BlockStats(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(surface: Surface, date: LocalDate = LocalDate.now()) {
        val key = key(date, surface)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun forDay(date: LocalDate = LocalDate.now()): Map<Surface, Int> =
        Surface.entries.associateWith { prefs.getInt(key(date, it), 0) }.filterValues { it > 0 }

    fun totalForDay(date: LocalDate = LocalDate.now()): Int = forDay(date).values.sum()

    /** Totals for the last [days] days ending today, oldest first. */
    fun lastDays(days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> =
        (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            date to totalForDay(date)
        }

    private fun key(date: LocalDate, surface: Surface) = "blocks:$date:${surface.name}"

    private companion object {
        const val FILE = "focuslite_stats"
    }
}
