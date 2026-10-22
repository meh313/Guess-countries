package com.example.progress

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Coming up line against the Due counts: every review is in exactly one of the two. */
class DueForecastTest {

    private val paris: TimeZone = TimeZone.getTimeZone("Europe/Paris")
    private val codes = setOf("FR", "DE", "JP", "IT", "BR", "ES")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun reviewed(dueAt: Long) = SkillState(score = 40, box = 2, dueAt = dueAt, reviews = 2, correct = 1)

    @Test
    fun aReviewDueThisEvening_isDueToday_notComingUp() {
        val morning = at(2026, 10, 20, 9)
        val flags = mapOf(
            "FR" to reviewed(at(2026, 10, 20, 19)),        // this evening
            "DE" to reviewed(at(2026, 10, 21, 0)),         // tomorrow, from midnight
            "JP" to reviewed(at(2026, 10, 21, 23, 59)),    // tomorrow, last minute
            "IT" to reviewed(at(2026, 10, 27, 23, 59)),    // the seventh day from tomorrow
            "BR" to reviewed(at(2026, 10, 28, 0))          // beyond the week
        )
        val forecast = DueForecast.of(mapOf(Skill.FLAG to flags), codes, morning, paris)
        assertEquals(DueForecast(tomorrow = 2, week = 3), forecast)
        assertEquals(listOf("FR"), flags.filterValues { SkillMastery.isDue(it, morning, paris) }.keys.toList())
    }

    @Test
    fun everyReviewIsDueOrComingUpOrLater_neverInBetween() {
        val now = at(2026, 10, 25, 8) // the 25-hour day the clocks go back
        val hour = 3_600_000L
        for (h in 0L..(9 * 24L)) {
            val state = reviewed(now + h * hour)
            val due = SkillMastery.isDue(state, now, paris)
            val week = DueForecast.of(mapOf(Skill.CAPITAL to mapOf("FR" to state)), codes, now, paris).week == 1
            val later = state.dueAt >= DueForecast.startOfDay(now, paris, daysAhead = 8)
            assertEquals("$h hours on: in exactly one place", 1, listOf(due, week, later).count { it })
        }
    }

    @Test
    fun theContinentSkill_isNeverComingUp() {
        val now = at(2026, 10, 20, 9)
        val tomorrow = mapOf("FR" to reviewed(at(2026, 10, 21, 12)))
        assertTrue(DueForecast.of(mapOf(Skill.CONTINENT to tomorrow), codes, now, paris).isEmpty)
        assertEquals(1, DueForecast.of(mapOf(Skill.LOCATION to tomorrow), codes, now, paris).tomorrow)
    }
}
