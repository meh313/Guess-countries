package com.example.progress

import java.util.Calendar
import java.util.TimeZone

/**
 * How many countries come up for review soon, for the "Coming up" line on Progress: [tomorrow] is the next local
 * day, [week] the next seven local days starting with tomorrow. A country counts once however many of its skills
 * fall due; what is already due (today or overdue) is on the Due tile, not here.
 */
data class DueForecast(val tomorrow: Int, val week: Int) {
    val isEmpty: Boolean get() = tomorrow == 0 && week == 0

    companion object {
        val NONE = DueForecast(0, 0)

        /**
         * The skills the forecast reads: flags, capitals and locations, each with its own due dates (a country due
         * in any of them counts once); the continent skill is kept but not shown anywhere.
         */
        private val SHOWN = listOf(Skill.FLAG, Skill.CAPITAL, Skill.LOCATION)

        /**
         * The forecast at [now] in [zone] over [skills] (per skill, per country code), counting only the countries
         * in [codes] (the sovereign ones). A skill never reviewed is unseen, not coming up.
         */
        fun of(skills: Map<Skill, Map<String, SkillState>>, codes: Set<String>, now: Long, zone: TimeZone): DueForecast {
            val startOfTomorrow = startOfDay(now, zone, daysAhead = 1)
            val endOfTomorrow = startOfDay(now, zone, daysAhead = 2)
            val endOfWeek = startOfDay(now, zone, daysAhead = 8)
            val tomorrow = HashSet<String>()
            val week = HashSet<String>()
            for (skill in SHOWN) {
                for ((code, state) in skills[skill].orEmpty()) {
                    if (code !in codes || state.isNew) continue
                    if (state.dueAt < startOfTomorrow || state.dueAt >= endOfWeek) continue
                    week += code
                    if (state.dueAt < endOfTomorrow) tomorrow += code
                }
            }
            return DueForecast(tomorrow.size, week.size)
        }

        /** Midnight, in [zone], [daysAhead] local days after the day of [now]; follows the calendar across clock changes. */
        fun startOfDay(now: Long, zone: TimeZone, daysAhead: Int): Long =
            Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, daysAhead)
            }.timeInMillis
    }
}
