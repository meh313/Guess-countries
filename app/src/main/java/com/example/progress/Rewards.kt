package com.example.progress

import com.example.data.local.QuizScoreEntity
import com.example.flashcards.StudySession
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import java.util.TimeZone

/**
 * The game layer over the quiz log: experience points (XP), levels, badges and how loud a finished quiz is
 * celebrated. Nothing is stored of its own; every value is worked out again from the logged quizzes and study
 * sessions, so it follows an import, a reset and the backup like the rest of the progress. Pure rules, no Android.
 */
object Rewards {
    /** XP for completing the day's challenge, once a day, on top of its points. */
    const val DAILY_BONUS = 50

    /** XP for each study card remembered; a study session has no points of its own. */
    const val STUDY_CARD_XP = 2

    /** Days of challenges in a row that earn [Badge.DAILY_STREAK]. */
    const val DAILY_STREAK_DAYS = 7

    /** A quiz of at least this many questions can be perfect or good. */
    private const val MIN_CELEBRATED = 5

    /** A level and how far the player is into it. */
    data class Level(val number: Int, val xpIntoLevel: Int, val xpForLevel: Int) {
        val fraction: Float get() = if (xpForLevel == 0) 0f else xpIntoLevel.toFloat() / xpForLevel
    }

    /** The XP at which [level] begins: 100 for level 2, 300 for 3, 600 for 4 (each level asks 100 more than the one before). */
    fun xpAtLevel(level: Int): Int = 50 * level * (level - 1)

    fun levelOf(xp: Int): Level {
        val points = xp.coerceAtLeast(0)
        var level = 1
        while (xpAtLevel(level + 1) <= points) level++
        return Level(level, points - xpAtLevel(level), xpAtLevel(level + 1) - xpAtLevel(level))
    }

    /** The badges, in the order they are shown. The names are stored nowhere; the titles and hints are string resources. */
    enum class Badge {
        FIRST_QUIZ,
        QUIZ_10,
        QUIZ_50,
        PERFECT,
        BLITZ_20,
        EXPLORER,
        STUDIOUS,
        DAILY_FIRST,
        DAILY_STREAK,
        LEVEL_5,
        LEVEL_10
    }

    /** How loud the end of a quiz is: nothing, confetti, or a bigger shower for a perfect score. */
    enum class Celebration { NONE, GOOD, PERFECT }

    /** What finishing one quiz earned, for its score screen. */
    data class Reward(
        val xpGained: Int,
        /** The level reached when the quiz raised it, else null. */
        val levelUp: Int?,
        val newBadges: List<Badge>,
        /** The quiz completed the day's challenge for the first time today. */
        val dailyCompleted: Boolean,
        val celebration: Celebration
    )

    private fun isQuiz(row: QuizScoreEntity) = row.mode != StudySession.MODE

    private fun isDaily(row: QuizScoreEntity) = isQuiz(row) && row.continentFilter == QuizEngine.DAILY

    /** The points of one logged session as XP. */
    private fun points(row: QuizScoreEntity): Int =
        if (isQuiz(row)) row.score else (row.correct ?: 0) * STUDY_CARD_XP

    /** The local days (see [PracticeStreak.localDay]) on which a daily challenge was finished. */
    fun dailyDays(history: Collection<QuizScoreEntity>, zone: TimeZone): java.util.SortedSet<Long> =
        history.filter(::isDaily).mapTo(java.util.TreeSet()) { PracticeStreak.localDay(it.timestamp, zone) }

    /** Days of challenges in a row up to today (or up to yesterday while today's is still to play). */
    fun dailyStreak(history: Collection<QuizScoreEntity>, now: Long, zone: TimeZone): Int {
        val days = dailyDays(history, zone)
        val today = PracticeStreak.localDay(now, zone)
        var day = if (today in days) today else today - 1
        var run = 0
        while (day in days) {
            run++
            day--
        }
        return run
    }

    /** Whether a challenge was finished on the local day of [now]. */
    fun dailyDoneToday(history: Collection<QuizScoreEntity>, now: Long, zone: TimeZone): Boolean =
        PracticeStreak.localDay(now, zone) in dailyDays(history, zone)

