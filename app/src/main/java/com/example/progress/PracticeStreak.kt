package com.example.progress

import java.util.TimeZone

/**
 * The daily practice streak: a day counts when at least one quiz was finished on it, by the clock on the wall of
 * the device's current time zone. Pure rules over the finish times of the quizzes; nothing is stored apart from them.
 * Built on [TimeZone] because java.time needs core-library desugaring on the lowest supported Android version.
 */
object PracticeStreak {
    private const val DAY_MILLIS = 86_400_000L

    /** [current] days in a row up to today (or up to yesterday while today is still open), the [best] run ever. */
    data class Summary(val current: Int, val best: Int, val practicedToday: Boolean)

    /** What finishing one more quiz does to the streak, for the line on the score screen. */
    enum class Kind { STARTED, KEPT, ALREADY_COUNTED }

    data class Outcome(val kind: Kind, val days: Int)

    val NONE = Summary(current = 0, best = 0, practicedToday = false)

    /** The calendar day of [epochMillis] in [zone], counted in days from 1 January 1970 on that zone's own clock. */
    fun localDay(epochMillis: Long, zone: TimeZone): Long =
        Math.floorDiv(epochMillis + zone.getOffset(epochMillis), DAY_MILLIS)

    /**
     * The streak after quizzes finished at [timestamps], seen at [now]. Quizzes dated after [now] (a clock that was set
     * back) count for nothing. The current run may end yesterday: today is still open, so the streak is not lost yet.
     */
    fun summary(timestamps: Collection<Long>, now: Long, zone: TimeZone): Summary {
        val today = localDay(now, zone)
        val days = timestamps.asSequence().map { localDay(it, zone) }.filter { it <= today }.toSortedSet()
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

    /** What a quiz finished at [now] does to the streak made by [previous], the finish times before it. */
    fun outcome(previous: Collection<Long>, now: Long, zone: TimeZone): Outcome {
        val before = summary(previous, now, zone)
        return when {
            before.practicedToday -> Outcome(Kind.ALREADY_COUNTED, before.current)
            before.current == 0 -> Outcome(Kind.STARTED, 1)
            else -> Outcome(Kind.KEPT, before.current + 1)
        }
    }
}
