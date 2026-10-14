package com.example.reminder

import com.example.progress.PracticeStreak
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")
    private val berlinId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0, nano: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, second, nano, berlinId).toInstant().toEpochMilli()

    private fun clock(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun inBerlin(millis: Long): ZonedDateTime = java.time.Instant.ofEpochMilli(millis).atZone(berlinId)

    // --- nextTrigger ----------------------------------------------------------------------------------------------

    @Test
    fun theTimeStillAheadToday_isToday() {
        val next = ReminderSchedule.nextTrigger(at(2026, 10, 5, 15), clock(19), berlin)

        assertEquals(at(2026, 10, 5, 19), next)
    }

    @Test
    fun theTimeAlreadyPassed_isTomorrow() {
        val next = ReminderSchedule.nextTrigger(at(2026, 10, 5, 20), clock(19), berlin)

        assertEquals(at(2026, 10, 6, 19), next)
    }

    @Test
    fun exactlyAtTheTime_isTomorrow_andOneMillisecondBeforeIsToday() {
        val due = at(2026, 10, 5, 19)

        assertEquals(at(2026, 10, 6, 19), ReminderSchedule.nextTrigger(due, clock(19), berlin))
        assertEquals(due, ReminderSchedule.nextTrigger(due - 1, clock(19), berlin))
    }

    @Test
    fun minutesAreKept() {
        assertEquals(at(2026, 10, 5, 7, 45), ReminderSchedule.nextTrigger(at(2026, 10, 5, 7, 44, 59), clock(7, 45), berlin))
        assertEquals(at(2026, 10, 6, 7, 45), ReminderSchedule.nextTrigger(at(2026, 10, 5, 7, 45, 0, 1_000_000), clock(7, 45), berlin))
    }

    @Test
    fun midnightAndTheLastMinuteOfTheDay() {
        assertEquals(at(2026, 10, 6, 0), ReminderSchedule.nextTrigger(at(2026, 10, 5, 15), 0, berlin))
        assertEquals(at(2026, 10, 5, 23, 59), ReminderSchedule.nextTrigger(at(2026, 10, 5, 15), 24 * 60 - 1, berlin))
    }

    @Test
    fun aMinuteOutsideTheDay_isHeldToTheDay() {
        assertEquals(at(2026, 10, 5, 23, 59), ReminderSchedule.nextTrigger(at(2026, 10, 5, 15), 5_000, berlin))
        assertEquals(at(2026, 10, 6, 0), ReminderSchedule.nextTrigger(at(2026, 10, 5, 15), -30, berlin))
    }

    @Test
    fun theSameMoment_hasADifferentTriggerInAnotherZone() {
        val now = at(2026, 10, 5, 15) // 13:00 UTC
        val inUtc = ReminderSchedule.nextTrigger(now, clock(19), utc)
        val inBerlinZone = ReminderSchedule.nextTrigger(now, clock(19), berlin)

        assertNotEquals(inUtc, inBerlinZone)
        assertEquals(Duration.ofHours(6), Duration.ofMillis(inUtc - now))
        assertEquals(Duration.ofHours(4), Duration.ofMillis(inBerlinZone - now))
    }

    @Test
    fun theDayTheClocksGoForward_isTwentyThreeHours() {
        // 29 March 2026: 02:00 became 03:00 in Berlin. 19:00 to 19:00 is one hour shorter.
        val now = at(2026, 3, 28, 19)

        val next = ReminderSchedule.nextTrigger(now, clock(19), berlin)

        assertEquals(at(2026, 3, 29, 19), next)
        assertEquals(Duration.ofHours(23), Duration.ofMillis(next - now))
    }

    @Test
    fun theDayTheClocksGoBack_isTwentyFiveHours() {
        // 25 October 2026: 03:00 became 02:00 in Berlin.
        val now = at(2026, 10, 24, 19)

        val next = ReminderSchedule.nextTrigger(now, clock(19), berlin)

        assertEquals(at(2026, 10, 25, 19), next)
        assertEquals(Duration.ofHours(25), Duration.ofMillis(next - now))
    }

    @Test
    fun onTheDayOfTheChangeItself_theTimeStillAheadIsToday() {
        val now = at(2026, 3, 29, 10)

        assertEquals(Duration.ofHours(9), Duration.ofMillis(ReminderSchedule.nextTrigger(now, clock(19), berlin) - now))
    }

    @Test
    fun aTimeTheClocksSkip_comesAnHourLater() {
        // 02:30 does not exist on 29 March 2026 in Berlin.
        val next = inBerlin(ReminderSchedule.nextTrigger(at(2026, 3, 28, 12), clock(2, 30), berlin))

        assertEquals(29, next.dayOfMonth)
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun aTimeTheClocksRepeat_isTheWallClockTimeOnce() {
        // 02:30 happens twice on 25 October 2026; the reminder is due at one of them, not both.
        val now = at(2026, 10, 24, 12)

        val next = inBerlin(ReminderSchedule.nextTrigger(now, clock(2, 30), berlin))

        assertEquals(25, next.dayOfMonth)
        assertEquals(2, next.hour)
        assertEquals(30, next.minute)
        assertTrue(ReminderSchedule.nextTrigger(next.toInstant().toEpochMilli(), clock(2, 30), berlin) > next.toInstant().toEpochMilli())
    }

    // --- shouldNotify -----------------------------------------------------------------------------------------------

    private val now = at(2026, 10, 5, 19)
    private val practicedToday = listOf(quiz(at(2026, 10, 5, 9)))
    private val practicedYesterday = listOf(quiz(at(2026, 10, 4, 9)))

    /** A full ten-question quiz at [at], or one of [items] questions. */
    private fun quiz(at: Long, items: Int = 10) = PracticeStreak.Session(at, items)

    private fun should(enabled: Boolean, allowed: Boolean, sessions: List<PracticeStreak.Session>, goal: Int = PracticeStreak.DEFAULT_GOAL) =
        ReminderSchedule.shouldNotify(enabled, allowed, sessions, goal, now, berlin)

    @Test
    fun theReminderIsSentOnlyWhenOnAllowedAndNothingWasPracticedToday() {
        for (enabled in listOf(false, true)) {
            for (allowed in listOf(false, true)) {
                for ((label, stamps) in listOf("never" to emptyList(), "yesterday" to practicedYesterday, "today" to practicedToday)) {
                    val expected = enabled && allowed && label != "today"
                    assertEquals("enabled=$enabled allowed=$allowed practiced=$label", expected, should(enabled, allowed, stamps))
                }
            }
        }
    }

    @Test
    fun aQuizFinishedJustAfterMidnight_countsForTheNewDay() {
        val justAfterMidnight = listOf(quiz(at(2026, 10, 5, 0, 0, 1)))

        assertFalse(should(enabled = true, allowed = true, sessions = justAfterMidnight))
        assertTrue(ReminderSchedule.shouldNotify(true, true, justAfterMidnight, PracticeStreak.DEFAULT_GOAL, at(2026, 10, 6, 19), berlin))
    }

    @Test
    fun theReminderStillComes_whileTodaysSessionsAreShortOfTheGoal() {
        // Four-question quizzes never count; five items are half of a goal of ten; two of them meet it.
        assertTrue(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 4), quiz(at(2026, 10, 5, 10), 4))))
        assertTrue(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 5))))
        assertFalse(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 5), quiz(at(2026, 10, 5, 12), 5))))
        // Against a goal of five, the one session of five is enough; against twenty, only a full session on its own is.
        assertFalse(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 5)), goal = 5))
        assertTrue(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 9)), goal = 20))
        assertFalse(should(enabled = true, allowed = true, sessions = listOf(quiz(at(2026, 10, 5, 9), 10)), goal = 20))
    }

    // --- message and lateness ---------------------------------------------------------------------------------------

    @Test
    fun aStreakAtRisk_isNamed() {
        val message = ReminderSchedule.message(PracticeStreak.Summary(current = 3, best = 5, practicedToday = false))

        assertTrue(message.keepsStreak)
        assertEquals(3, message.streakDays)
    }

    @Test
    fun noStreak_invitesANewOne() {
        val message = ReminderSchedule.message(PracticeStreak.Summary(current = 0, best = 4, practicedToday = false))

        assertFalse(message.keepsStreak)
        assertEquals(0, message.streakDays)
    }

    @Test
    fun aReminderMoreThanThreeHoursLate_isDropped() {
        val due = at(2026, 10, 5, 19)

        assertFalse(ReminderSchedule.isTooLate(due, due - 60_000))
        assertFalse(ReminderSchedule.isTooLate(due, due))
        assertFalse(ReminderSchedule.isTooLate(due, due + ReminderSchedule.MAX_LATENESS_MILLIS))
        assertTrue(ReminderSchedule.isTooLate(due, due + ReminderSchedule.MAX_LATENESS_MILLIS + 1))
    }
}