    /** The longest run of challenge days anywhere in the log. */
    private fun bestDailyRun(history: Collection<QuizScoreEntity>, zone: TimeZone): Int {
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in dailyDays(history, zone)) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** Every point of every quiz, the cards remembered in study sessions, and the daily bonus of each challenge day. */
    fun xp(history: Collection<QuizScoreEntity>, zone: TimeZone): Int =
        history.sumOf(::points) + DAILY_BONUS * dailyDays(history, zone).size

    /** The badges [history] has earned. */
    fun badges(history: Collection<QuizScoreEntity>, zone: TimeZone): Set<Badge> {
        val quizzes = history.filter(::isQuiz)
        val level = levelOf(xp(history, zone)).number
        val earned = LinkedHashSet<Badge>()
        if (quizzes.isNotEmpty()) earned += Badge.FIRST_QUIZ
        if (quizzes.size >= 10) earned += Badge.QUIZ_10
        if (quizzes.size >= 50) earned += Badge.QUIZ_50
        if (quizzes.any { isPerfect(it) }) earned += Badge.PERFECT
        if (quizzes.any { it.mode == QuizMode.BLITZ.name && (it.correct ?: 0) >= 20 }) earned += Badge.BLITZ_20
        if (quizzes.map { it.mode }.toSet().size >= 4) earned += Badge.EXPLORER
        if (history.filterNot(::isQuiz).sumOf { it.correct ?: 0 } >= 100) earned += Badge.STUDIOUS
        if (quizzes.any(::isDaily)) earned += Badge.DAILY_FIRST
        if (bestDailyRun(history, zone) >= DAILY_STREAK_DAYS) earned += Badge.DAILY_STREAK
        if (level >= 5) earned += Badge.LEVEL_5
        if (level >= 10) earned += Badge.LEVEL_10
        return earned
    }

    private fun isPerfect(row: QuizScoreEntity): Boolean {
        val asked = row.questionCount ?: return false
        return row.mode != QuizMode.BLITZ.name && asked >= MIN_CELEBRATED && row.correct == asked
    }

    /** How loudly a quiz with [correct] right of [answered] is celebrated; [endless] for the blitz, which counts its right answers. */
    fun celebration(correct: Int, answered: Int, endless: Boolean): Celebration = when {
        endless -> if (correct >= 20) Celebration.PERFECT else if (correct >= 10) Celebration.GOOD else Celebration.NONE
        answered < MIN_CELEBRATED -> Celebration.NONE
        correct == answered -> Celebration.PERFECT
        correct * 10 >= answered * 7 -> Celebration.GOOD
        else -> Celebration.NONE
    }

    /**
     * What [finished], a session just logged, earned on top of [before], the sessions logged earlier, seen at [now]:
     * its XP (the daily bonus when it is the first challenge of the day), the level it raised the player to, and the
     * badges it unlocked.
     */
    fun reward(before: Collection<QuizScoreEntity>, finished: QuizScoreEntity, zone: TimeZone): Reward {
        val after = before + finished
        val xpBefore = xp(before, zone)
        val xpAfter = xp(after, zone)
        val levelBefore = levelOf(xpBefore).number
        val levelAfter = levelOf(xpAfter).number
        val had = badges(before, zone)
        val asked = finished.questionCount ?: 0
        val endless = finished.mode == QuizMode.BLITZ.name
        return Reward(
            xpGained = xpAfter - xpBefore,
            levelUp = levelAfter.takeIf { it > levelBefore },
            newBadges = badges(after, zone).filter { it !in had },
            dailyCompleted = isDaily(finished) && PracticeStreak.localDay(finished.timestamp, zone) !in dailyDays(before, zone),
            celebration = celebration(finished.correct ?: 0, asked, endless)
        )
    }

    /** Everything the Home screen shows about the game layer, as of [now]. */
    data class Summary(
        val xp: Int,
        val level: Level,
        val badges: Set<Badge>,
        val dailyDoneToday: Boolean,
        val dailyStreak: Int
    ) {
        companion object {
            val EMPTY = Summary(0, levelOf(0), emptySet(), false, 0)
        }
    }

    fun summary(history: Collection<QuizScoreEntity>, now: Long, zone: TimeZone): Summary {
        val xp = xp(history, zone)
        return Summary(xp, levelOf(xp), badges(history, zone), dailyDoneToday(history, now, zone), dailyStreak(history, now, zone))
    }
}
