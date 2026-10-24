package com.example.progress

import com.example.data.local.QuizScoreEntity
import com.example.flashcards.StudySession
import com.example.progress.Rewards.Badge
import com.example.progress.Rewards.Celebration
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    /** Noon UTC on the [n]th day from 1 Jan 2026. */
    private fun at(n: Int) = 1_767_225_600_000L + n * day + day / 2

    private fun quiz(
        n: Int = 0,
        score: Int = 100,
        correct: Int = 7,
        asked: Int = 10,
        mode: QuizMode = QuizMode.FLAG_NAME,
        scope: String = QuizEngine.GLOBAL
    ) = QuizScoreEntity(
        mode = mode.name, score = score, total = 190, continentFilter = scope, timestamp = at(n), correct = correct, questionCount = asked
    )

    private fun study(n: Int = 0, remembered: Int = 8, cards: Int = 10) = QuizScoreEntity(
        mode = StudySession.MODE, score = 0, total = 0, continentFilter = "This deck", timestamp = at(n), correct = remembered, questionCount = cards
    )

    @Test
    fun levels_beginAt100MoreThanTheOneBefore() {
        assertEquals(listOf(0, 100, 300, 600, 1000), (1..5).map(Rewards::xpAtLevel))
        assertEquals(1, Rewards.levelOf(0).number)
        assertEquals(1, Rewards.levelOf(99).number)
        assertEquals(2, Rewards.levelOf(100).number)
        assertEquals(Rewards.Level(2, 50, 200), Rewards.levelOf(150))
        assertEquals(5, Rewards.levelOf(1000).number)
        assertEquals("a negative total reads as none", 1, Rewards.levelOf(-5).number)
    }

    @Test
    fun xp_isThePointsOfEveryQuizPlusTwoPerRememberedCard() {
        assertEquals(0, Rewards.xp(emptyList(), utc))
        assertEquals(100 + 150 + 16, Rewards.xp(listOf(quiz(0, 100), quiz(1, 150), study(2, remembered = 8)), utc))
    }

    @Test
    fun xp_addsTheDailyBonusOncePerDayNotPerAttempt() {
        val daily = quiz(0, score = 80, scope = QuizEngine.DAILY)
        assertEquals(80 + Rewards.DAILY_BONUS, Rewards.xp(listOf(daily), utc))
        assertEquals("a second challenge on the same day earns its points only", 80 + 60 + Rewards.DAILY_BONUS, Rewards.xp(listOf(daily, daily.copy(score = 60)), utc))
        assertEquals(80 + 60 + 2 * Rewards.DAILY_BONUS, Rewards.xp(listOf(daily, daily.copy(score = 60, timestamp = at(1))), utc))
    }

    @Test
    fun theDailyDayIsTheLocalDay() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        // 23:30 UTC on day 0 is 08:30 on day 1 in Tokyo.
        val late = quiz(0, scope = QuizEngine.DAILY).copy(timestamp = at(0) + 11 * 3_600_000L + 30 * 60_000L)
        assertTrue(Rewards.dailyDoneToday(listOf(late), at(0), utc))
        assertFalse(Rewards.dailyDoneToday(listOf(late), at(1), utc))
        assertFalse(Rewards.dailyDoneToday(listOf(late), at(0), tokyo))
        assertTrue(Rewards.dailyDoneToday(listOf(late), at(1), tokyo))
    }

    @Test
    fun dailyStreak_countsDaysInARowUpToTodayOrYesterday() {
        val history = listOf(0, 1, 2, 4).map { quiz(it, scope = QuizEngine.DAILY) }
        assertEquals("today's challenge is still to play: the run ends yesterday", 3, Rewards.dailyStreak(history, at(3), utc))
        assertEquals(1, Rewards.dailyStreak(history, at(4), utc))
        assertEquals("a missed day ends the run", 0, Rewards.dailyStreak(history, at(6), utc))
        assertEquals("the missing day filled in joins the runs", 5, Rewards.dailyStreak(history + quiz(3, scope = QuizEngine.DAILY), at(4), utc))
        assertEquals(0, Rewards.dailyStreak(emptyList(), at(0), utc))
    }

    @Test
    fun badges_followTheLog() {
        assertTrue(Rewards.badges(emptyList(), utc).isEmpty())
        assertEquals(setOf(Badge.FIRST_QUIZ), Rewards.badges(listOf(quiz()), utc))
        assertTrue("a study session is no quiz", Rewards.badges(listOf(study()), utc).isEmpty())
        assertTrue(Badge.QUIZ_10 in Rewards.badges(List(10) { quiz(it) }, utc))
        assertTrue(Badge.QUIZ_50 !in Rewards.badges(List(49) { quiz(it) }, utc))
        assertTrue(Badge.QUIZ_50 in Rewards.badges(List(50) { quiz(it) }, utc))
        assertTrue(Badge.PERFECT in Rewards.badges(listOf(quiz(correct = 10)), utc))
        assertTrue("four questions are too few to be flawless", Badge.PERFECT !in Rewards.badges(listOf(quiz(correct = 4, asked = 4)), utc))
        assertTrue("a blitz is never flawless", Badge.PERFECT !in Rewards.badges(listOf(quiz(mode = QuizMode.BLITZ, correct = 30, asked = 30)), utc))
        assertTrue(Badge.BLITZ_20 in Rewards.badges(listOf(quiz(mode = QuizMode.BLITZ, correct = 20, asked = 22)), utc))
        assertTrue(Badge.BLITZ_20 !in Rewards.badges(listOf(quiz(mode = QuizMode.BLITZ, correct = 19, asked = 22)), utc))
        val modes = listOf(QuizMode.FLAG_NAME, QuizMode.CAPITAL, QuizMode.LOCATE, QuizMode.CONTINENT)
        assertTrue(Badge.EXPLORER in Rewards.badges(modes.map { quiz(mode = it) }, utc))
        assertTrue(Badge.EXPLORER !in Rewards.badges(modes.take(3).map { quiz(mode = it) }, utc))
        assertTrue(Badge.STUDIOUS in Rewards.badges(List(13) { study(it) }, utc))
        assertTrue(Badge.STUDIOUS !in Rewards.badges(List(12) { study(it) }, utc))
    }

    @Test
    fun dailyBadges_andTheLevelBadges() {
        assertTrue(Badge.DAILY_FIRST in Rewards.badges(listOf(quiz(scope = QuizEngine.DAILY)), utc))
        val week = (0..6).map { quiz(it, score = 10, scope = QuizEngine.DAILY) }
        assertTrue(Badge.DAILY_STREAK in Rewards.badges(week, utc))
        assertTrue("six days are not seven", Badge.DAILY_STREAK !in Rewards.badges(week.take(6), utc))
        assertTrue("a gap breaks it", Badge.DAILY_STREAK !in Rewards.badges(week.filter { it.timestamp != at(3) }, utc))
        assertTrue(Badge.LEVEL_5 in Rewards.badges(listOf(quiz(score = 1000)), utc))
        assertTrue(Badge.LEVEL_5 !in Rewards.badges(listOf(quiz(score = 999)), utc))
        assertTrue(Badge.LEVEL_10 in Rewards.badges(listOf(quiz(score = 4500)), utc))
    }

    @Test
    fun celebration_followsTheShareOfRightAnswers() {
        assertEquals(Celebration.PERFECT, Rewards.celebration(10, 10, endless = false))
        assertEquals(Celebration.GOOD, Rewards.celebration(7, 10, endless = false))
        assertEquals(Celebration.NONE, Rewards.celebration(6, 10, endless = false))
        assertEquals("too short to cheer", Celebration.NONE, Rewards.celebration(4, 4, endless = false))
        assertEquals(Celebration.NONE, Rewards.celebration(9, 60, endless = true))
        assertEquals(Celebration.GOOD, Rewards.celebration(10, 60, endless = true))
        assertEquals(Celebration.PERFECT, Rewards.celebration(20, 60, endless = true))
    }

    @Test
    fun reward_isWhatTheQuizAdds() {
        val before = listOf(quiz(0, score = 90))
        val finished = quiz(1, score = 150, correct = 10)
        val reward = Rewards.reward(before, finished, utc)
        assertEquals(150, reward.xpGained)
        assertEquals("90 + 150 = 240 passes 100", 2, reward.levelUp)
        assertEquals(listOf(Badge.PERFECT), reward.newBadges)
        assertFalse(reward.dailyCompleted)
        assertEquals(Celebration.PERFECT, reward.celebration)
    }

    @Test
    fun reward_countsALevelUpTheFirstChallengeAndItsBonus() {
        val before = listOf(quiz(0, score = 50))
        val finished = quiz(1, score = 60, scope = QuizEngine.DAILY)
        val reward = Rewards.reward(before, finished, utc)
        assertEquals(60 + Rewards.DAILY_BONUS, reward.xpGained)
        assertEquals(2, reward.levelUp)
        assertTrue(reward.dailyCompleted)
        assertTrue(Badge.DAILY_FIRST in reward.newBadges)
        assertNull(Rewards.reward(before + finished, finished.copy(score = 10), utc).levelUp)
        assertFalse("the second attempt of the day is no completion", Rewards.reward(before + finished, finished.copy(score = 10), utc).dailyCompleted)
        assertEquals(10, Rewards.reward(before + finished, finished.copy(score = 10), utc).xpGained)
    }

    @Test
    fun summary_gathersWhatHomeShows() {
        val history = listOf(quiz(0, score = 200, scope = QuizEngine.DAILY), quiz(1, score = 50))
        val summary = Rewards.summary(history, at(1), utc)
        assertEquals(300, summary.xp)
        assertEquals(3, summary.level.number)
        assertFalse(summary.dailyDoneToday)
        assertEquals(1, summary.dailyStreak)
        assertEquals(setOf(Badge.FIRST_QUIZ, Badge.DAILY_FIRST), summary.badges)
        assertEquals(Rewards.Summary.EMPTY, Rewards.summary(emptyList(), at(0), utc))
    }
}
