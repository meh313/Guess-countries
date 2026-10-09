package com.example.progress

import com.example.progress.PracticeStreak.Kind
import com.example.progress.PracticeStreak.Outcome
import com.example.progress.PracticeStreak.Summary
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PracticeStreakTest {

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")

    /** A moment on the Berlin wall clock. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()

    /** Monday 5 October 2026, mid-afternoon in Berlin. */
    private val now = at(2026, 10, 5, 15)

    private fun daysBefore(n: Int, hour: Int = 9) =
        ZonedDateTime.of(2026, 10, 5, hour, 0, 0, 0, ZoneId.of("Europe/Berlin")).minusDays(n.toLong()).toInstant().toEpochMilli()

    private fun summary(vararg timestamps: Long, at: Long = now) = PracticeStreak.summary(timestamps.toList(), at, berlin)

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

    @Test
    fun noQuizzes_noStreak() {
        assertEquals(Summary(0, 0, false), summary())
    }

    @Test
    fun aQuizToday_isAStreakOfOne() {
        assertEquals(Summary(1, 1, true), summary(daysBefore(0)))
    }

    @Test
    fun todayAndYesterday_isTwo() {
        assertEquals(Summary(2, 2, true), summary(daysBefore(0), daysBefore(1)))
    }

    @Test
    fun onlyYesterday_keepsTheStreakAliveUntilTodayEnds() {
        assertEquals(Summary(1, 1, false), summary(daysBefore(1)))
        assertEquals(Summary(3, 3, false), summary(daysBefore(1), daysBefore(2), daysBefore(3)))
    }

    @Test
    fun twoDaysAgo_hasBrokenTheStreakButNotTheBest() {
        assertEquals(Summary(0, 1, false), summary(daysBefore(2)))
    }

    @Test
    fun gapsSplitRuns_andTheBestRunIsRemembered() {
        // Today, yesterday, the day before: three. A gap. Then four days in a row, a week ago.
        val s = summary(
            daysBefore(0), daysBefore(1), daysBefore(2),
            daysBefore(4), daysBefore(5), daysBefore(6), daysBefore(7)
        )
        assertEquals(Summary(3, 4, true), s)
    }

    @Test
    fun severalQuizzesOnOneDay_countOnce() {
        assertEquals(Summary(1, 1, true), summary(daysBefore(0, 8), daysBefore(0, 12), daysBefore(0, 14)))
        assertEquals(Summary(2, 2, true), summary(daysBefore(0, 8), daysBefore(0, 12), daysBefore(1, 7), daysBefore(1, 23)))
    }

    @Test
    fun theOrderOfTheQuizzesDoesNotMatter() {
        val stamps = listOf(daysBefore(3), daysBefore(0), daysBefore(1), daysBefore(6), daysBefore(2))
        val expected = PracticeStreak.summary(stamps, now, berlin)
        assertEquals(Summary(4, 4, true), expected)
        assertEquals(expected, PracticeStreak.summary(stamps.reversed(), now, berlin))
    }

    @Test
    fun theLastSecondOfADayAndTheFirstOfTheNextAreConsecutiveDays() {
        val s = PracticeStreak.summary(listOf(at(2026, 10, 4, 23, 59), at(2026, 10, 5, 0, 0)), now, berlin)
        assertEquals(Summary(2, 2, true), s)
    }

    @Test
    fun theDayTheClocksGoForward_isStillOneDay() {
        // 29 March 2026 has 23 hours in Berlin. A quiz in its first minutes and one the evening before are consecutive days.
        val timestamps = listOf(at(2026, 3, 28, 23, 30), at(2026, 3, 29, 0, 30), at(2026, 3, 30, 0, 10))
        val s = PracticeStreak.summary(timestamps, at(2026, 3, 30, 12), berlin)
        assertEquals(Summary(3, 3, true), s)
    }

    @Test
    fun theDayTheClocksGoBack_isStillOneDay() {
        // 25 October 2026 has 25 hours in Berlin.
        val timestamps = listOf(at(2026, 10, 24, 23, 30), at(2026, 10, 25, 12), at(2026, 10, 26, 0, 30))
        val s = PracticeStreak.summary(timestamps, at(2026, 10, 26, 9), berlin)
        assertEquals(Summary(3, 3, true), s)
    }

    @Test
    fun aWholeStreakCountsThroughADaylightSavingChange() {
        val timestamps = (24..31).map { at(2026, 3, it, 20) } // 24 to 31 March, across the change on the 29th
        val s = PracticeStreak.summary(timestamps, at(2026, 3, 31, 21), berlin)
        assertEquals(Summary(8, 8, true), s)
    }

    @Test
    fun aQuizDatedAfterNow_countsForNothing() {
        assertEquals(Summary(1, 1, true), summary(daysBefore(0), at(2026, 10, 9)))
        assertEquals(Summary(0, 0, false), summary(at(2026, 10, 9)))
    }

    @Test
    fun theSameQuizzesGiveADifferentStreakInAnotherZone() {
        // 23:30 UTC on 4 October is already the 5th in Berlin: with 4 and 5 October (Berlin) it is two days there, one in UTC.
        val lateOnThe4th = 1_791_156_600_000L // 2026-10-04T23:30:00Z, 01:30 on the 5th in Berlin
        val morningOfThe5th = at(2026, 10, 5, 9)
        assertEquals(Summary(1, 1, true), PracticeStreak.summary(listOf(lateOnThe4th, morningOfThe5th), now, berlin))
        assertEquals(Summary(2, 2, true), PracticeStreak.summary(listOf(lateOnThe4th, morningOfThe5th), now, utc))
    }

    // --- What the next finished quiz does -------------------------------------------------------------------------

    private fun outcome(vararg previous: Long) = PracticeStreak.outcome(previous.toList(), now, berlin)

    @Test
    fun theFirstQuizEver_startsAStreak() {
        assertEquals(Outcome(Kind.STARTED, 1), outcome())
    }

    @Test
    fun aQuizAfterAGap_startsAStreakAgain() {
        assertEquals(Outcome(Kind.STARTED, 1), outcome(daysBefore(2), daysBefore(3), daysBefore(4)))
    }

    @Test
    fun aQuizTheDayAfterOne_keepsTheStreak() {
        assertEquals(Outcome(Kind.KEPT, 2), outcome(daysBefore(1)))
        assertEquals(Outcome(Kind.KEPT, 4), outcome(daysBefore(1), daysBefore(2), daysBefore(3)))
    }

    @Test
    fun aSecondQuizToday_isAlreadyCounted() {
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 1), outcome(daysBefore(0, 8)))
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 3), outcome(daysBefore(0, 8), daysBefore(1), daysBefore(2)))
    }
}
