package com.example.widget

import com.example.data.local.SkillProgressEntity
import com.example.progress.PracticeStreak
import com.example.progress.SkillMastery
import java.util.TimeZone

/** What the home-screen widget shows: reviews due now, the streak, and today's items against the goal. Pure. */
data class WidgetState(val due: Int, val streak: Int, val today: Int, val goal: Int) {
    val goalMet: Boolean get() = today >= goal

    companion object {
        /**
         * From the skill rows and the practice log: [due] counts every (country, skill) review that is due at [now]
         * for a country in [sovereign] (Antarctica has no reviews), as the Due scopes of the quiz do; the streak and
         * today's items follow [PracticeStreak] with [goal].
         */
        fun of(
            skills: List<SkillProgressEntity>,
            sessions: Collection<PracticeStreak.Session>,
            sovereign: Set<String>,
            goal: Int,
            now: Long,
            zone: TimeZone
        ): WidgetState {
            val due = skills.count { it.countryCode in sovereign && SkillMastery.isDue(it.toState(), now) }
            val summary = PracticeStreak.summary(sessions, goal, now, zone)
            return WidgetState(due, summary.current, PracticeStreak.itemsToday(sessions, now, zone), goal)
        }
    }
}
