package com.example.progress

import com.example.quiz.QuizMode
import java.util.TimeZone

/**
 * What a country can be known by. A flag quiz, a capital quiz, a continent quiz and a map quiz measure different
 * things; the continent is its own skill so that a right continent pick never marks a flag as learning, and the
 * location (Map → Country) its own so that knowing where a country is never counts as knowing its flag. The UI shows
 * FLAG and CAPITAL on the cards and LOCATION on the detail sheet and Progress; CONTINENT rows are stored like the
 * others and shown on the detail sheet. The names are stored in the skill rows.
 */
enum class Skill {
    FLAG, CAPITAL, CONTINENT, LOCATION;

    /**
     * Whether this skill's reviews fall due: every skill but the continent. A continent pick is light evidence, which
     * never moves a due date on, and the continent quiz always asks the whole world (it has no Due scope), so nothing
     * could ever clear a continent review: its rows keep a score and a box, and no due count, list or forecast
     * includes them.
     */
    val hasReviews: Boolean get() = this != CONTINENT

    companion object {
        /** The skills whose reviews fall due ([hasReviews]): what the Due tile, the Today card, the widget and the forecast count. */
        val REVIEWED: List<Skill> = entries.filter { it.hasReviews }
    }
}

/**
 * How much an answer says. A quiz answer among four countries is FULL evidence; one given against a clock (Speed
 * Round, Blitz) is TIMED, where a miss may be the clock rather than a gap, so it costs less; a continent pick (one
 * of five) and a self-graded flashcard are LIGHT: they move the score a little and never past [SkillMastery.LIGHT_CAP].
 */
enum class Evidence { FULL, LIGHT, TIMED }

/**
 * A flashcard grade: Again drops the card two boxes, Good is a light "I knew it" that clears a due card until its
 * interval passes, Easy promotes it but never past [SkillMastery.SELF_GRADE_MAX_BOX].
 */
enum class Grade { AGAIN, GOOD, EASY }

/**
 * One country's standing in one skill. [score] is a 0..100 confidence, [box] the Leitner box (0 = never seen,
 * 1..5), [dueAt] when the next review is due. Nothing here is written by a clock: a country lapses by not being
 * reviewed, which the rules read off [dueAt] when asked.
 */
data class SkillState(
    val score: Int = 0,
    val box: Int = 0,
    val dueAt: Long = 0L,
    val reviews: Int = 0,
    val correct: Int = 0,
    /** 1 after a right answer, -1 after a wrong one, 0 when nothing is known (a migrated row). */
    val lastResult: Int = 0,
    val lastReviewed: Long = 0L
) {
    val isNew: Boolean get() = reviews == 0
}

/**
 * The rules of per-skill mastery: a Leitner schedule (boxes 1..5 with growing intervals) over a confidence score.
 * Pure, like [PracticeStreak]; every rule takes `now` so tests pin the clock.
 *
 * Mastering a country takes right answers on three separate due days with real quiz evidence: an early answer
 * (before the item is due) raises the score but does not promote the box, so nothing is mastered in one sitting,
 * and light evidence alone (continent picks, flashcards) tops out below the mastery line. A miss drops boxes
 * rather than resetting: a long-known flag missed once is reviewed sooner, not relearned from scratch.
 *
 * A review falls due on a local day, not at a minute: the intervals are counted from the answer, and the item is
 * due from the start of the day its due time falls on ([endOfToday]), so a flag learnt on Monday evening is due all
 * of Tuesday, and answering it on Tuesday morning is its due review.
 */
object SkillMastery {
    const val MAX_BOX = 5

