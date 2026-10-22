package com.example.widget

import com.example.data.local.SkillProgressEntity
import com.example.progress.GoalHistory
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillMastery
import java.util.TimeZone

/** What the home-screen widget shows: reviews due today, the streak, and today's items against the goal. Pure. */
data class WidgetState(val due: Int, val streak: Int, val today: Int, val goal: Int) {
    val goalMet: Boolean get() = today >= goal

    companion object {
        /**
         * From the skill rows and the practice log: [due] counts every (country, skill) review that is due today at
         * [now] in [zone] for a country in [sovereign] (Antarctica has no reviews), in the skills that have reviews
         * ([Skill.REVIEWED]: not the continent), as the Due tile and the Due scopes of the quiz do; the streak and
         * today's items follow [PracticeStreak] with [goals] (each day judged by the goal in force on it).
         */
        fun of(
            skills: List<SkillProgressEntity>,
            sessions: Collection<PracticeStreak.Session>,
            sovereign: Set<String>,
            goals: GoalHistory,
            now: Long,
            zone: TimeZone
        ): WidgetState {
            val reviewed = Skill.REVIEWED.mapTo(HashSet()) { it.name }
            val due = skills.count { it.countryCode in sovereign && it.skill in reviewed && SkillMastery.isDue(it.toState(), now, zone) }
            val summary = PracticeStreak.summary(sessions, goals, now, zone)
            return WidgetState(due, summary.current, PracticeStreak.itemsToday(sessions, now, zone), goals.current)
        }
    }
}
