package com.example.progress

import com.example.progress.GoalHistory.Replaced
import com.example.progress.PracticeStreak.Kind
import com.example.progress.PracticeStreak.Outcome
import com.example.progress.PracticeStreak.Session
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The goal in force on each day, and the streak judging every past day by its own goal. */
class GoalHistoryTest {

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val dayMillis = SkillMastery.DAY_MILLIS

    /** A moment on the Berlin wall clock in October 2026. */
    private fun at(day: Int, hour: Int = 12, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()

    private fun dayOf(day: Int) = PracticeStreak.localDay(at(day), berlin)

    /** Thursday 29 October 2026, mid-afternoon. */
    private val now = at(29, 15)

    /** A session of [items] at 09:00 on each of the [days] of October. */
    private fun daily(days: IntRange, items: Int) = days.map { Session(at(it, 9), items) }

    @Test
    fun eachDayIsJudgedByTheGoalInForceAtItsEnd() {
        // 5 until the 10th at 20:00, then 20 until the 20th at 08:00, then 10.
        val goals = GoalHistory(10, listOf(Replaced(5, at(10, 20)), Replaced(20, at(20, 8))))
        assertEquals(5, goals.goalOn(dayOf(1), berlin))
        assertEquals(5, goals.goalOn(dayOf(9), berlin))
        // The day of a change already follows the new goal.
        assertEquals(20, goals.goalOn(dayOf(10), berlin))
        assertEquals(20, goals.goalOn(dayOf(19), berlin))
        assertEquals(10, goals.goalOn(dayOf(20), berlin))
        assertEquals(10, goals.goalOn(dayOf(29), berlin))
    }

    @Test
    fun raisingTheGoal_keepsTheStreakEarnedUnderTheLowerOne() {
        // Four weeks of five-item sessions under a goal of 5, then the goal goes up to 10 this morning.
        val sessions = daily(1..28, items = 5)
        val goals = GoalHistory(5).changedTo(10, at(29, 8), berlin)

        val summary = PracticeStreak.summary(sessions, goals, now, berlin)

        // Today is still open under the new goal, so the run up to yesterday stands.
        assertEquals(PracticeStreak.Summary(current = 28, best = 28, practicedToday = false), summary)
        // Judged by today's goal alone, as before, every one of those days would be lost.
        assertEquals(PracticeStreak.NONE, PracticeStreak.summary(sessions, 10, now, berlin))
        // Five items today are not enough for the new goal; ten are, and keep the streak.
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), PracticeStreak.outcome(sessions, Session(now, 5), goals, now, berlin))
        assertEquals(Outcome(Kind.KEPT, 29), PracticeStreak.outcome(sessions, Session(now, 10), goals, now, berlin))
    }

    @Test
    fun loweringTheGoal_doesNotCountTheDaysThatFellShortOfTheHigherOne() {
        val sessions = daily(20..28, items = 5)
        val goals = GoalHistory(10).changedTo(5, at(29, 8), berlin)

        assertEquals(PracticeStreak.NONE, PracticeStreak.summary(sessions, goals, now, berlin))
        // Today follows the new goal at once.
        val today = sessions + Session(at(29, 10), 5)
        assertEquals(PracticeStreak.Summary(current = 1, best = 1, practicedToday = true), PracticeStreak.summary(today, goals, now, berlin))
    }

    @Test
    fun changingTheGoalBackAndForth_givesTheStreakBack() {
        val sessions = daily(1..28, items = 5)
        val goals = GoalHistory(5).changedTo(20, at(29, 8), berlin).changedTo(5, at(29, 9), berlin)
        assertEquals(PracticeStreak.Summary(current = 28, best = 28, practicedToday = false), PracticeStreak.summary(sessions, goals, now, berlin))
    }

    @Test
    fun pickingTheGoalInForce_changesNothing() {
        val goals = GoalHistory(10, listOf(Replaced(5, at(3))))
        assertSame(goals, goals.changedTo(10, now, berlin))
        assertEquals(GoalHistory(20, listOf(Replaced(5, at(3)), Replaced(10, now))), goals.changedTo(20, now, berlin))
    }

    @Test
    fun onlyTheLatestChangesAreKept() {
        // One change a day, at noon UTC on days 1, 2, 3 ... of 1970.
        val utc = TimeZone.getTimeZone("UTC")
        var goals = GoalHistory(5)
        repeat(GoalHistory.MAX_CHANGES + 6) { goals = goals.changedTo(if (goals.current == 5) 10 else 5, (it + 1) * dayMillis + dayMillis / 2, utc) }
        assertEquals(GoalHistory.MAX_CHANGES, goals.earlier.size)
        assertEquals(7 * dayMillis + dayMillis / 2, goals.earlier.first().until)
    }

    @Test
    fun manyChangesInOneDay_keepOnlyTheGoalTheDayBeganWith() {
        // The goal went from 5 to 10 on the 9th; today the buttons are tapped forty times, ending on 5.
        var goals = GoalHistory(5).changedTo(10, at(9), berlin)
        repeat(40) { goals = goals.changedTo(if (it % 2 == 0) 20 else 5, at(29, 8, it), berlin) }

        assertEquals(GoalHistory(5, listOf(Replaced(5, at(9)), Replaced(10, at(29, 8, 0)))), goals)
        // The days before the 9th keep their 5, the 9th to yesterday their 10; today follows the last tap.
        assertEquals(5, goals.goalOn(dayOf(8), berlin))
        assertEquals(10, goals.goalOn(dayOf(9), berlin))
        assertEquals(10, goals.goalOn(dayOf(28), berlin))
        assertEquals(5, goals.goalOn(dayOf(29), berlin))
        // Tomorrow's first change is a change of its own.
        assertEquals(listOf(Replaced(5, at(9)), Replaced(10, at(29, 8, 0)), Replaced(5, at(30, 7))), goals.changedTo(20, at(30, 7), berlin).earlier)
    }

    @Test
    fun goingBackTheSameDayToTheGoalItBeganWith_leavesNoChange() {
        val goals = GoalHistory(10, listOf(Replaced(5, at(3))))
        assertEquals(goals, goals.changedTo(20, at(29, 8), berlin).changedTo(5, at(29, 9), berlin).changedTo(10, at(29, 10), berlin))
    }

    @Test
    fun theDayOfAChange_isTheLocalOne() {
        // 23:50 on the 28th and 00:30 on the 29th in Berlin: two days there, one in UTC.
        val goals = GoalHistory(10).changedTo(20, at(28, 23, 50), berlin).changedTo(5, at(29, 0, 30), berlin)
        assertEquals(listOf(Replaced(10, at(28, 23, 50)), Replaced(20, at(29, 0, 30))), goals.earlier)
        val utc = TimeZone.getTimeZone("UTC")
        val inUtc = GoalHistory(10).changedTo(20, at(28, 23, 50), utc).changedTo(5, at(29, 0, 30), utc)
        assertEquals(listOf(Replaced(10, at(28, 23, 50))), inUtc.earlier)
    }

    @Test
    fun theSettingsText_readsBackAsTheSameHistory() {
        val goals = GoalHistory(5).changedTo(20, at(3), berlin).changedTo(10, at(17, 21), berlin)
        assertEquals("5@${at(3)},20@${at(17, 21)}", goals.encodeEarlier())
        assertEquals(goals, GoalHistory.of(goals.current, goals.encodeEarlier()))
        assertEquals("", GoalHistory(10).encodeEarlier())
        assertEquals(GoalHistory(10), GoalHistory.of(10, ""))
    }

    @Test
    fun aDamagedSettingsText_losesOnlyTheBadParts_andIsPutInOrder() {
        val read = GoalHistory.of(10, "20@300, junk ,7@100,5@-4,5@200,10@,@5,5@100")
        assertEquals(GoalHistory(10, listOf(Replaced(5, 100), Replaced(5, 200), Replaced(20, 300))), read)
    }
}
