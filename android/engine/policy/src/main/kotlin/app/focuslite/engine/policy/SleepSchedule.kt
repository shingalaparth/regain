package app.focuslite.engine.policy

/** Where the clock sits relative to the sleep routine. */
enum class SleepPhase {
    NONE,

    /** Before bed: no short video at all, whatever the settings say. */
    WIND_DOWN,

    /** Bedtime until wake-up: the covered apps are locked completely. */
    OVERNIGHT,

    /** For a while after waking: the covered apps stay locked. */
    MORNING,
}

/**
 * Times are minutes after midnight. The routine may cross midnight (wind-down 22:00, wake 07:00).
 * Phases: [windDownStart, bedtime) wind-down, [bedtime, wake) overnight, [wake, wake + morningLock) morning.
 */
data class SleepSchedule(
    val enabled: Boolean = false,
    val windDownStartMin: Int = 22 * 60,
    val bedtimeMin: Int = 23 * 60,
    val wakeMin: Int = 7 * 60,
    val morningLockMin: Int = 30,
) {
    fun phaseAt(minuteOfDay: Int): SleepPhase {
        if (!enabled) return SleepPhase.NONE
        val m = Math.floorMod(minuteOfDay, DAY)
        return when {
            inRange(m, bedtimeMin, wakeMin) -> SleepPhase.OVERNIGHT
            inRange(m, windDownStartMin, bedtimeMin) -> SleepPhase.WIND_DOWN
            inRange(m, wakeMin, wakeMin + morningLockMin) -> SleepPhase.MORNING
            else -> SleepPhase.NONE
        }
    }

    /** True for [from, to) on a 24-hour clock that wraps at midnight. An empty range is never true. */
    private fun inRange(m: Int, from: Int, to: Int): Boolean {
        val a = Math.floorMod(from, DAY)
        val b = Math.floorMod(to, DAY)
        return when {
            a == b -> false
            a < b -> m in a until b
            else -> m >= a || m < b
        }
    }

    private companion object {
        const val DAY = 24 * 60
    }
}
