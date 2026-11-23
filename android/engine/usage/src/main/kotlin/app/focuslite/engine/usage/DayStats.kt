package app.focuslite.engine.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything about one day of phone use, worked out from real sessions. */
data class DayStats(
    val day: LocalDate,
    /** Milliseconds on screen per package. */
    val perPackage: Map<String, Long>,
    /** Milliseconds on screen in each hour of the day, index 0 is 00:00-01:00. Always 24 long. */
    val perHour: LongArray,
    /** How many times each package was opened (one per session). */
    val opens: Map<String, Int>,
    val longestSessionMs: Long,
    /** First and last moment of use that day, epoch millis, or null if nothing happened. */
    val firstUseMs: Long?,
    val lastUseMs: Long?,
) {
    val totalMs: Long get() = perPackage.values.sum()
    val totalOpens: Int get() = opens.values.sum()

    override fun equals(other: Any?): Boolean = other is DayStats && day == other.day &&
        perPackage == other.perPackage && perHour.contentEquals(other.perHour) && opens == other.opens &&
        longestSessionMs == other.longestSessionMs && firstUseMs == other.firstUseMs && lastUseMs == other.lastUseMs

    override fun hashCode(): Int = day.hashCode() * 31 + perHour.contentHashCode()

    companion object {
        /** Stats for [day], counting only [packages] (all packages when null). Sessions may cross midnight. */
        fun of(sessions: List<Session>, day: LocalDate, zone: ZoneId, packages: Set<String>? = null): DayStats {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val perPackage = HashMap<String, Long>()
            val opens = HashMap<String, Int>()
            val perHour = LongArray(24)
            var longest = 0L
            var first: Long? = null
            var last: Long? = null

            for (s in sessions) {
                if (packages != null && s.packageName !in packages) continue
                val from = maxOf(s.startMs, start)
                val to = minOf(s.endMs, end)
                if (to <= from) continue
                perPackage[s.packageName] = (perPackage[s.packageName] ?: 0L) + (to - from)
                if (s.startMs >= start) opens[s.packageName] = (opens[s.packageName] ?: 0) + 1
                longest = maxOf(longest, to - from)
                first = minOf(first ?: from, from)
                last = maxOf(last ?: to, to)

                // Spread the session over the hours it touches.
                var cursor = from
                while (cursor < to) {
                    val hour = Instant.ofEpochMilli(cursor).atZone(zone).hour
                    val hourEnd = Instant.ofEpochMilli(cursor).atZone(zone)
                        .withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                    val slice = minOf(to, hourEnd)
                    perHour[hour] += slice - cursor
                    cursor = slice
                }
            }
            return DayStats(day, perPackage, perHour, opens, longest, first, last)
        }
    }
}

object Goals {
    /**
     * Counts consecutive days, going back from [today], on which total time stayed within [goalMs].
     * Today only counts once it is within the goal, and a day over the goal ends the run. Days with
     * no use at all count as within the goal. [totals] holds only days we have data for, so the run
     * cannot reach further back than the oldest day in [totals]; [oldest] is that limit.
     */
    fun streak(totals: Map<LocalDate, Long>, goalMs: Long, today: LocalDate, oldest: LocalDate): Int {
        var days = 0
        var day = today
        while (!day.isBefore(oldest)) {
            val ms = totals[day] ?: 0L
            if (ms > goalMs) break
            days++
            day = day.minusDays(1)
        }
        return days
    }
}
