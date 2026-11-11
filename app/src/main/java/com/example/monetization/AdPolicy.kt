package com.example.monetization

import java.util.concurrent.TimeUnit

/**
 * When the free app shows an interstitial ad: after a finished quiz, at most one per [QUIZZES_PER_INTERSTITIAL]
 * quizzes, never in the first [GRACE_DAYS] days of play, and never once Remove ads is bought. Never during a
 * question, on launch, on exit, or in Learn and the flashcards: the result screen's buttons are the only place that
 * asks (see [Monetization.showInterstitialIfDue]). Pure rules, so they are testable without Android.
 */
object AdPolicy {
    /** How many quizzes finish between two interstitials. */
    const val QUIZZES_PER_INTERSTITIAL = 3

    /** How long a new player goes without any interstitial, from the first start of a version with ads. */
    const val GRACE_DAYS = 2

    val GRACE_MS: Long = TimeUnit.DAYS.toMillis(GRACE_DAYS.toLong())

    /**
     * Whether an interstitial is due: [quizzesSinceAd] have finished since the last one (or since the start), the
     * grace period after [firstRunAt] (epoch millis; 0 while unknown, which counts as no) is over at [now], and ads
     * were not bought away. A clock set back before the first run reads as still in the grace period.
     */
    fun interstitialDue(quizzesSinceAd: Int, firstRunAt: Long, now: Long, adsRemoved: Boolean): Boolean =
        !adsRemoved &&
            quizzesSinceAd >= QUIZZES_PER_INTERSTITIAL &&
            firstRunAt > 0L &&
            now - firstRunAt >= GRACE_MS
}