    /** Days until the next review, by box. Box 0 (never seen) is due at once; box 5 holds two months. */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 21, 60)

    const val MASTERED_SCORE = 75
    const val MASTERED_BOX = 3

    /** Light evidence alone never lifts the score past this, so a flag is never mastered without a quiz answer. */
    const val LIGHT_CAP = 60

    /** A self-graded Easy never lifts a card past this box: the quiz, not the player, decides the long intervals. */
    const val SELF_GRADE_MAX_BOX = 2

    const val FULL_RIGHT = 20
    const val FULL_WRONG = 20
    const val TIMED_WRONG = 10
    const val LIGHT_RIGHT = 8
    const val LIGHT_WRONG = 8
    const val EASY_RIGHT = 12
    const val AGAIN_WRONG = 12

    const val DAY_MILLIS = 86_400_000L

    fun skillOf(mode: QuizMode): Skill = when (mode) {
        QuizMode.CAPITAL -> Skill.CAPITAL
        QuizMode.CONTINENT -> Skill.CONTINENT
        QuizMode.LOCATE -> Skill.LOCATION
        else -> Skill.FLAG
    }

    /** A map pick among four named countries (LOCATE) is as telling as a flag pick: full evidence, like the rest. */
    fun evidenceOf(mode: QuizMode): Evidence = when (mode) {
        QuizMode.CONTINENT -> Evidence.LIGHT
        QuizMode.SPEED_MATCH, QuizMode.BLITZ -> Evidence.TIMED
        else -> Evidence.FULL
    }

    fun intervalMillis(box: Int): Long = INTERVAL_DAYS[box.coerceIn(0, MAX_BOX)] * DAY_MILLIS

    /** The state after one quiz answer about the country, right or wrong, at [now]; [zone] says which day that is. */
    fun afterAnswer(state: SkillState, correct: Boolean, evidence: Evidence, now: Long, zone: TimeZone): SkillState = when (evidence) {
        Evidence.FULL ->
            if (correct) promoteIfDue(state, now, zone, newScore = minOf(100, state.score + FULL_RIGHT))
            else demote(state, now, by = 2, newScore = maxOf(0, state.score - FULL_WRONG))
        Evidence.TIMED ->
            if (correct) promoteIfDue(state, now, zone, newScore = minOf(100, state.score + FULL_RIGHT))
            else demote(state, now, by = 1, newScore = maxOf(0, state.score - TIMED_WRONG))
        Evidence.LIGHT ->
            if (correct) seen(state, now, zone, newScore = lightAdd(state.score, LIGHT_RIGHT))
            else demote(state, now, by = 1, newScore = maxOf(0, state.score - LIGHT_WRONG))
    }

    /** The state after a flashcard grade at [now]; [zone] says which day that is. */
    fun afterGrade(state: SkillState, grade: Grade, now: Long, zone: TimeZone): SkillState = when (grade) {
        Grade.AGAIN -> demote(state, now, by = 2, newScore = maxOf(0, state.score - AGAIN_WRONG))
        Grade.GOOD -> seen(state, now, zone, newScore = lightAdd(state.score, LIGHT_RIGHT), rescheduleDue = true)
        Grade.EASY -> promoteIfDue(state, now, zone, newScore = lightAdd(state.score, EASY_RIGHT), maxBox = SELF_GRADE_MAX_BOX)
    }

    /** Known well and kept up: box 3 or higher, score at the line, and not left for longer than twice its interval. */
    fun isMastered(state: SkillState?, now: Long): Boolean =
        state != null && state.box >= MASTERED_BOX && state.score >= MASTERED_SCORE &&
            now <= state.dueAt + intervalMillis(state.box)

    /**
     * Where "due today" ends: the local midnight that closes the day of [now] in [zone] (a 23- or 25-hour day across
     * a clock change, the current zone after a time-zone change). Every review due before it is due today, whatever
     * the hour; the Coming up forecast starts at it, so each review is in exactly one of the two.
     */
    fun endOfToday(now: Long, zone: TimeZone): Long = DueForecast.startOfDay(now, zone, daysAhead = 1)

    /**
     * Reviewed before and due today or overdue ([endOfToday]): the one rule behind every due count and list (the Due
     * tile, the Today card, the quiz's Due scope, the study chooser, the widget) and behind promotion, so an answer to
     * a review due this evening counts as the due review. A country never reviewed is unseen, not due.
     */
    fun isDue(state: SkillState?, now: Long, zone: TimeZone): Boolean =
        state != null && !state.isNew && state.dueAt < endOfToday(now, zone)

    /** Reviewed before and not (or no longer) mastered. */
    fun isWeak(state: SkillState?, now: Long): Boolean = state != null && !state.isNew && !isMastered(state, now)

    /** Light evidence adds a little while the score is below the cap and never lifts it over the cap. */
    private fun lightAdd(score: Int, points: Int): Int = if (score >= LIGHT_CAP) score else minOf(LIGHT_CAP, score + points)

    /**
     * A right answer: one box up (never past [maxBox]) and a longer interval when the item was due; an early answer
     * keeps its schedule. An item already at or above [maxBox] when due is rescheduled at its box, not moved down.
     */
    private fun promoteIfDue(state: SkillState, now: Long, zone: TimeZone, newScore: Int, maxBox: Int = MAX_BOX): SkillState {
        val due = state.isNew || isDue(state, now, zone)
        val box = if (due && state.box < maxBox) state.box + 1 else state.box
        return state.copy(
            score = newScore,
            box = box,
            dueAt = if (due) now + intervalMillis(box) else state.dueAt,
            reviews = state.reviews + 1,
            correct = state.correct + 1,
            lastResult = 1,
            lastReviewed = now
        )
    }

    /**
     * Light evidence of knowing: counts as seen and keeps the schedule (a new item becomes due tomorrow). With
     * [rescheduleDue], a due item is rescheduled at its current box, so studying it clears it from the due list.
     */
    private fun seen(state: SkillState, now: Long, zone: TimeZone, newScore: Int, rescheduleDue: Boolean = false): SkillState {
        val box = maxOf(1, state.box)
        val reschedule = state.isNew || (rescheduleDue && isDue(state, now, zone))
        return state.copy(
            score = newScore,
            box = box,
            dueAt = if (reschedule) now + intervalMillis(box) else state.dueAt,
            reviews = state.reviews + 1,
            correct = state.correct + 1,
            lastResult = 1,
            lastReviewed = now
        )
    }

    /** Evidence of not knowing: [by] boxes down (never below 1), reviewed again after that box's interval. */
    private fun demote(state: SkillState, now: Long, by: Int, newScore: Int): SkillState {
        val box = maxOf(1, state.box - by)
        return state.copy(score = newScore, box = box, dueAt = now + intervalMillis(box), reviews = state.reviews + 1, lastResult = -1, lastReviewed = now)
    }
}
