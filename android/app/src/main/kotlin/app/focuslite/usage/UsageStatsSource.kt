package app.focuslite.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import app.focuslite.engine.usage.DayStats
import app.focuslite.engine.usage.Session
import app.focuslite.engine.usage.SessionBuilder
import app.focuslite.engine.usage.UsageEvent
import app.focuslite.engine.usage.UsageEventType
import java.time.LocalDate
import java.time.ZoneId

/** Real on-screen time per app, read from Android's usage-event log. Nothing here is estimated or stored. */
class UsageStatsSource(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Every stretch an app in [packages] was on screen during the last [days] days including today.
     * Empty when usage access is not granted.
     */
    fun loadSessions(packages: Set<String>, days: Int): List<Session> {
        if (!hasAccess()) return emptyList()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val start = LocalDate.now(zone).minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

        val manager = context.getSystemService(UsageStatsManager::class.java)
        val raw = manager.queryEvents(start, now) ?: return emptyList()
        val events = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, ACTIVITY_STOPPED -> UsageEventType.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.SCREEN_OFF
                else -> continue
            }
            // Screen-off events belong to the system, not to an app, so they skip the package filter.
            if (type != UsageEventType.SCREEN_OFF && e.packageName !in packages) continue
            events += UsageEvent(e.packageName, e.className.orEmpty(), type, e.timeStamp)
        }
        return SessionBuilder.build(events, now)
    }

    private companion object {
        const val ACTIVITY_STOPPED = 23 // UsageEvents.Event.ACTIVITY_STOPPED, API 29
    }
}

/** What the screens show. [sessions] is empty and [hasAccess] false until the user allows usage access. */
data class UsageData(
    val hasAccess: Boolean = false,
    val loaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Real stats for one day, counting only [packages] (everything loaded when null). */
    fun day(date: LocalDate, packages: Set<String>? = null): DayStats = DayStats.of(sessions, date, zone, packages)

    fun msOn(packages: Set<String>, date: LocalDate): Long = day(date, packages).totalMs
}
