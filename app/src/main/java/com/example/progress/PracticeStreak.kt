package com.example.progress

import java.util.TimeZone

/**
 * The daily practice streak: a day counts as practised when its sessions reach the daily goal, by the clock on the
 * wall of the device's current time zone. Pure rules over the finish time and size of each quiz or study session;
 * nothing is stored apart from them. Built on [TimeZone] because java.time needs core-library desugaring on the
 * lowest supported Android version.
 *
 * The rule: a day is practised when the sessions of at least [MIN_SESSION] items finished on it add up to the goal
 * or more, or when any single session of the day has [FULL_SESSION] items or more. A session under [MIN_SESSION]
 * items (a quiz of four questions, a blitz with no answer) never counts towards anything.
 */
object PracticeStreak {
    private const val DAY_MILLIS = 86_400_000L

    /** The daily goal in items (questions answered, cards graded) until the player picks another; one of [GOALS]. */
    const val DEFAULT_GOAL = 10

    /** The goals the player can pick from. */
    val GOALS = listOf(5, 10, 20)

    /** A session shorter than this counts for nothing: too little to call practice. */
    const val MIN_SESSION = 5

    /** A single session this long makes the day count whatever the goal. */
    const val FULL_SESSION = 10

    /** One finished quiz or study session: when it ended and how many items it had (questions answered, cards graded). */
    data class Session(val at: Long, val items: Int)

    /** [current] days in a row up to today (or up to yesterday while today is still open), the [best] run ever. */
    data class Summary(val current: Int, val best: Int, val practicedToday: Boolean)

    /** What finishing one more quiz does to the streak, for the line on the score screen. */
    enum class Kind { STARTED, KEPT, ALREADY_COUNTED }

    data class Outcome(val kind: Kind, val days: Int)

    val NONE = Summary(current = 0, best = 0, practicedToday = false)

    /** The calendar day of [epochMillis] in [zone], counted in days from 1 January 1970 on that zone's own clock. */
    fun localDay(epochMillis: Long, zone: TimeZone): Long =
        Math.floorDiv(epochMillis + zone.getOffset(epochMillis), DAY_MILLIS)

    /** Whether the sessions of one day, [items] each, make it a practised day against [goal]. */
    fun dayCounts(items: Collection<Int>, goal: Int): Boolean =
        items.any { it >= FULL_SESSION } || countedItems(items) >= goal

    /** The items of one day's sessions as the goal counts them: those of the sessions of at least [MIN_SESSION] items. */
    fun countedItems(items: Collection<Int>): Int = items.filter { it >= MIN_SESSION }.sum()

    /** The items done today as the goal counts them ([countedItems]), from [sessions] seen at [now]. */
    fun itemsToday(sessions: Collection<Session>, now: Long, zone: TimeZone): Int {
        val today = localDay(now, zone)
        return countedItems(sessions.filter { localDay(it.at, zone) == today }.map { it.items })
    }

    /** The days practised among [sessions] against [goal], up to and including [today]. */
    private fun practisedDays(sessions: Collection<Session>, goal: Int, today: Long, zone: TimeZone): java.util.SortedSet<Long> =
        sessions.asSequence()
            .map { localDay(it.at, zone) to it.items }
            .filter { (day, _) -> day <= today }
            .groupBy({ it.first }, { it.second })
            .filterValues { dayCounts(it, goal) }
            .keys
            .toSortedSet()

    /**
     * The streak after [sessions], against [goal], seen at [now]. Sessions dated after [now] (a clock that was set
     * back) count for nothing. The current run may end yesterday: today is still open, so the streak is not lost yet.
     */
    fun summary(sessions: Collection<Session>, goal: Int, now: Long, zone: TimeZone): Summary {
        val today = localDay(now, zone)
        val days = practisedDays(sessions, goal, today, zone)
        if (days.isEmpty()) return NONE

        val practicedToday = today in days
        var current = 0
        var day = if (practicedToday) today else today - 1
        while (day in days) {
            current++
            day--
        }

        var best = 0
        var run = 0
        var previous: Long? = null
        for (d in days) {
            run = if (previous != null && d == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = d
        }
        return Summary(current, best, practicedToday)
    }

    /**
     * What [latest], a session just finished, does to the streak made by [previous], the sessions before it, against
     * [goal] at [now]. Today was already counted when [previous] alone made it a practised day; the session starts or
     * keeps the streak when it is what makes today count; and a session that leaves today short of the goal changes
     * nothing, which comes back as [Kind.ALREADY_COUNTED] with 0 days (the score screen says the goal is not met yet).
     */
    fun outcome(previous: Collection<Session>, latest: Session, goal: Int, now: Long, zone: TimeZone): Outcome {
        val before = summary(previous, goal, now, zone)
        if (before.practicedToday) return Outcome(Kind.ALREADY_COUNTED, before.current)
        val after = summary(previous + latest, goal, now, zone)
        return when {
            !after.practicedToday -> Outcome(Kind.ALREADY_COUNTED, 0)
            before.current == 0 -> Outcome(Kind.STARTED, 1)
            else -> Outcome(Kind.KEPT, before.current + 1)
        }
    }
}
