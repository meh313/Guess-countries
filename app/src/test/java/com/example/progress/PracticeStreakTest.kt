package com.example.progress

import com.example.progress.PracticeStreak.Kind
import com.example.progress.PracticeStreak.Outcome
import com.example.progress.PracticeStreak.Session
import com.example.progress.PracticeStreak.Summary
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeStreakTest {

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")

    /** The goal most tests run against. */
    private val goal = PracticeStreak.DEFAULT_GOAL

    /** A moment on the Berlin wall clock. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()

    /** Monday 5 October 2026, mid-afternoon in Berlin. */
    private val now = at(2026, 10, 5, 15)

    private fun daysBefore(n: Int, hour: Int = 9) =
        ZonedDateTime.of(2026, 10, 5, hour, 0, 0, 0, ZoneId.of("Europe/Berlin")).minusDays(n.toLong()).toInstant().toEpochMilli()

    /** A full quiz (ten questions) at [at]: one of these makes a day count whatever the goal. */
    private fun quiz(at: Long, items: Int = 10) = Session(at, items)

    private fun summary(vararg sessions: Session, goal: Int = this.goal, at: Long = now) =
        PracticeStreak.summary(sessions.toList(), goal, at, berlin)

    /** The summary over full quizzes at [timestamps]. */
    private fun summaryOf(vararg timestamps: Long, at: Long = now) = summary(*timestamps.map { quiz(it) }.toTypedArray(), at = at)

    @Test
    fun localDay_isTheCalendarDayOnTheZonesOwnClock() {
        // 15:00 on 5 October 2026 in Berlin, and day 20731 counting from 1 January 1970.
        assertEquals(20_731L, PracticeStreak.localDay(1_791_205_200_000L, berlin))
        assertEquals(20_731L, PracticeStreak.localDay(1_791_205_200_000L, utc))
    }

    @Test
    fun localDay_changesAtLocalMidnightNotAtUtcMidnight() {
        val beforeMidnight = 1_791_237_540_000L // 23:59 in Berlin
        val midnight = 1_791_237_600_000L // 00:00 the next day in Berlin
        assertEquals(20_731L, PracticeStreak.localDay(beforeMidnight, berlin))
        assertEquals(20_732L, PracticeStreak.localDay(midnight, berlin))
        assertEquals(20_731L, PracticeStreak.localDay(midnight - 1, berlin))
    }

    @Test
    fun theSameMomentIsOnDifferentDaysInDifferentZones() {
        val lateInUtc = 1_791_243_000_000L // 23:30 UTC on 5 October, 01:30 on 6 October in Berlin
        assertEquals(20_731L, PracticeStreak.localDay(lateInUtc, utc))
        assertEquals(20_732L, PracticeStreak.localDay(lateInUtc, berlin))
        assertNotEquals(PracticeStreak.localDay(lateInUtc, utc), PracticeStreak.localDay(lateInUtc, berlin))
    }

    // --- The daily goal -------------------------------------------------------------------------------------------

    @Test
    fun aDayCounts_whenSessionsOfAtLeastFiveItemsAddUpToTheGoal() {
        assertTrue(PracticeStreak.dayCounts(listOf(5, 5), goal = 10))
        assertTrue(PracticeStreak.dayCounts(listOf(5, 5, 5, 5), goal = 20))
        assertFalse(PracticeStreak.dayCounts(listOf(5), goal = 10))
        assertFalse(PracticeStreak.dayCounts(listOf(5, 4, 4, 4), goal = 10))
        assertTrue(PracticeStreak.dayCounts(listOf(7, 6), goal = 10))
    }

    @Test
    fun aSingleSessionOfTenItems_countsWhateverTheGoal() {
        assertTrue(PracticeStreak.dayCounts(listOf(10), goal = 20))
        assertTrue(PracticeStreak.dayCounts(listOf(4, 10), goal = 20))
        assertFalse(PracticeStreak.dayCounts(listOf(9), goal = 20))
    }

    @Test
    fun sessionsUnderFiveItems_neverCount() {
        // Four-question quizzes, however many, and a blitz with no answer.
        assertFalse(PracticeStreak.dayCounts(listOf(4, 4, 4, 4, 4, 4), goal = 5))
        assertFalse(PracticeStreak.dayCounts(listOf(0), goal = 5))
        assertEquals(0, PracticeStreak.countedItems(listOf(4, 4, 0)))
        assertEquals(12, PracticeStreak.countedItems(listOf(4, 7, 5, 0)))
    }

    @Test
    fun goalMetByTwoSessionsOfFive_isAPractisedDay() {
        assertEquals(Summary(1, 1, true), summary(quiz(daysBefore(0, 8), 5), quiz(daysBefore(0, 12), 5)))
        assertEquals(Summary(0, 0, false), summary(quiz(daysBefore(0, 8), 5)))
        // Against a goal of 5, the one session is enough.
        assertEquals(Summary(1, 1, true), summary(quiz(daysBefore(0, 8), 5), goal = 5))
    }

    @Test
    fun oneSessionOfTen_isAPractisedDayAgainstAGoalOfTwenty() {
        assertEquals(Summary(1, 1, true), summary(quiz(daysBefore(0), 10), goal = 20))
        assertEquals(Summary(0, 0, false), summary(quiz(daysBefore(0), 9), goal = 20))
    }

    @Test
    fun fourQuestionQuizzesAndAnEmptyBlitz_countForNothing() {
        assertEquals(Summary(0, 0, false), summary(quiz(daysBefore(0, 8), 4), quiz(daysBefore(0, 9), 4), quiz(daysBefore(0, 10), 4), goal = 5))
        assertEquals(Summary(0, 0, false), summary(quiz(daysBefore(0), 0), goal = 5))
        // They do not break a run either: the day before and after still join up only when each counts on its own.
        assertEquals(Summary(1, 1, false), summary(quiz(daysBefore(1)), quiz(daysBefore(0), 4)))
    }

    @Test
    fun itemsToday_sumsTodaysSessionsOfAtLeastFiveItems() {
        val sessions = listOf(quiz(daysBefore(0, 8), 5), quiz(daysBefore(0, 9), 4), quiz(daysBefore(0, 11), 7), quiz(daysBefore(1), 10))
        assertEquals(12, PracticeStreak.itemsToday(sessions, now, berlin))
        assertEquals(0, PracticeStreak.itemsToday(emptyList(), now, berlin))
    }

    // --- Runs of days ---------------------------------------------------------------------------------------------

    @Test
    fun noQuizzes_noStreak() {
        assertEquals(Summary(0, 0, false), summaryOf())
    }

    @Test
    fun aQuizToday_isAStreakOfOne() {
        assertEquals(Summary(1, 1, true), summaryOf(daysBefore(0)))
    }

    @Test
    fun todayAndYesterday_isTwo() {
        assertEquals(Summary(2, 2, true), summaryOf(daysBefore(0), daysBefore(1)))
    }

    @Test
    fun onlyYesterday_keepsTheStreakAliveUntilTodayEnds() {
        assertEquals(Summary(1, 1, false), summaryOf(daysBefore(1)))
        assertEquals(Summary(3, 3, false), summaryOf(daysBefore(1), daysBefore(2), daysBefore(3)))
    }

    @Test
    fun twoDaysAgo_hasBrokenTheStreakButNotTheBest() {
        assertEquals(Summary(0, 1, false), summaryOf(daysBefore(2)))
    }

    @Test
    fun gapsSplitRuns_andTheBestRunIsRemembered() {
        // Today, yesterday, the day before: three. A gap. Then four days in a row, a week ago.
        val s = summaryOf(
            daysBefore(0), daysBefore(1), daysBefore(2),
            daysBefore(4), daysBefore(5), daysBefore(6), daysBefore(7)
        )
        assertEquals(Summary(3, 4, true), s)
    }

    @Test
    fun severalQuizzesOnOneDay_countOnce() {
        assertEquals(Summary(1, 1, true), summaryOf(daysBefore(0, 8), daysBefore(0, 12), daysBefore(0, 14)))
        assertEquals(Summary(2, 2, true), summaryOf(daysBefore(0, 8), daysBefore(0, 12), daysBefore(1, 7), daysBefore(1, 23)))
    }

    @Test
    fun theOrderOfTheQuizzesDoesNotMatter() {
        val sessions = listOf(daysBefore(3), daysBefore(0), daysBefore(1), daysBefore(6), daysBefore(2)).map { quiz(it) }
        val expected = PracticeStreak.summary(sessions, goal, now, berlin)
        assertEquals(Summary(4, 4, true), expected)
        assertEquals(expected, PracticeStreak.summary(sessions.reversed(), goal, now, berlin))
    }

    @Test
    fun theLastSecondOfADayAndTheFirstOfTheNextAreConsecutiveDays() {
        val s = summaryOf(at(2026, 10, 4, 23, 59), at(2026, 10, 5, 0, 0))
        assertEquals(Summary(2, 2, true), s)
    }

    @Test
    fun theDayTheClocksGoForward_isStillOneDay() {
        // 29 March 2026 has 23 hours in Berlin. A quiz in its first minutes and one the evening before are consecutive days.
        val s = summaryOf(at(2026, 3, 28, 23, 30), at(2026, 3, 29, 0, 30), at(2026, 3, 30, 0, 10), at = at(2026, 3, 30, 12))
        assertEquals(Summary(3, 3, true), s)
    }

    @Test
    fun theDayTheClocksGoBack_isStillOneDay() {
        // 25 October 2026 has 25 hours in Berlin.
        val s = summaryOf(at(2026, 10, 24, 23, 30), at(2026, 10, 25, 12), at(2026, 10, 26, 0, 30), at = at(2026, 10, 26, 9))
        assertEquals(Summary(3, 3, true), s)
    }

    @Test
    fun aWholeStreakCountsThroughADaylightSavingChange() {
        val timestamps = (24..31).map { at(2026, 3, it, 20) } // 24 to 31 March, across the change on the 29th
        val s = summaryOf(*timestamps.toLongArray(), at = at(2026, 3, 31, 21))
        assertEquals(Summary(8, 8, true), s)
    }

    @Test
    fun aQuizDatedAfterNow_countsForNothing() {
        assertEquals(Summary(1, 1, true), summaryOf(daysBefore(0), at(2026, 10, 9)))
        assertEquals(Summary(0, 0, false), summaryOf(at(2026, 10, 9)))
    }

    @Test
    fun theSameQuizzesGiveADifferentStreakInAnotherZone() {
        // 23:30 UTC on 4 October is already the 5th in Berlin: with 4 and 5 October (Berlin) it is two days there, one in UTC.
        val lateOnThe4th = 1_791_156_600_000L // 2026-10-04T23:30:00Z, 01:30 on the 5th in Berlin
        val morningOfThe5th = at(2026, 10, 5, 9)
        val sessions = listOf(quiz(lateOnThe4th), quiz(morningOfThe5th))
        assertEquals(Summary(1, 1, true), PracticeStreak.summary(sessions, goal, now, berlin))
        assertEquals(Summary(2, 2, true), PracticeStreak.summary(sessions, goal, now, utc))
    }

    @Test
    fun twoHalfSessionsOnDifferentDays_doNotAddUp() {
        // Five items yesterday and five today: neither day reaches ten on its own.
        assertEquals(Summary(0, 0, false), summary(quiz(daysBefore(1), 5), quiz(daysBefore(0), 5)))
    }

    // --- What the next finished session does ----------------------------------------------------------------------

    private fun outcome(vararg previous: Session, latest: Session = quiz(now), goal: Int = this.goal) =
        PracticeStreak.outcome(previous.toList(), latest, goal, now, berlin)

    private fun outcomeOf(vararg previous: Long) = outcome(*previous.map { quiz(it) }.toTypedArray())

    @Test
    fun theFirstQuizEver_startsAStreak() {
        assertEquals(Outcome(Kind.STARTED, 1), outcomeOf())
    }

    @Test
    fun aQuizAfterAGap_startsAStreakAgain() {
        assertEquals(Outcome(Kind.STARTED, 1), outcomeOf(daysBefore(2), daysBefore(3), daysBefore(4)))
    }

    @Test
    fun aQuizTheDayAfterOne_keepsTheStreak() {
        assertEquals(Outcome(Kind.KEPT, 2), outcomeOf(daysBefore(1)))
        assertEquals(Outcome(Kind.KEPT, 4), outcomeOf(daysBefore(1), daysBefore(2), daysBefore(3)))
    }

    @Test
    fun aSecondQuizToday_isAlreadyCounted() {
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 1), outcomeOf(daysBefore(0, 8)))
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 3), outcomeOf(daysBefore(0, 8), daysBefore(1), daysBefore(2)))
    }

    @Test
    fun theSessionThatCompletesTheGoal_isTheOneThatStartsOrKeepsTheStreak() {
        // Five items this morning did not count; five more now bring the day to the goal.
        assertEquals(Outcome(Kind.STARTED, 1), outcome(quiz(daysBefore(0, 8), 5), latest = quiz(now, 5)))
        assertEquals(Outcome(Kind.KEPT, 2), outcome(quiz(daysBefore(1)), quiz(daysBefore(0, 8), 5), latest = quiz(now, 5)))
    }

    @Test
    fun aSessionThatLeavesTodayShortOfTheGoal_changesNothing() {
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), outcome(latest = quiz(now, 5)))
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), outcome(quiz(daysBefore(1)), latest = quiz(now, 4)))
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), outcome(latest = quiz(now, 0)))
        // Against a goal of 20, a nine-question quiz is short; a ten-question one is a day on its own.
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), outcome(latest = quiz(now, 9), goal = 20))
        assertEquals(Outcome(Kind.STARTED, 1), outcome(latest = quiz(now, 10), goal = 20))
    }
}
