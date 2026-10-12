package com.example.quiz

import com.example.data.model.Country
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import kotlin.random.Random

/**
 * Which countries a quiz asks about first. Pure: every country in the pool gets a weight from its standing in the
 * skill the quiz exercises, and the order is a weighted draw without replacement, so a ten-question quiz is mostly
 * unseen and due countries, rarely one mastered last week, and seldom one it asked about a quiz or two ago.
 */
object QuestionPicker {
    /** Never reviewed in this skill. */
    const val UNSEEN = 4.0

    /** Reviewed and past its due date; grows by [OVERDUE_WEEK_BONUS] per week overdue, up to [OVERDUE_BONUS_CAP]. */
    const val DUE = 4.0
    const val OVERDUE_WEEK_BONUS = 1.0
    const val OVERDUE_BONUS_CAP = 4.0

    /** Reviewed, not mastered, not due yet. */
    const val WEAK = 2.0

    /** Mastered and not due: a rare check that it still holds. */
    const val MASTERED = 0.5

    /** Among the targets of the last few quizzes: the weight is multiplied by this. */
    const val RECENT_FACTOR = 0.1

    private const val WEEK_MILLIS = 7 * SkillMastery.DAY_MILLIS

    /** The weight of a country whose state in the quiz's skill is [state] (null when never reviewed) at [now]. */
    fun weight(state: SkillState?, now: Long, recent: Boolean): Double {
        val base = when {
            state == null || state.isNew -> UNSEEN
            SkillMastery.isDue(state, now) -> {
                val weeksOverdue = ((now - state.dueAt) / WEEK_MILLIS).toDouble()
                DUE + minOf(OVERDUE_BONUS_CAP, weeksOverdue * OVERDUE_WEEK_BONUS)
            }
            SkillMastery.isMastered(state, now) -> MASTERED
            else -> WEAK
        }
        return if (recent) base * RECENT_FACTOR else base
    }

    /**
     * Every country of [pool] once, the likeliest first: a weighted draw without replacement over the pool sorted by
     * code, so the same [random] always gives the same order whatever order the pool came in. [skill] holds the
     * states of the skill the quiz exercises; [recent] the codes asked about in the last few quizzes.
     */
    fun order(
        pool: List<Country>,
        skill: Map<String, SkillState>,
        recent: Set<String>,
        now: Long,
        random: Random
    ): List<Country> {
        val remaining = pool.sortedBy { it.code }
            .map { it to weight(skill[it.code], now, it.code in recent) }
            .toMutableList()
        val ordered = ArrayList<Country>(remaining.size)
        while (remaining.isNotEmpty()) {
            var pick = random.nextDouble() * remaining.sumOf { it.second }
            var index = remaining.lastIndex
            for (i in remaining.indices) {
                pick -= remaining[i].second
                if (pick < 0) {
                    index = i
                    break
                }
            }
            ordered += remaining.removeAt(index).first
        }
        return ordered
    }
}
