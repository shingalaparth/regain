package app.focuslite.engine.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class SleepScheduleTest {
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    // wind-down 22:00, bed 23:00, wake 07:00, 30 minutes morning lock
    private val s = SleepSchedule(enabled = true)

    @Test
    fun `phases across the night`() {
        assertEquals(SleepPhase.NONE, s.phaseAt(at(21, 59)))
        assertEquals(SleepPhase.WIND_DOWN, s.phaseAt(at(22)))
        assertEquals(SleepPhase.WIND_DOWN, s.phaseAt(at(22, 59)))
        assertEquals(SleepPhase.OVERNIGHT, s.phaseAt(at(23)))
        assertEquals(SleepPhase.OVERNIGHT, s.phaseAt(at(0)))
        assertEquals(SleepPhase.OVERNIGHT, s.phaseAt(at(6, 59)))
        assertEquals(SleepPhase.MORNING, s.phaseAt(at(7)))
        assertEquals(SleepPhase.MORNING, s.phaseAt(at(7, 29)))
        assertEquals(SleepPhase.NONE, s.phaseAt(at(7, 30)))
        assertEquals(SleepPhase.NONE, s.phaseAt(at(12)))
    }

    @Test
    fun `a disabled schedule is always none`() {
        assertEquals(SleepPhase.NONE, s.copy(enabled = false).phaseAt(at(2)))
    }

    @Test
    fun `a routine that stays inside one day works too`() {
        val nap = SleepSchedule(enabled = true, windDownStartMin = at(13), bedtimeMin = at(13, 30), wakeMin = at(15), morningLockMin = 0)
        assertEquals(SleepPhase.WIND_DOWN, nap.phaseAt(at(13, 10)))
        assertEquals(SleepPhase.OVERNIGHT, nap.phaseAt(at(14)))
        assertEquals(SleepPhase.NONE, nap.phaseAt(at(15)))
    }

    @Test
    fun `minutes outside one day wrap`() {
        assertEquals(SleepPhase.OVERNIGHT, s.phaseAt(at(24) + at(1)))
        assertEquals(SleepPhase.OVERNIGHT, s.phaseAt(-at(1)))
    }
}
