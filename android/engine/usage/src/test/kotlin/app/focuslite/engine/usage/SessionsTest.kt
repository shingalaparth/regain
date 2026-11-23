package app.focuslite.engine.usage

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionsTest {
    private val min = 60_000L
    private fun ev(pkg: String, type: UsageEventType, t: Long, act: String = "A") = UsageEvent(pkg, act, type, t)

    @Test
    fun `resume then pause is one session`() {
        val s = SessionBuilder.build(
            listOf(ev("ig", UsageEventType.RESUMED, 10 * min), ev("ig", UsageEventType.PAUSED, 25 * min)),
            nowMs = 100 * min,
        )
        assertEquals(listOf(Session("ig", 10 * min, 25 * min)), s)
    }

    @Test
    fun `switching activities inside one app does not split or double count`() {
        val s = SessionBuilder.build(
            listOf(
                ev("ig", UsageEventType.RESUMED, 0, "Main"),
                ev("ig", UsageEventType.RESUMED, 5 * min, "Chat"), // opened before Main paused
                ev("ig", UsageEventType.PAUSED, 5 * min + 10, "Main"),
                ev("ig", UsageEventType.PAUSED, 9 * min, "Chat"),
            ),
            nowMs = 100 * min,
        )
        assertEquals(listOf(Session("ig", 0, 9 * min)), s)
    }

    @Test
    fun `screen off closes every open app`() {
        val s = SessionBuilder.build(
            listOf(ev("yt", UsageEventType.RESUMED, 0), ev("yt", UsageEventType.SCREEN_OFF, 3 * min)),
            nowMs = 100 * min,
        )
        assertEquals(listOf(Session("yt", 0, 3 * min)), s)
    }

    @Test
    fun `an app still open at the end runs until now`() {
        val s = SessionBuilder.build(listOf(ev("ig", UsageEventType.RESUMED, 10 * min)), nowMs = 14 * min)
        assertEquals(listOf(Session("ig", 10 * min, 14 * min)), s)
    }

    @Test
    fun `a pause with no matching resume is ignored`() {
        val s = SessionBuilder.build(listOf(ev("ig", UsageEventType.PAUSED, 5 * min)), nowMs = 100 * min)
        assertEquals(emptyList(), s)
    }

    @Test
    fun `quick pause and resume inside one app counts as one visit`() {
        val s = SessionBuilder.build(
            listOf(
                ev("ig", UsageEventType.RESUMED, 0), ev("ig", UsageEventType.PAUSED, 2 * min),
                ev("ig", UsageEventType.RESUMED, 2 * min + 3_000), ev("ig", UsageEventType.PAUSED, 5 * min), // 3 s gap
                ev("ig", UsageEventType.RESUMED, 5 * min + 60_000), ev("ig", UsageEventType.PAUSED, 7 * min), // 1 min gap
            ),
            nowMs = 100 * min,
        )
        assertEquals(listOf(Session("ig", 0, 5 * min), Session("ig", 5 * min + 60_000, 7 * min)), s)
    }

    @Test
    fun `different apps get separate sessions`() {
        val s = SessionBuilder.build(
            listOf(
                ev("ig", UsageEventType.RESUMED, 0), ev("ig", UsageEventType.PAUSED, 2 * min),
                ev("yt", UsageEventType.RESUMED, 2 * min), ev("yt", UsageEventType.PAUSED, 7 * min),
            ),
            nowMs = 100 * min,
        )
        assertEquals(2, s.size)
        assertEquals(5 * min, s.single { it.packageName == "yt" }.durationMs)
    }

    @Test
    fun `a session over midnight is split between the two days`() {
        val zone = ZoneId.of("UTC")
        val midnight = LocalDate.of(2026, 11, 22).atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = DayTotals.perDay(
            listOf(Session("ig", midnight - 10 * min, midnight + 20 * min)),
            zone,
        )
        assertEquals(10 * min, totals[LocalDate.of(2026, 11, 21)]!!["ig"])
        assertEquals(20 * min, totals[LocalDate.of(2026, 11, 22)]!!["ig"])
    }

    @Test
    fun `day totals add several sessions of the same app`() {
        val zone = ZoneId.of("UTC")
        val noon = LocalDate.of(2026, 11, 22).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 60 * min
        val totals = DayTotals.perDay(
            listOf(Session("ig", noon, noon + 5 * min), Session("ig", noon + 60 * min, noon + 63 * min)),
            zone,
        )
        assertEquals(8 * min, totals[LocalDate.of(2026, 11, 22)]!!["ig"])
    }

    @Test
    fun `durations are formatted for people`() {
        assertEquals("0m", formatDuration(0))
        assertEquals("<1m", formatDuration(30_000))
        assertEquals("12m", formatDuration(12 * min))
        assertEquals("1h 05m", formatDuration(65 * min))
        assertEquals("2h 00m", formatDuration(120 * min))
    }
}
