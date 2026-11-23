package app.focuslite.engine.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class UsageEventType { RESUMED, PAUSED, SCREEN_OFF }

/** One record from Android's usage-event log, reduced to what matters here. */
data class UsageEvent(
    val packageName: String,
    val activity: String,
    val type: UsageEventType,
    val timeMs: Long,
)

/** A stretch of time an app was on screen. */
data class Session(val packageName: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

object SessionBuilder {
    /**
     * Pairs resume/pause events into sessions. An app counts as on screen from the moment its first
     * activity resumes until its last open activity pauses, or the screen turns off. Anything still
     * open at [nowMs] ends there. Events must be in time order.
     */
    fun build(events: List<UsageEvent>, nowMs: Long): List<Session> {
        val open = HashMap<String, MutableSet<String>>() // package -> activities currently resumed
        val startedAt = HashMap<String, Long>()
        val sessions = ArrayList<Session>()

        fun close(pkg: String, at: Long) {
            val start = startedAt.remove(pkg) ?: return
            open.remove(pkg)
            if (at > start) sessions += Session(pkg, start, at)
        }

        for (e in events) {
            when (e.type) {
                UsageEventType.RESUMED -> {
                    val activities = open.getOrPut(e.packageName) { HashSet() }
                    if (activities.isEmpty()) startedAt[e.packageName] = e.timeMs
                    activities += e.activity
                }
                UsageEventType.PAUSED -> {
                    val activities = open[e.packageName] ?: continue // paused before the log window began
                    activities -= e.activity
                    if (activities.isEmpty()) close(e.packageName, e.timeMs)
                }
                UsageEventType.SCREEN_OFF -> open.keys.toList().forEach { close(it, e.timeMs) }
            }
        }
        open.keys.toList().forEach { close(it, nowMs) }
        return mergeCloseSessions(sessions)
    }

    /**
     * Apps pause and resume within moments when you open a dialog or switch screens inside them. Android's
     * own Digital Wellbeing treats that as one visit, so sessions of the same app less than [MERGE_GAP_MS]
     * apart are joined.
     */
    private fun mergeCloseSessions(sessions: List<Session>): List<Session> {
        val merged = ArrayList<Session>()
        for ((_, list) in sessions.groupBy { it.packageName }) {
            var current: Session? = null
            for (s in list.sortedBy { it.startMs }) {
                val c = current
                current = if (c != null && s.startMs - c.endMs <= MERGE_GAP_MS) {
                    c.copy(endMs = maxOf(c.endMs, s.endMs))
                } else {
                    if (c != null) merged += c
                    s
                }
            }
            current?.let { merged += it }
        }
        return merged.sortedBy { it.startMs }
    }

    const val MERGE_GAP_MS = 10_000L
}

object DayTotals {
    /**
     * Adds sessions up per local day and package, in milliseconds. A session that crosses midnight is
     * split, so each day only gets its own part.
     */
    fun perDay(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Map<String, Long>> {
        val totals = HashMap<LocalDate, HashMap<String, Long>>()
        for (s in sessions) {
            var cursor = s.startMs
            while (cursor < s.endMs) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(s.endMs, dayEnd)
                val perPackage = totals.getOrPut(day) { HashMap() }
                perPackage[s.packageName] = (perPackage[s.packageName] ?: 0L) + (sliceEnd - cursor)
                cursor = sliceEnd
            }
        }
        return totals
    }
}

/** "<1m", "12m", "1h 05m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        ms <= 0 -> "0m"
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
    }
}
