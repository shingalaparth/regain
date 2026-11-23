package app.focuslite.engine.accessibility

import android.content.Context
import android.content.SharedPreferences
import app.focuslite.core.model.Surface
import app.focuslite.core.model.SurfaceMode
import app.focuslite.core.model.SurfaceSetting
import app.focuslite.engine.policy.PolicyConfig
import app.focuslite.engine.policy.SleepSchedule
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

private fun Context.prefs(name: String): SharedPreferences =
    applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

/** The user's per-surface choices (block, pause first, daily limit, allow), shared by the UI and the service. */
class PolicyPrefs(context: Context) {
    private val prefs = context.prefs("focuslite_policy")

    fun setting(surface: Surface): SurfaceSetting {
        val raw = prefs.getString("set:${surface.name}", null)
        if (raw != null) {
            val parts = raw.split(':')
            val mode = runCatching { SurfaceMode.valueOf(parts[0]) }.getOrNull()
            if (mode != null) return SurfaceSetting(mode, parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_LIMIT)
        }
        return PolicyConfig.DEFAULT_SETTINGS[surface] ?: SurfaceSetting(SurfaceMode.ALLOW)
    }

    fun setSetting(surface: Surface, setting: SurfaceSetting) {
        prefs.edit().putString("set:${surface.name}", "${setting.mode.name}:${setting.limitMinutes}").apply()
    }

    /** Defaults plus whatever the user changed. Cheap enough to call on every screen check. */
    fun config(): PolicyConfig = PolicyConfig(settings = Surface.entries.associateWith(::setting))

    companion object {
        const val DEFAULT_LIMIT = 15
    }
}

/** Timed states (Post Mode, focus, unlocks), the sleep routine, the daily goal and time-on-surface counters. */
class RegainState(context: Context) {
    private val prefs = context.prefs("focuslite_state")

    // ---- Post Mode ----
    var postModeUntil: Long
        get() = prefs.getLong("postModeUntil", 0)
        private set(v) = prefs.edit().putLong("postModeUntil", v).apply()

    fun startPostMode(minutes: Int, now: Long = System.currentTimeMillis()) { postModeUntil = now + minutes * 60_000L }
    fun endPostMode() { postModeUntil = 0 }

    // ---- Focus session ----
    var focusUntil: Long
        get() = prefs.getLong("focusUntil", 0)
        private set(v) = prefs.edit().putLong("focusUntil", v).apply()
    var focusStrict: Boolean
        get() = prefs.getBoolean("focusStrict", false)
        private set(v) = prefs.edit().putBoolean("focusStrict", v).apply()

    fun focusActive(now: Long = System.currentTimeMillis()) = focusUntil > now

    fun startFocus(minutes: Int, strict: Boolean, now: Long = System.currentTimeMillis()) {
        focusUntil = now + minutes * 60_000L
        focusStrict = strict
        endPostMode() // a focus session and Post Mode cannot overlap
    }

    /** Ends the session early. A strict session cannot be ended: returns false and changes nothing. */
    fun stopFocus(now: Long = System.currentTimeMillis()): Boolean {
        if (focusActive(now) && focusStrict) return false
        focusUntil = 0
        return true
    }

    // ---- Sleep routine ----
    fun sleepSchedule(): SleepSchedule = SleepSchedule(
        enabled = prefs.getBoolean("sleepOn", false),
        windDownStartMin = prefs.getInt("sleepWind", 22 * 60),
        bedtimeMin = prefs.getInt("sleepBed", 23 * 60),
        wakeMin = prefs.getInt("sleepWake", 7 * 60),
        morningLockMin = prefs.getInt("sleepMorning", 30),
    )

    fun setSleepSchedule(s: SleepSchedule) {
        prefs.edit()
            .putBoolean("sleepOn", s.enabled).putInt("sleepWind", s.windDownStartMin).putInt("sleepBed", s.bedtimeMin)
            .putInt("sleepWake", s.wakeMin).putInt("sleepMorning", s.morningLockMin).apply()
    }

    // ---- Show the live reel counter on top of the video ----
    var showCounterPill: Boolean
        get() = prefs.getBoolean("showCounterPill", true)
        set(v) = prefs.edit().putBoolean("showCounterPill", v).apply()

