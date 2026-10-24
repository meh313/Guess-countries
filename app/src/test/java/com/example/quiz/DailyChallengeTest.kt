package com.example.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeTest {

    @Test
    fun dayNumber_countsDaysSince1970() {
        assertEquals(0, DailyChallenge.dayNumber("1970-01-01"))
        assertEquals(11017, DailyChallenge.dayNumber("2000-03-01"))
        assertEquals(20750, DailyChallenge.dayNumber("2026-10-24"))
    }

    @Test
    fun dayNumber_stepsByOneAcrossMonthsYearsAndLeapDays() {
        for ((a, b) in listOf("2026-10-31" to "2026-11-01", "2026-12-31" to "2027-01-01", "2028-02-28" to "2028-02-29", "2028-02-29" to "2028-03-01", "2027-02-28" to "2027-03-01")) {
            assertEquals("$a -> $b", DailyChallenge.dayNumber(a) + 1, DailyChallenge.dayNumber(b))
        }
    }

    @Test
    fun theMode_changesEveryDayAndCyclesThroughAllOfThem() {
        val modes = (0 until DailyChallenge.MODES.size).map { DailyChallenge.modeFor("2026-11-%02d".format(it + 1)) }
        assertEquals(DailyChallenge.MODES.toSet(), modes.toSet())
        assertNotEquals(DailyChallenge.modeFor("2026-11-01"), DailyChallenge.modeFor("2026-11-02"))
        assertEquals(DailyChallenge.modeFor("2026-11-01"), DailyChallenge.modeFor("2026-11-06"))
    }

    @Test
    fun theModes_haveNoClockAndNoTyping() {
        assertTrue(DailyChallenge.MODES.none { it.isEndless || it.isTyped || it.timeLimitSeconds != null })
    }

    @Test
    fun theQuestions_areTheSameOnEveryStartOfTheDay() {
        fun ask(day: String) = DailyChallenge.questions(day, allCountries).map { it.targetCountry.code to it.options }
        val today = ask("2026-10-24")
        assertEquals(QuizEngine.MAX_QUESTIONS, today.size)
        assertEquals(today, ask("2026-10-24"))
        assertNotEquals(today.map { it.first }, ask("2026-10-25").map { it.first })
    }

    @Test
    fun theQuestions_askAboutSovereignCountriesOnly_andNeverTheSameOneTwice() {
        for (n in 1..10) {
            val questions = DailyChallenge.questions("2026-12-%02d".format(n), allCountries)
            assertTrue(questions.all { it.targetCountry.isSovereign })
            assertEquals(questions.size, questions.map { it.targetCountry.code }.toSet().size)
        }
    }

    @Test
    fun withTooFewCountries_thereIsNoChallenge() {
        assertTrue(DailyChallenge.questions("2026-10-24", sovereign.take(3)).isEmpty())
    }
}
