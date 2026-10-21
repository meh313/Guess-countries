package com.example.widget

import com.example.data.local.SkillProgressEntity
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Berlin")
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L // 2025-10-09 in Berlin
    private val sovereign = setOf("FR", "DE", "JP")

    private fun row(code: String, skill: Skill, dueAt: Long, reviews: Int = 2) =
        SkillProgressEntity.of(code, skill, SkillState(score = 40, box = 2, dueAt = dueAt, reviews = reviews, correct = 1))

    @Test
    fun dueCountsEveryOverdueReviewOfASovereignCountry_perSkill() {
        val skills = listOf(
            row("FR", Skill.FLAG, dueAt = now - day),          // due
            row("FR", Skill.CAPITAL, dueAt = now - 1),         // due: a second skill of the same country counts
            row("DE", Skill.FLAG, dueAt = now + day),          // not yet
            row("JP", Skill.LOCATION, dueAt = now - day),      // due
            row("AQ", Skill.FLAG, dueAt = now - day),          // not a country
            row("DE", Skill.CAPITAL, dueAt = now - day, reviews = 0) // never reviewed: not a review that is due
        )

        val state = WidgetState.of(skills, emptyList(), sovereign, goal = 10, now = now, zone = zone)

        assertEquals(3, state.due)
        assertEquals(0, state.streak)
        assertEquals(0, state.today)
        assertEquals(10, state.goal)
        assertFalse(state.goalMet)
    }

    @Test
    fun aReviewDueLaterToday_isDueNow_andTheContinentSkillHasNoReviews() {
        val midnight = com.example.progress.SkillMastery.endOfToday(now, zone)
        val skills = listOf(
            row("FR", Skill.FLAG, dueAt = now + 8 * 3_600_000L),   // this evening: due today
            row("DE", Skill.FLAG, dueAt = midnight),               // after midnight: tomorrow's
            row("JP", Skill.CONTINENT, dueAt = now - 30 * day),    // a continent pick: never due, however old
            row("FR", Skill.CONTINENT, dueAt = now - day)
        )

        assertEquals(1, WidgetState.of(skills, emptyList(), sovereign, goal = 10, now = now, zone = zone).due)
    }

    @Test
    fun streakAndTodayFollowThePracticeLogAndTheGoal() {
        val sessions = listOf(
            PracticeStreak.Session(now - 2 * day, 10),
            PracticeStreak.Session(now - day, 10),
            PracticeStreak.Session(now - 3_600_000L, 6)
        )

        val state = WidgetState.of(emptyList(), sessions, sovereign, goal = 5, now = now, zone = zone)

        assertEquals(PracticeStreak.summary(sessions, 5, now, zone).current, state.streak)
        assertEquals(6, state.today)
        assertEquals(5, state.goal)
        assertTrue(state.goalMet)
    }
}
