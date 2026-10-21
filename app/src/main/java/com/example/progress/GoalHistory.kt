package com.example.progress

import java.util.TimeZone

/**
 * The daily goal over time: [current] since the last change, and before it the goals the player replaced, each with
 * the moment it was replaced ([earlier], oldest first). The streak judges every day by the goal in force at its end
 * ([goalOn]), so picking another goal applies from today on and never re-judges the days already played. Kept in the
 * settings as [encodeEarlier] text, at most one change a local day ([changedTo]) and [MAX_CHANGES] of them back; a day
 * before the oldest one kept is judged by the oldest goal kept.
 */
data class GoalHistory(val current: Int, val earlier: List<Replaced> = emptyList()) {

    /** A goal the player replaced, and the moment ([until], epoch millis) they picked the next one. */
    data class Replaced(val goal: Int, val until: Long)

    /**
     * The goal [day] (a [PracticeStreak.localDay] in [zone]) is judged by: the one in force at its end, so the day of a
     * change already follows the new goal, as the Today card does.
     */
    fun goalOn(day: Long, zone: TimeZone): Int =
        earlier.firstOrNull { PracticeStreak.localDay(it.until, zone) > day }?.goal ?: current

    /**
     * The history once the player picks [goal] at [now]; the goal already in force changes nothing. Only the first
     * change of a local day (in [zone]) is kept: the days before are judged by the goal that day began with, and the
     * day itself by the goal in force at its end, so a later change the same day replaces [current] alone (picking
     * the goal the day began with again leaves no change at all). However often the goal is tapped, the history
     * holds one change a day and keeps [MAX_CHANGES] days of them.
     */
    fun changedTo(goal: Int, now: Long, zone: TimeZone): GoalHistory {
        if (goal == current) return this
        val last = earlier.lastOrNull()
        if (last != null && PracticeStreak.localDay(last.until, zone) == PracticeStreak.localDay(now, zone)) {
            return GoalHistory(goal, if (goal == last.goal) earlier.dropLast(1) else earlier)
        }
        return GoalHistory(goal, (earlier + Replaced(current, now)).takeLast(MAX_CHANGES))
    }

    /** The replaced goals as the settings keep them: `5@1760000000000,10@1761000000000`, oldest first; empty for none. */
    fun encodeEarlier(): String = earlier.joinToString(",") { "${it.goal}@${it.until}" }

    companion object {
        /** How many changes back are kept, one a day at most: plenty for a setting picked once and changed now and then. */
        const val MAX_CHANGES = 32

        /**
         * [current] with the replaced goals read back from [encoded] ([encodeEarlier] text). A part that is not a goal
         * of [PracticeStreak.GOALS] at a moment is left out, so a damaged value costs only the history, never the goal.
         */
        fun of(current: Int, encoded: String): GoalHistory {
            val earlier = encoded.split(',').mapNotNull { part ->
                val goal = part.substringBefore('@').trim().toIntOrNull()?.takeIf { it in PracticeStreak.GOALS }
                val until = part.substringAfter('@', "").trim().toLongOrNull()?.takeIf { it >= 0 }
                if (goal == null || until == null) null else Replaced(goal, until)
            }
            return GoalHistory(current, earlier.sortedBy { it.until }.takeLast(MAX_CHANGES))
        }
    }
}
