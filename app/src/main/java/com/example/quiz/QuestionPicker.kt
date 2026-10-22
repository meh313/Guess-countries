package com.example.quiz

import com.example.data.model.Country
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import java.util.TimeZone
import kotlin.random.Random

/**
 * Which countries a quiz asks about. Pure: every country in the pool gets a weight from its standing in the skill the
 * quiz exercises, and [order] is a weighted draw without replacement; [select] fills a fixed-length quiz by group,
 * due reviews first, so the reviews are cleared before new flags pile more on. Recency means "asked in the same skill
 * in the last 45 minutes" ([RECENT_WINDOW_MILLIS]): a country just asked about is nearly skipped ([order] weighs it
 * down, [select] takes it only once nothing else is left), a due one too. Due means due today in the zone the rules
 * are given ([SkillMastery.isDue]), at whatever hour, so a flag first answered at 23:50 is due again at midnight;
 * without the hold a quiz at 00:05 would ask it again, and promote it, a quarter of an hour after the first answer.
 */
object QuestionPicker {
    /** Never reviewed in this skill. */
    const val UNSEEN = 4.0

    /** Reviewed and due today or overdue; grows by [OVERDUE_WEEK_BONUS] per week overdue, up to [OVERDUE_BONUS_CAP]. */
    const val DUE = 4.0
    const val OVERDUE_WEEK_BONUS = 1.0
    const val OVERDUE_BONUS_CAP = 4.0

    /** Reviewed, not mastered, not due yet. */
    const val WEAK = 2.0

    /** Mastered and not due: a rare check that it still holds. */
    const val MASTERED = 0.5

    /** Asked about in this skill within [RECENT_WINDOW_MILLIS]: the weight is multiplied by this, due or not. */
    const val RECENT_FACTOR = 0.1

    /** How long a country counts as recently asked: the same sitting, not the same day, so yesterday's reviews come back. */
    const val RECENT_WINDOW_MILLIS = 45 * 60 * 1000L

    /** At most this many of a quiz's questions are due reviews, so a backlog still leaves room for new countries. */
    const val DUE_SHARE = 7

    /**
     * At most this many never-seen countries per quiz, so new flags arrive at a pace the reviews can keep up with; more
     * only when nothing else is left but the countries just asked about (see [select]).
     */
    const val NEW_PER_QUIZ = 3

    private const val WEEK_MILLIS = 7 * SkillMastery.DAY_MILLIS

    /**
     * The weight of a country whose state in the quiz's skill is [state] (null when never reviewed) at [now] in
     * [zone], a tenth of it when [recent]: a due item just asked about (due again at midnight) waits too.
     */
    fun weight(state: SkillState?, now: Long, zone: TimeZone, recent: Boolean): Double {
        val base = when {
            state != null && SkillMastery.isDue(state, now, zone) ->
                DUE + minOf(OVERDUE_BONUS_CAP, ((now - state.dueAt) / WEEK_MILLIS).toDouble() * OVERDUE_WEEK_BONUS)
            state == null || state.isNew -> UNSEEN
            SkillMastery.isMastered(state, now) -> MASTERED
            else -> WEAK
        }
        return if (recent) base * RECENT_FACTOR else base
    }

    /**
     * Every country of [pool] once, the likeliest first: a weighted draw without replacement over the pool sorted by
     * code, so the same [random] always gives the same order whatever order the pool came in. [skill] holds the
     * states of the skill the quiz exercises; [recent] the codes asked about in that skill within the recent window.
     */
    fun order(
        pool: List<Country>,
        skill: Map<String, SkillState>,
        recent: Set<String>,
        now: Long,
        zone: TimeZone,
        random: Random
    ): List<Country> = draw(pool, skill, recent, now, zone, random, pool.size)

    /**
     * How a fixed-length quiz is filled: reviews first, then a few new countries, then the rest by weight, and the
     * countries just asked about last.
     *
     * Returns [count] countries of [pool] (all of it when smaller), as groups in priority order: the due ones (by
     * weighted draw among themselves) up to [DUE_SHARE]; the weak ones (reviewed, not due, not mastered); unseen ones,
     * at most [newAllowance] and at most [NEW_PER_QUIZ]; then the reviewed ones left (mastered, more weak, more due) by
     * weight; then more unseen ones; and only when nothing else is left, the ones in [recent], due or not. So a quiz
     * straight after another moves on to other countries instead of asking the same ten again, even when that means
     * more new ones, and a flag due again at midnight is not asked twice within the hour. The caller shuffles the
     * final question order, so due reviews are not always the opening questions.
     *
     * [preferred] holds the codes of the countries to introduce first (the focus continent's): among the unseen, those
     * are drawn before the rest, each part in its own weighted draw.
     */
    fun select(
        pool: List<Country>,
        skill: Map<String, SkillState>,
        recent: Set<String>,
        now: Long,
        zone: TimeZone,
        random: Random,
        newAllowance: Int,
        count: Int = QuizEngine.MAX_QUESTIONS,
        preferred: Set<String> = emptySet()
    ): List<Country> {
        // Just asked about, due or not: held back until every other group is used up.
        val (held, others) = pool.partition { it.code in recent }
        val due = others.filter { SkillMastery.isDue(skill[it.code], now, zone) }
        val weak = others.filter { SkillMastery.isWeak(skill[it.code], now) && !SkillMastery.isDue(skill[it.code], now, zone) }
        val unseen = others.filter { skill[it.code]?.isNew != false }
        val picked = ArrayList<Country>(count)
        fun room() = count - picked.size

        /** A draw of [limit] unseen countries from [from], the preferred ones first. */
        fun drawUnseen(from: List<Country>, limit: Int): List<Country> {
            if (preferred.isEmpty()) return draw(from, skill, recent, now, zone, random, limit)
            val first = draw(from.filter { it.code in preferred }, skill, recent, now, zone, random, limit)
            return first + draw(from.filter { it.code !in preferred }, skill, recent, now, zone, random, limit - first.size)
        }

        picked += draw(due, skill, recent, now, zone, random, minOf(DUE_SHARE, room()))
        picked += draw(weak, skill, recent, now, zone, random, room())
        picked += drawUnseen(unseen, minOf(newAllowance.coerceIn(0, NEW_PER_QUIZ), room()))

        val taken = picked.mapTo(HashSet()) { it.code }
        val reviewedLeft = others.filter { it.code !in taken && skill[it.code]?.isNew == false }
        picked += draw(reviewedLeft, skill, recent, now, zone, random, room())
        picked += drawUnseen(unseen.filter { it.code !in taken }, room())
        picked += draw(held, skill, recent, now, zone, random, room())
        return picked
    }

    /** The first [limit] of a weighted draw without replacement over [pool] sorted by code. */
    private fun draw(
        pool: List<Country>,
        skill: Map<String, SkillState>,
        recent: Set<String>,
        now: Long,
        zone: TimeZone,
        random: Random,
        limit: Int
    ): List<Country> {
        if (limit <= 0 || pool.isEmpty()) return emptyList()
        val remaining = pool.sortedBy { it.code }
            .map { it to weight(skill[it.code], now, zone, it.code in recent) }
            .toMutableList()
        val drawn = ArrayList<Country>(minOf(limit, remaining.size))
        while (remaining.isNotEmpty() && drawn.size < limit) {
            var pick = random.nextDouble() * remaining.sumOf { it.second }
            var index = remaining.lastIndex
            for (i in remaining.indices) {
                pick -= remaining[i].second
                if (pick < 0) {
                    index = i
                    break
                }
            }
            drawn += remaining.removeAt(index).first
        }
        return drawn
    }
}
