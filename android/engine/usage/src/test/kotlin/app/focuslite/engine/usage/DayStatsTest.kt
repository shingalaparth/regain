package app.focuslite.engine.usage

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayStatsTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 22)
    private val midnight = day.atStartOfDay(utc).toInstant().toEpochMilli()
    private val min = 60_000L
    private val hour = 60 * min
    private fun at(h: Int, m: Int = 0) = midnight + h * hour + m * min

    @Test
    fun `an empty day has zeros and no first or last use`() {
        val s = DayStats.of(emptyList(), day, utc)
        assertEquals(0, s.totalMs)
        assertEquals(24, s.perHour.size)
        assertNull(s.firstUseMs)
        assertNull(s.lastUseMs)
    }

    @Test
    fun `totals, opens, longest and first and last use`() {
        val s = DayStats.of(
            listOf(
                Session("ig", at(9), at(9, 20)),
                Session("ig", at(13), at(13, 5)),
                Session("yt", at(20), at(20, 45)),
            ),
            day, utc,
        )
        assertEquals(25 * min, s.perPackage["ig"])
        assertEquals(45 * min, s.perPackage["yt"])
        assertEquals(70 * min, s.totalMs)
        assertEquals(mapOf("ig" to 2, "yt" to 1), s.opens)
        assertEquals(3, s.totalOpens)
        assertEquals(45 * min, s.longestSessionMs)
        assertEquals(at(9), s.firstUseMs)
        assertEquals(at(20, 45), s.lastUseMs)
    }

    @Test
    fun `a session over an hour boundary is split between the hours`() {
        val s = DayStats.of(listOf(Session("ig", at(9, 50), at(10, 20))), day, utc)
        assertEquals(10 * min, s.perHour[9])
        assertEquals(20 * min, s.perHour[10])
        assertEquals(30 * min, s.perHour.sum())
    }

    @Test
    fun `a session over midnight only counts its part of the day, and is not an open of the new day`() {
        val s = DayStats.of(listOf(Session("ig", midnight - 10 * min, midnight + 20 * min)), day, utc)
        assertEquals(20 * min, s.totalMs)
        assertEquals(0, s.totalOpens) // it was opened the day before
        assertEquals(20 * min, s.perHour[0])
    }

    @Test
    fun `only the chosen packages count`() {
        val s = DayStats.of(
            listOf(Session("ig", at(9), at(9, 10)), Session("bank", at(9, 10), at(9, 40))),
            day, utc, packages = setOf("ig"),
        )
        assertEquals(10 * min, s.totalMs)
        assertEquals(setOf("ig"), s.perPackage.keys)
    }

    @Test
    fun `streak counts days within the goal back from today`() {
        val goal = 2 * hour
        val totals = mapOf(
            day to 1 * hour,
            day.minusDays(1) to 90 * min,
            day.minusDays(2) to 3 * hour, // over the goal ends the run
            day.minusDays(3) to 10 * min,
        )
        assertEquals(2, Goals.streak(totals, goal, day, oldest = day.minusDays(6)))
    }

    @Test
    fun `today over the goal means no streak yet`() {
        assertEquals(0, Goals.streak(mapOf(day to 3 * hour), 2 * hour, day, oldest = day.minusDays(6)))
    }

    @Test
    fun `days without any use count and the streak stops at the oldest day we know`() {
        assertEquals(7, Goals.streak(emptyMap(), 2 * hour, day, oldest = day.minusDays(6)))
    }
}