    // ---- Daily goal for time on the covered apps ----
    var goalMinutes: Int
        get() = prefs.getInt("goalMinutes", DEFAULT_GOAL_MINUTES)
        set(v) = prefs.edit().putInt("goalMinutes", v.coerceIn(10, 12 * 60)).apply()

    // ---- Unlocks granted by the pause gate ----
    fun unlockedUntil(surface: Surface): Long = prefs.getLong("unlock:${surface.name}", 0)
    fun setUnlockedUntil(surface: Surface, until: Long) = prefs.edit().putLong("unlock:${surface.name}", until).apply()

    // ---- Time spent on short-video surfaces, per day ----
    fun usedMs(surface: Surface, date: LocalDate = LocalDate.now()): Long = prefs.getLong("used:$date:${surface.name}", 0)
    fun addUsedMs(surface: Surface, deltaMs: Long, date: LocalDate = LocalDate.now()) {
        val key = "used:$date:${surface.name}"
        prefs.edit().putLong(key, prefs.getLong(key, 0) + deltaMs).apply()
    }

    fun shortVideoMs(date: LocalDate = LocalDate.now()): Long = Surface.entries.sumOf { usedMs(it, date) }

    // ---- Reel counter: short videos actually watched (never blocked ones), per surface per day ----
    fun reels(surface: Surface, date: LocalDate = LocalDate.now()): Int = prefs.getInt("reels:$date:${surface.name}", 0)

    fun addReels(surface: Surface, count: Int, date: LocalDate = LocalDate.now()) {
        val key = "reels:$date:${surface.name}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + count).apply()
    }

    /** Reels watched on [date], per surface (only surfaces with at least one). */
    fun reelsByDay(date: LocalDate = LocalDate.now()): Map<Surface, Int> =
        Surface.entries.associateWith { reels(it, date) }.filterValues { it > 0 }

    fun reelsTotal(date: LocalDate = LocalDate.now()): Int = reelsByDay(date).values.sum()

    // ---- Protection gaps (tamper alerts) ----
    var serviceStoppedAt: Long
        get() = prefs.getLong("serviceStoppedAt", 0)
        set(v) = prefs.edit().putLong("serviceStoppedAt", v).apply()

    companion object {
        const val DEFAULT_GOAL_MINUTES = 120
    }
}

data class UnlockEvent(val atMs: Long, val surface: Surface, val reason: String, val waitSeconds: Int)

/** Every time the pause gate was passed, with the reason given. The raw material for the weekly report. */
class UnlockLog(context: Context) {
    private val prefs = context.prefs("focuslite_unlocks")

    fun append(event: UnlockEvent) {
        val all = readAll().takeLast(MAX - 1) + event
        val arr = JSONArray()
        all.forEach {
            arr.put(JSONObject().put("at", it.atMs).put("s", it.surface.name).put("r", it.reason).put("w", it.waitSeconds))
        }
        prefs.edit().putString("log", arr.toString()).apply()
    }

    fun since(fromMs: Long): List<UnlockEvent> = readAll().filter { it.atMs >= fromMs }

    private fun readAll(): List<UnlockEvent> {
        val raw = prefs.getString("log", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val surface = runCatching { Surface.valueOf(o.getString("s")) }.getOrNull() ?: return@mapNotNull null
                UnlockEvent(o.getLong("at"), surface, o.getString("r"), o.optInt("w"))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX = 500
    }
}

/** Times the protection was off (service stopped, switched off, phone rebooted). */
data class ProtectionGap(val fromMs: Long, val toMs: Long)

class TamperLog(context: Context) {
    private val prefs = context.prefs("focuslite_tamper")

    fun append(gap: ProtectionGap) {
        val all = recent(MAX - 1) + gap
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("f", it.fromMs).put("t", it.toMs)) }
        prefs.edit().putString("gaps", arr.toString()).apply()
    }

    fun recent(n: Int): List<ProtectionGap> {
        val raw = prefs.getString("gaps", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> ProtectionGap(o.getLong("f"), o.getLong("t")) } }
        }.getOrDefault(emptyList()).takeLast(n)
    }

    private companion object {
        const val MAX = 50
    }
}
