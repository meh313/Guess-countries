package com.example.data.model

import com.example.progress.SkillMastery
import com.example.progress.SkillState

/**
 * The countries a player has practiced in a skill but not yet mastered (or let lapse), weakest first, and the ones
 * whose review is due. Quizzes and flashcards can be limited to them, so the review time goes where it helps.
 */
object WeakSpots {
    /**
     * Sovereign countries reviewed at least once and not mastered at [now]. The lowest score comes first, then the
     * earliest due, then the country code, so the order is the same on every call. Countries never reviewed (a
     * bookmark alone creates no skill row) are not weak spots, they are unseen.
     */
    fun select(skills: Map<String, SkillState>, countries: List<Country>, now: Long): List<Country> =
        countries.asSequence()
            .filter { it.isSovereign }
            .mapNotNull { country -> skills[country.code]?.takeIf { SkillMastery.isWeak(it, now) }?.let { country to it } }
            .sortedWith(compareBy<Pair<Country, SkillState>> { it.second.score }.thenBy { it.second.dueAt }.thenBy { it.first.code })
            .map { it.first }
            .toList()

    /** Sovereign countries whose review is due at [now], the longest overdue first, then the lowest score, then the code. */
    fun due(skills: Map<String, SkillState>, countries: List<Country>, now: Long): List<Country> =
        countries.asSequence()
            .filter { it.isSovereign }
            .mapNotNull { country -> skills[country.code]?.takeIf { SkillMastery.isDue(it, now) }?.let { country to it } }
            .sortedWith(compareBy<Pair<Country, SkillState>> { it.second.dueAt }.thenBy { it.second.score }.thenBy { it.first.code })
            .map { it.first }
            .toList()
}
