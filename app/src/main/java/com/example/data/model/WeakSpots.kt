package com.example.data.model

import com.example.data.local.UserProgressEntity

/**
 * The countries a player has practiced but not yet mastered, weakest first. Quizzes and flashcards can be limited to
 * them, so the review time goes where it helps.
 */
object WeakSpots {
    /** A country at or above this mastery score counts as learned. */
    const val MASTERED = 75

    /**
     * Sovereign countries that were reviewed at least once and sit below [MASTERED]. The lowest mastery comes
     * first, then the one practiced longest ago, then the country code, so the order is the same on every call.
     * Countries never reviewed (a bookmark alone creates a progress row) are not weak spots, they are unseen.
     */
    fun select(progress: Collection<UserProgressEntity>, countries: List<Country>): List<Country> {
        val byCode = progress.associateBy { it.countryCode }
        return countries.asSequence()
            .filter { it.isSovereign }
            .mapNotNull { country -> byCode[country.code]?.takeIf { it.timesReviewed >= 1 && it.masteryScore < MASTERED }?.let { country to it } }
            .sortedWith(
                compareBy<Pair<Country, UserProgressEntity>> { it.second.masteryScore }
                    .thenBy { it.second.lastReviewed }
                    .thenBy { it.first.code }
            )
            .map { it.first }
            .toList()
    }
}
